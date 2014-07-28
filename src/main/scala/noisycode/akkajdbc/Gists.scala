/**
  * Source for the gists used in the blog post.
  */

package noisycode.akkajdbc

import akka.actor._
import akka.actor.SupervisorStrategy.Restart
import akka.routing.RoundRobinPool
import akka.pattern.ask
import akka.util.Timeout

import java.sql._

import scala.concurrent.Future
import scala.concurrent.duration._

object Gist1 {
  val databaseUrl = "postgresql://some-hostname:5432/db-name"

  Class.forName("my.sql.database.driver.classname")

  class BasicJdbcActor(connFac: () => Connection) extends Actor {
    lazy val conn = connFac()

    override def preRestart(why: Throwable, msg: Option[Any]): Unit = 
      try { conn.close() }
    
    def receive = {
      case anything => throw new Exception("Where's my implementation?")
    }
  }

  def connFac = () => DriverManager.getConnection(databaseUrl)

  def makeMeAnActor(sys: ActorSystem): ActorRef = 
    sys.actorOf(Props(new BasicJdbcActor(connFac)))
}

object Gist2 {
  import Gist1._

  // very naive, be more specific based on your problem:
  val restartStrategy = OneForOneStrategy(
    maxNrOfRetries = 10, 
    withinTimeRange = 1 minute) {
    case _ => Restart
  }

  def newPool(sys: ActorSystem): ActorRef = {
    val props = Props(new BasicJdbcActor(connFac))
    val pool = RoundRobinPool(4, supervisorStrategy = restartStrategy)
    sys.actorOf(pool.props(props))
  }
}

object Gist4 {
  import Gist1._
  import Gist2._

  def newBulkheadingPool(sys: ActorSystem): ActorRef = {
    val props = Props(new BasicJdbcActor(connFac))
      .withDispatcher("my-dispatcher")
    val pool = RoundRobinPool(4, supervisorStrategy = restartStrategy)
    sys.actorOf(pool.props(props))
  }
}

object Gist5 {
  case class Person(name: String, email: String)
  case class PersonById(id: Int)

  class PersonDao(cf: () => Connection) extends Actor {
    lazy val conn = cf()

    override def preRestart(why: Throwable, msg: Option[Any]): Unit = 
      try { conn.close() }
    
    def receive = {
      case Person(n, e) =>
        //call insert function with above connection
        sender ! 1 // mock person ID
      case PersonById(id) =>
        //get person from connection above
        sender ! Person("name", "email") // mock
    }
  }
}

object Gist6 {
  import Gist5._

  trait PersonClient {
    // supply a router with a pool of PersonDao:
    val personPool: ActorRef

    // how long should we wait for a response from PersonDao:
    val timeoutInMillis: Long

    implicit val timeout = Timeout(timeoutInMillis millis)

    def addPerson(p: Person): Future[Int] =
      (personPool ? p).mapTo[Int]

    def personById(id: Long): Future[Person] = 
      (personPool ? PersonById).mapTo[Person]
  }
}
