package noisycode.akkajdbc.model

import akka.actor._
import akka.actor.SupervisorStrategy.Restart
import akka.pattern.{ ask, CircuitBreaker }
import akka.routing.RoundRobinPool
import akka.util.Timeout

import java.sql._

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success, Try }

/**
  * Our example aggregate root.
  */
case class Person(id: Option[Int], name: String, email: String, addresses: Seq[Address])
case class Address(street: String, city: String)

/**
  * Provides a simple way to get a pool of Person DAO actors behind a router.
  */
object PersonDaoFactory {
  /**
    * This is the most naive possible strategy in that it <b>always</b> restarts.
    */
  val restartStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
    case _ => Restart
  }

  /**
    * Use the given [[ActorSystem]]'s config to make a new pool of
    * DAOs behind a router.
    */
  def newPool(sys: ActorSystem): ActorRef = {
    val count = sys.settings.config.getInt("person-worker-count")
    val connUrl = sys.settings.config.getString("db.url")

    Class.forName(sys.settings.config.getString("db.driver"))

    val connFac = () => DriverManager.getConnection(connUrl)

    sys.actorOf(RoundRobinPool(count, supervisorStrategy = restartStrategy)
      .props(Props(new PersonDaoActor(connFac)).withDispatcher("person-dispatcher")))      
  }
}

/**
  * Here is the implementation of our [[Future]]-based client/interface to the DAO.  Within this
  * trait is where you would swap out calls to an internal actor for calls to an external service
  * when/if it is necessary/desirable to do so.
  */
trait PersonClient {
  /**
    * A reference to an instance of [[PersonDaoActor]] or a router in front of a pool of them.
    */
  val personActor: ActorRef
  /**
    * All calls will be protected with this circuit breaker.
    */
  val breaker: CircuitBreaker

  def addPerson(person: Person): Future[Int] = withBreaker(PersonApi.AddPerson(Promise[Int](), person))

  def personById(id: Int): Future[Option[Person]] = 
    withBreaker(PersonApi.PersonById(Promise[Option[Person]](), id))

  def personByEmail(email: String): Future[Option[Person]] =
    withBreaker(PersonApi.PersonByEmail(Promise[Option[Person]](), email))

  def withBreaker[T](msg: PersonApi.PersonDaoMsg[T]): Future[T] = {
    personActor ! msg
    breaker.withCircuitBreaker(msg.res.future)
  }
}

/**
  * All messages that a [[PersonDaoActor]] understands are given here.
  */
private [model] object PersonApi {
  /**
    * We have this trait so that a [[PersonDaoActor]] can send back a failure as part of restart
    * behaviour by getting a handle on the original [[Promise]].
    */
  trait PersonDaoMsg[T] {
    val res: Promise[T]
  }

  case class AddPerson(res: Promise[Int], p: Person) extends PersonDaoMsg[Int]
  case class PersonById(res: Promise[Option[Person]], id: Int) extends PersonDaoMsg[Option[Person]]
  case class PersonByEmail(res: Promise[Option[Person]], email: String) extends PersonDaoMsg[Option[Person]]
}

class PersonDaoActor(connFactory: () => Connection) extends Actor with ActorLogging with PersonJdbc {
  lazy val conn = connFactory()

  /**
    * The behaviour here assumes that the supervision policy has made the right
    * decision to actually replace this actor.
    */
  override def preRestart(reason: Throwable, msg: Option[Any]): Unit = {
    msg match {
      // send the failure to the caller for this one, they might want a bit more info:
      case Some(daoMsg) if daoMsg.isInstanceOf[PersonApi.PersonDaoMsg[_]] => 
        daoMsg.asInstanceOf[PersonApi.PersonDaoMsg[_]].res.failure(reason)
      case Some(other) => {} //somebody sent a bad message, ignore
      case None => {}
    }

    try { conn.close }
  }

  override def postStop(): Unit = {
    try { conn.close() }
    super.postStop()
  }

