package sound4s

import cats.effect.IO
import munit.CatsEffectSuite

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.*
import scala.scalajs.js

/** The flow control around the playback worklet, against a fake worklet port.
  */
class WebPlaybackSuite extends CatsEffectSuite {

  private val mono = AudioFormat(8000, 16, 1)

  /** A port that records what is posted to it. */
  private class FakePort {
    val posted: ArrayBuffer[js.Dynamic] = ArrayBuffer.empty
    val port: js.Dynamic                = js.Dynamic.literal(
      postMessage = ({ (message: js.Dynamic, _: js.UndefOr[js.Any]) =>
        posted += message
        ()
      }: js.Function2[js.Dynamic, js.UndefOr[js.Any], Unit])
    )
  }

  private def playback(capacityFrames: Int): (FakePort, WebPlayback[IO]) = {
    val fake = new FakePort
    (fake, new WebPlayback[IO](fake.port, mono, capacityFrames))
  }

  test("a write posts its audio as floats") {
    val (fake, p) = playback(1000)
    p.write(Array[Byte](0, 0, -1, 127), 4).map { _ =>
      assertEquals(fake.posted.size, 1)
      assertEquals(fake.posted.head.length.asInstanceOf[Int], 2)
    }
  }

  test("what has been posted counts as queued until the worklet takes it") {
    val (_, p) = playback(1000)
    for {
      _      <- p.write(new Array[Byte](200), 200)
      queued <- p.queuedFrames
      _ = p.reportConsumed(60)
      later <- p.queuedFrames
    } yield {
      assertEquals(queued, 100.0)
      assertEquals(later, 40.0)
    }
  }

  test("a write waits while the worklet holds a full buffer") {
    val (fake, p) = playback(100)
    for {
      _       <- p.write(new Array[Byte](200), 200)
      blocked <- p.write(new Array[Byte](2), 2).timeout(50.millis).attempt
      _ = p.reportConsumed(100)
      _ <- p.write(new Array[Byte](2), 2).timeout(500.millis)
    } yield {
      assert(blocked.isLeft, "the write should have waited")
      assertEquals(fake.posted.size, 2)
    }
  }

  test("a write larger than the buffer still goes through when it is empty") {
    val (fake, p) = playback(10)
    p.write(new Array[Byte](200), 200).map { _ =>
      assertEquals(fake.posted.size, 1)
    }
  }

  test("flush tells the worklet to drop its audio and empties the queue") {
    val (fake, p) = playback(1000)
    for {
      _      <- p.write(new Array[Byte](200), 200)
      _      <- p.flush
      queued <- p.queuedFrames
    } yield {
      assertEquals(queued, 0.0)
      assertEquals(fake.posted.last.flush.asInstanceOf[Boolean], true)
    }
  }

  test("a count from the worklet older than a flush doesn't undo it") {
    val (_, p) = playback(1000)
    for {
      _ <- p.write(new Array[Byte](200), 200)
      _ <- p.flush
      _ = p.reportConsumed(10)
      queued <- p.queuedFrames
    } yield assertEquals(queued, 0.0)
  }

  test("drain returns once the worklet has played what it held") {
    val (_, p) = playback(1000)
    for {
      _     <- p.write(new Array[Byte](200), 200)
      fiber <- p.drain(2.seconds).start
      _     <- IO.sleep(30.millis)
      _ = p.reportConsumed(100)
      _ <- fiber.joinWithNever.timeout(500.millis)
    } yield ()
  }

  test("drain gives up after its timeout") {
    val (_, p) = playback(1000)
    p.write(new Array[Byte](200), 200) >> p.drain(50.millis).timeout(1.second)
  }
}
