import ox.*
import scala.concurrent.duration._
import scala.util.Try
import scala.util.Random
import scala.util.Failure
import scala.util.Success

def computationOX(): Int =
  sleep(2.seconds)
  2

@main def multiOx =
  val result: (Int, Int) = par(computationOX(), computationOX())
  def computations = Seq.fill(20)(() => computationOX())
  val resultMap: Seq[Unit] =
    computations.mapPar(5)(str => println(str()))
  val resultForeach: Unit =
    computations.foreachPar(5)(str => println(str()))

@main def racingOx =
  def service: String =
    sleep(2.seconds)
    "Hello service"
  def database: String =
    sleep(2.seconds)
    "Hello database"

  println(race(service, database))

@main def timeoutCancelOX =
  def computation: Int =
    sleep(2.seconds)
    println("stopped")
    1

  println(Try(timeout(1.second)(computation)))
  println(Try(timeout(3.seconds)(computation)))

  supervised:
    val cancellable = forkCancellable:
      while true do
        println("tick")
        sleep(1.second)
    forkUser:
      sleep(4.seconds)
      cancellable.cancel()

@main def exceptionsOx() =
  supervised:
    def computation(withException: Option[String]): Int =
      sleep(2.seconds)
      withException match
        case None => 1
        case Some(value) =>
          throw new Exception(value)

    val fork1 = fork:
      computation(withException = None)
    val fork2 = fork:
      computation(withException = Some("Oh no!"))
    val fork3 = fork:
      computation(withException = Some("Oh well.."))
    println(fork1.join())

@main def exceptionsOxBonus() =
  val res: Either[Throwable, Int] =
    supervisedError(ox.EitherMode[Throwable]()):
      def computation(withException: Option[String]): Either[Throwable, Int] =
        sleep(2.seconds)
        withException match
          case None => Right(1)
          case Some(value) =>
            Left(Exception(value))

      val fork1 = forkError:
        computation(withException = None)
      val fork2 = forkError:
        computation(withException = Some("Oh no!"))
      val fork3 = forkError:
        computation(withException = Some("Oh well.."))
      println(fork1.join())
      println(fork2.join())
      println(fork3.join())
      Right(1)
  println("Hello!")
  println(res)

import ox.resilience._

def request(): Int =
  sleep(1.second)
  if Random.nextBoolean() then 100
  else
    println("Ups!")
    throw new Exception("Bad!")

@main def retryOx() =
  val policy: RetryPolicy[Throwable, Int] =
    RetryPolicy.backoff(3, 100.millis, 5.minutes, Jitter.Equal)
  println(retry(policy)(request()))
  println(
    retryEither(policy)(
      Try(request()).toEither
    )
  )
  println(
    retryWithErrorMode(UnionMode[Throwable])(policy)(
      Try(request()) match
        case Failure(exception) => exception
        case Success(value)     => value
    )
  )

import ox.channels.Channel

@main def channelsOx() =
  val channel = Channel.buffered[String](5)

  supervised:
    val sender = forkUser:
      sleep(1.second)
      channel.send("Hello!")
      sleep(3.second)
      channel.send("World!")
    val receiver = forkUser:
      sleep(1.second)
      println(channel.receive())
      sleep(1.second)
      println(channel.receive())
    sender.join()
    receiver.join()
  