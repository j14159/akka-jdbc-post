package noisycode.akkajdbc

import akka.util.Timeout

import noisycode.akkajdbc.model.{ Person, Address }

import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Very basic integration test using the provided Vagrant VM.  The cleanup in afterAll()
  * is obviously a bit brittle.
  */
class PersonClientTest extends FlatSpec with Matchers with BeforeAndAfterAll {
  // Using this for convenience and to test whole chain of methods:
  val kernel = new AkkaJdbcKernel()
  import kernel._
  val client = new StandaloneClient(breaker(kernel.system.settings.config), system)

  val person1 = Person(None, "Some Person", "who@where.com", 
    List(Address("123 Nowhere St", "Nowheresville")))

  val person2 = Person(None, "Some person with 2 houses", "property@where.com",
    List(Address("321 Nowhere St", "Nowheresville"), 
      Address("456 Other Ave", "Nowheresville")))

  "A StandaloneClient" should "add and retrieve a Person correctly" in {

    val id1 = Await.result(client.addPerson(person1), 1 second)
    val id2 = Await.result(client.addPerson(person2), 1 second)

    val expected1 = Some(person1.copy(id = Some(id1)))
    val expected2 = Some(person2.copy(id = Some(id2)))

    Await.result(client.personById(id1), 1 second) should be (expected1)
    Await.result(client.personByEmail(expected1.get.email), 1 second) should be (expected1)
    Await.result(client.personById(id2), 1 second) should be (expected2)
  }

  override def afterAll() {
    val c = java.sql.DriverManager.getConnection(kernel.system.settings.config.getString("db.url"))
    val personDelete = c.prepareStatement("delete from person where email=? OR email=?")
    val addressDelete = c.prepareStatement("delete from address where city=?")

    personDelete.setString(1, person1.email)
    personDelete.setString(2, person2.email)
    addressDelete.setString(1, person1.addresses.head.city)

    addressDelete.execute()
    personDelete.execute()

    personDelete.close()
    addressDelete.close()
    c.close()

    kernel.shutdown
  }
}
