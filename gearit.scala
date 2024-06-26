import gears.async.*
import gears.async.default.given
import scala.concurrent.duration._
import scala.util.Try
import scala.util.Random
import gears.async.Retry.Delay
import gears.async.Retry.Jitter

def computationGears(using Async.Spawn) = Future:
  AsyncOperations.sleep(2.second)
  println("Hello")

@main def multiGears() =
  Async.blocking:
    val computations: Seq[() => Future[Unit]] =
      Seq.fill(20)(() => computationGears)
    val resMap: Seq[Unit] =
      computations.map(fut => fut()).awaitAll
    val resGrouped: Unit =
      computations
        .grouped(5)
        .foreach: futs =>
          futs.map(fut => fut()).awaitAll

@main def racingGears() =
  Async.blocking:
    def service: Future[String] =
      Future:
        AsyncOperations.sleep(2001)
        "Hello service"
    def database: Future[String] =
      Future:
        AsyncOperations.sleep(2001)
        "Hello database"

    println(Async.race(service, database).await)

@main def timeoutCancelGears() =
  Async.blocking:
    def computation: Int =
      AsyncOperations.sleep(2.seconds)
      println("stopped")
      1
    println(Try(withTimeout(1.seconds)(computation)))
    println(Try(withTimeout(3.seconds)(computation)))

    val future = Future:
      while true do
        println("tock")
        AsyncOperations.sleep(1.second)
    Future:
      AsyncOperations.sleep(4000)
      future.cancel()
    future.await

@main def exceptionsGears() =
  Async.blocking:
    def computation(withException: Option[String]): Int =
      AsyncOperations.sleep(2.seconds)
      withException match
        case None => 1
        case Some(value) =>
          throw new Exception(value)

    val future1 = Future:
      computation(withException = None)
    val future2 = Future:
      computation(withException = Some("Oh no!"))
    val future3 = Future:
      computation(withException = Some("Oh well.."))
    future1.await
    future2.awaitResult
    future3.await

@main def retryGears() =
  def request()(using Async): Int =
    AsyncOperations.sleep(1000)
    if Random.nextBoolean() then 100
    else
      println("Ups!")
      throw new Exception("Bad!")
  Async.blocking:
    val result = Retry.untilSuccess
      .withMaximumFailures(5)
      .withDelay(
        Delay.backoff(
          maximum = 1.minute,
          starting = 1.second,
          jitter = Jitter.full
        )
      )(request())
    println(result)

@main def channelsGears() =
  val channel = BufferedChannel[String](5)
  Async.blocking:
    
    val sender = Future:
      AsyncOperations.sleep(1.second)
      channel.send("Hello!")
      AsyncOperations.sleep(3.second)
      channel.send("World!")
    val receiver = Future:
      AsyncOperations.sleep(1.second)
      println(channel.read()) // Either[Closed, T]
      AsyncOperations.sleep(1.second)
      println(channel.read()) // Either[Closed, T]
    sender.await
    receiver.await