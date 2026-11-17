package sound4s

import cats.effect.IO
import fs2.Stream
import munit.CatsEffectSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

import scala.concurrent.duration.*

/** Runs against the real audio stack, so it only plays silence and never
  * captures: opening a microphone can prompt for permission.
  */
class NativeBackendSuite extends CatsEffectSuite {

  private val logger: Logger[IO] = NoOpLogger.impl[IO]
  private val format             = AudioFormat(8000, 16, 1)
  private val missing            =
    AudioDevice("__no_such_device_42__", "none", Set(AudioDevice.Kind.Output))

  test("list returns each device once, with a direction") {
    AudioDevices.list[IO].map { devices =>
      assertEquals(devices.map(_.name).distinct.size, devices.size)
      assert(devices.forall(_.kinds.nonEmpty))
    }
  }

  test("findByName returns None for a nonexistent device") {
    AudioDevices
      .findByName[IO]("__no_such_device_42__")
      .map(device => assertEquals(device, None))
  }

  test("Speakers.resource fails with DeviceNotFound for an unknown device") {
    Speakers
      .resource[IO](format, logger, Some(missing))
      .use_
      .attempt
      .map(result =>
        assertEquals(result, Left(AudioError.DeviceNotFound(missing.id)))
      )
  }

  test("Microphone.resource fails with DeviceNotFound for an unknown device") {
    Microphone
      .resource[IO](format, logger, Some(missing))
      .use_
      .attempt
      .map(result =>
        assertEquals(result, Left(AudioError.DeviceNotFound(missing.id)))
      )
  }

  test("Speakers.resource rejects big-endian samples") {
    val bigEndian = format.copy(bigEndian = true)
    Speakers
      .resource[IO](bigEndian, logger)
      .use_
      .attempt
      .map {
        case Left(AudioError.UnsupportedFormat(f, _)) =>
          assertEquals(f, bigEndian)
        case other => fail(s"expected UnsupportedFormat, got $other")
      }
  }

  test("Speakers.resource rejects an unsupported bit depth") {
    val twelveBit = format.copy(bitDepth = 12)
    Speakers
      .resource[IO](twelveBit, logger)
      .use_
      .attempt
      .map {
        case Left(AudioError.UnsupportedFormat(f, _)) =>
          assertEquals(f, twelveBit)
        case other => fail(s"expected UnsupportedFormat, got $other")
      }
  }

  test("a sink plays silence through the default speakers and closes") {
    val silence = Stream.emits(new Array[Byte](format.chunkSize(100.millis)))
    Speakers
      .resource[IO](format, logger)
      .use(speakers => silence.covary[IO].through(speakers.sink).compile.drain)
      .attempt
      .map { result =>
        assume(
          !result.left.exists(_.isInstanceOf[AudioError.DeviceFailure]),
          s"no usable audio output in this environment: $result"
        )
        assertEquals(result, Right(()))
      }
  }

  test("a source captures chunks from the default microphone (opt-in)") {
    assume(
      sys.env.contains("SOUND4S_TEST_CAPTURE"),
      "set SOUND4S_TEST_CAPTURE=1 to capture from the microphone"
    )
    val chunkSize = format.chunkSize(20.millis)
    Microphone
      .resource[IO](format, logger)
      .use(
        _.source(chunkSize).take(10).chunks.compile.toList
      )
      .map { chunks =>
        assertEquals(chunks.size, 10)
        assert(chunks.forall(_.size == chunkSize))
      }
  }
}