  //we leave all fault handling to the supervisor:
  def receive = {
    case PersonApi.AddPerson(res, p) => 
      log.debug(s"Making a person:  ${p}")
      res success addPerson(conn, p)
    case PersonApi.PersonById(res, id) => res success getPersonById(conn, id)
    case PersonApi.PersonByEmail(res, email) => res success getPersonByEmail(conn, email)
  }
}

/**
  * Keeping the actual DB function separate from the actor keeps the actor code
  * clean and simple while also letting us write simple integration tests against
  * the functions without needing the actor should one want to.  Note the complete 
  * lack of try/catch and Try since decisions about error handling are all up to the
  * actor and/or its supervision strategy.
  * 
  * Using an ORM or something like slick/jooq/etc is completely acceptable here,
  * I just have no particular objection to the slight overhead involved in writing JDBC
  * for something simple like this.
  */
private [model] trait PersonJdbc {
  val clause = "AND address.owner=person.id"
  val byIdQ = s"SELECT person.id,name,email,street,city FROM person,address WHERE person.id=? ${clause}"
  val byEmailQ = s"SELECT person.id,name,email,street,city FROM person,address WHERE email=? ${clause}"

  val insertPerson = "INSERT INTO person (name,email) VALUES (?,?) RETURNING id"
  val insertAddress = "INSERT INTO address (owner,street,city) VALUES (?,?,?)"

  /**
    * Add a [[Person]] to the database and return their new ID.
    */
  def addPerson(c: Connection, p: Person): Int = {
    val s = c.prepareStatement(insertPerson)
    s.setString(1, p.name)
    s.setString(2, p.email)
    val id = idFromResultSet(s.executeQuery())
    s.close()

    addAddresses(c, id, p.addresses)
    id
  }

  def addAddresses(c: Connection, personId: Int, a: Seq[Address]): Unit = {
    val s = c.prepareStatement(insertAddress)
    a.foreach { case Address(street, city) =>
      s.clearParameters()
      s.setInt(1, personId)
      s.setString(2, street)
      s.setString(3, city)
      s.execute()
    }

    s.close()
  }

  private def idFromResultSet(rs: ResultSet): Int = {
    rs.next()
    val res = rs.getInt(1)
    rs.close()
    res
  }

  def getPersonById(c: Connection, id: Int): Option[Person] =
    runPersonQuery(c, byIdQ, _.setInt(1, id))

  def getPersonByEmail(c: Connection, email: String): Option[Person] = 
    runPersonQuery(c, byEmailQ, _.setString(1, email))

  def runPersonQuery(c: Connection, q: String, f: PreparedStatement => Unit): Option[Person] = {
    val s = c.prepareStatement(q)
    f(s)
    val res = personFromResultSet(s.executeQuery())
    s.close()
    res
  }

  /**
    * We expect single Person instances so this will both position and close
    * the ResultSet.  It should be immediately obvious that I'm making some
    * large assumptions in the interest of brevity.
    */
  def personFromResultSet(rs: ResultSet): Option[Person] = {
    val res = 
      if(rs.next() && !rs.isAfterLast)
        Some(Person(Some(rs.getInt(1)), rs.getString(2), rs.getString(3),
            addressFrom(rs) :: addressesFromPositionedResultSet(rs).toList))
      else
        None

    rs.close
    res
  }

  /**
    * Closing over the chunk of side-effects and mutability in the ResultSet is
    * hardly ideal but I'm making the assumption that one would not be foolish
    * enough to share this or put it in a potentially concurrent situation outside
    * of the actor leveraging this trait's functionality.  Knives come with sharp
    * edges.
    */
  def addressesFromPositionedResultSet(rs: ResultSet): Iterator[Address] = 
    new Iterator[Address] {
      def hasNext() = rs.next()
      def next() = addressFrom(rs)
    }

  def addressFrom(rs: ResultSet) = Address(rs.getString(4), rs.getString(5))
}
