package noisycode.akkajdbc

import akka.actor.{ ActorRef, ActorSystem }
import akka.kernel.Bootable
import akka.pattern.CircuitBreaker

import com.typesafe.config.Config

import noisycode.akkajdbc.model.{ PersonClient, PersonDaoFactory }

import scala.concurrent.duration._

/**
  * A simple example microkernel boot class to show how one might wire everything
  * together.
  */
class AkkaJdbcKernel extends Bootable {
  val system = ActorSystem("akkajdbc-example")

  /**
    * A simple example of a [[PersonClient]] you can easily use wherever necessary.
    * I would tend to avoid this in favour of actors leveraging the [[PersonClient]]
    * trait directly but that's not a hard and fast rule.
    */
  class StandaloneClient(val breaker: CircuitBreaker, sys: ActorSystem) extends PersonClient {
    val personActor = PersonDaoFactory.newPool(sys)
  }

  /**
    * Get a [[CircuitBreaker]] using configuration.
    */
  def breaker(config: Config): CircuitBreaker = 
    breaker(config.getInt("breaker.max-failures"),
      config.getLong("breaker.call-timeout"),
      config.getLong("breaker.reset-timeout"))
      
  /**
    * Get a configured [[CircuitBreaker]], times in MS.
    */
  def breaker(maxFailures: Int, callTimeout: Long, resetTimeout: Long): CircuitBreaker = 
    new CircuitBreaker(
      system.dispatcher, 
      system.scheduler, 
      maxFailures, 
      callTimeout millis, 
      resetTimeout millis)

  def startup = {
    val client = new StandaloneClient(breaker(system.settings.config), system)

    /*
     * here you'd start any actors handling web requests, etc.  Generally speaking,
     * I would favour my actors extending the [[PersonClient]] trait directly rather than
     * sharing the above standalone client.
     */
  }

  def shutdown = {
    system.shutdown()
  }
}
