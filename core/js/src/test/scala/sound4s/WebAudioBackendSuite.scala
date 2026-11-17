package sound4s

import cats.effect.IO
import munit.CatsEffectSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

/** Runs under Node, where there is no Web Audio: checks what fails and how. */
class WebAudioBackendSuite extends CatsEffectSuite {

  private val logger: Logger[IO] = NoOpLogger.impl[IO]
  private val format             = AudioFormat(8000, 16, 1)

  test("list fails with DeviceFailure outside a browser") {
    AudioDevices.list[IO].attempt.map {
      case Left(_: AudioError.DeviceFailure) => ()
      case other => fail(s"expected DeviceFailure, got $other")
    }
  }

  test("Speakers.resource fails with DeviceFailure outside a browser") {
    Speakers.resource[IO](format, logger).use_.attempt.map {
      case Left(_: AudioError.DeviceFailure) => ()
      case other => fail(s"expected DeviceFailure, got $other")
    }
  }

  test("Microphone.resource fails with DeviceFailure outside a browser") {
    Microphone.resource[IO](format, logger).use_.attempt.map {
      case Left(_: AudioError.DeviceFailure) => ()
      case other => fail(s"expected DeviceFailure, got $other")
    }
  }

  test("a format Web Audio code can't convert is rejected first") {
    val bigEndian = format.copy(bigEndian = true)
    Speakers.resource[IO](bigEndian, logger).use_.attempt.map {
      case Left(AudioError.UnsupportedFormat(f, _)) =>
        assertEquals(f, bigEndian)
      case other => fail(s"expected UnsupportedFormat, got $other")
    }
  }
}
