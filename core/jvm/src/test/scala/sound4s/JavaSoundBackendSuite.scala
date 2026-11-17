package sound4s

import cats.effect.IO
import munit.CatsEffectSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

import scala.collection.mutable.ArrayBuffer
import javax.sound.sampled.AudioSystem

class JavaSoundBackendSuite extends CatsEffectSuite {

  import JavaSoundStubs.*

  private val logger: Logger[IO] = NoOpLogger.impl[IO]
  private val format             = AudioFormat(8000, 16, 1)
  private val javaFormat         = JavaSoundBackend.toJavaFormat(format)

  test("toJavaFormat carries every field of the format") {
    val converted = JavaSoundBackend.toJavaFormat(
      AudioFormat(44100, 24, 2, signed = false, bigEndian = true)
    )
    assertEquals(converted.getSampleRate, 44100f)
    assertEquals(converted.getSampleSizeInBits, 24)
    assertEquals(converted.getChannels, 2)
    assertEquals(
      converted.getEncoding,
      javax.sound.sampled.AudioFormat.Encoding.PCM_UNSIGNED
    )
    assert(converted.isBigEndian)
    assertEquals(converted.getFrameSize, 6)
  }

  test("deviceOf and mixerInfoOf round-trip every installed mixer") {
    AudioSystem.getMixerInfo.foreach { info =>
      val found = JavaSoundBackend.mixerInfoOf(JavaSoundBackend.deviceOf(info))
      assertEquals(found.map(_.getName), Some(info.getName))
    }
  }

  test("mixer fails with DeviceNotFound for a device that isn't installed") {
    JavaSoundBackend
      .mixer[IO](Some(AudioDevice("__no_such_id__", "none")), logger)
      .use_
      .attempt
      .map {
        case Left(AudioError.DeviceNotFound(id)) =>
          assertEquals(id, "__no_such_id__")
        case other => fail(s"expected DeviceNotFound, got $other")
      }
  }

  test("mixer acquires a usable default mixer") {
    assume(
      AudioSystem.getMixerInfo.nonEmpty,
      "no audio mixer available in this environment"
    )
    JavaSoundBackend
      .mixer[IO](None, logger)
      .use(mixer => IO(assert(mixer.getMixerInfo != null)))
  }

  test(
    "openTargetDataLine fails with UnexpectedLineType when the mixer's line isn't a TargetDataLine"
  ) {
    JavaSoundBackend
      .openTargetDataLine[IO](new StubMixer(new StubLine), format, logger)
      .use_
      .attempt
      .map {
        case Left(AudioError.UnexpectedLineType(expected, actual)) =>
          assertEquals(expected, "TargetDataLine")
          assertEquals(actual, "StubLine")
        case other => fail(s"expected UnexpectedLineType, got $other")
      }
  }

  test(
    "openSourceDataLine fails with UnexpectedLineType when the mixer's line isn't a SourceDataLine"
  ) {
    JavaSoundBackend
      .openSourceDataLine[IO](new StubMixer(new StubLine), format, logger)
      .use_
      .attempt
      .map {
        case Left(AudioError.UnexpectedLineType(expected, actual)) =>
          assertEquals(expected, "SourceDataLine")
          assertEquals(actual, "StubLine")
        case other => fail(s"expected UnexpectedLineType, got $other")
      }
  }

  test("playback writes only the first `length` bytes") {
    val writes   = ArrayBuffer.empty[Array[Byte]]
    val playback =
      JavaSoundBackend.playback[IO](sourceDataLine(javaFormat, writes), format)
    playback.write(Array[Byte](1, 2, 3, 4, 5), 4).map { _ =>
      assertEquals(writes.toList.map(_.toList), List(List[Byte](1, 2, 3, 4)))
    }
  }

  test("playback flush flushes the line") {
    val events   = ArrayBuffer.empty[String]
    val playback = JavaSoundBackend
      .playback[IO](sourceDataLine(javaFormat, events = events), format)
    playback.flush.map(_ => assertEquals(events.toList, List("flush")))
  }

  test("capture reads chunks from the line") {
    val capture =
      JavaSoundBackend.capture[IO](targetDataLine(javaFormat), format)
    capture.read(320).take(1).chunks.compile.lastOrError.map { chunk =>
      assertEquals(chunk.size, 1)
    }
  }

  test("capture flush flushes the line") {
    val events  = ArrayBuffer.empty[String]
    val capture =
      JavaSoundBackend.capture[IO](targetDataLine(javaFormat, events), format)
    capture.flush.map(_ => assertEquals(events.toList, List("flush")))
  }
}
