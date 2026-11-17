package sound4s

import cats.effect.IO
import fs2.{Chunk, Pipe, Stream}
import munit.CatsEffectSuite

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.*

/** Exercises the logic shared by every platform against in-memory backends. */
class SoundStreamingSuite extends CatsEffectSuite {

  private val mono   = AudioFormat(8000, 16, 1)
  private val stereo = AudioFormat(8000, 16, 2)

  private def fakePlayback(
      audioFormat: AudioFormat,
      writes: ArrayBuffer[Array[Byte]],
      events: ArrayBuffer[String] = ArrayBuffer.empty
  ): AudioPlayback[IO] = new AudioPlayback[IO] {
    def format: AudioFormat                              = audioFormat
    def write(bytes: Array[Byte], length: Int): IO[Unit] =
      IO { writes += bytes.take(length); () }
    def flush: IO[Unit] = IO { events += "flush"; () }
  }

  private def fakeCapture(
      audioFormat: AudioFormat,
      events: ArrayBuffer[String] = ArrayBuffer.empty
  ): AudioCapture[IO] = new AudioCapture[IO] {
    def format: AudioFormat                    = audioFormat
    def read(chunkSize: Int): Stream[IO, Byte] =
      Stream.chunk(Chunk.array(new Array[Byte](chunkSize))).repeat.covary[IO]
    def flush: IO[Unit] = IO { events += "flush"; () }
  }

  private def streaming(
      playback: AudioPlayback[IO],
      capture: AudioCapture[IO]
  ): SoundStreaming[IO] =
    new SoundStreaming[IO](
      new Microphone[IO](capture),
      new Speakers[IO](playback),
      20.millis
    )

  private def makeSink(
      playback: AudioPlayback[IO],
      capture: AudioCapture[IO]
  ): Pipe[IO, Byte, Unit] = streaming(playback, capture).sink

  test("sink writes frame-aligned chunks") {
    val writes = ArrayBuffer.empty[Array[Byte]]
    val sink   = makeSink(fakePlayback(mono, writes), fakeCapture(mono))
    Stream.emits(Array.fill[Byte](7)(1)).through(sink).compile.drain.map { _ =>
      assertEquals(writes.map(_.length).sum % mono.frameSize, 0)
    }
  }

  test("sink discards sub-frame remnants for stereo") {
    val writes = ArrayBuffer.empty[Array[Byte]]
    val sink   = makeSink(fakePlayback(stereo, writes), fakeCapture(stereo))
    Stream.emits(Array.fill[Byte](5)(1)).through(sink).compile.drain.map { _ =>
      assertEquals(writes.map(_.length).sum, 4)
    }
  }

  test("sink handles empty stream") {
    val writes = ArrayBuffer.empty[Array[Byte]]
    val sink   = makeSink(fakePlayback(mono, writes), fakeCapture(mono))
    Stream.empty.through(sink).compile.drain.map(_ => assert(writes.isEmpty))
  }

  test("sink preserves byte content") {
    val writes = ArrayBuffer.empty[Array[Byte]]
    val sink   = makeSink(fakePlayback(mono, writes), fakeCapture(mono))
    val input  = Array[Byte](0x01, 0x02, 0x03, 0x04)
    Stream.emits(input).through(sink).compile.drain.map { _ =>
      assertEquals(writes.flatMap(_.toVector).toVector, input.toVector)
    }
  }

  test("sink writes in chunks of the packet interval") {
    val writes = ArrayBuffer.empty[Array[Byte]]
    val sink   = makeSink(fakePlayback(mono, writes), fakeCapture(mono))
    Stream
      .emits(Array.fill[Byte](640)(1))
      .through(sink)
      .compile
      .drain
      .map(_ => assertEquals(writes.map(_.length).toList, List(320, 320)))
  }

  test("source reads in chunks of the packet interval") {
    streaming(fakePlayback(mono, ArrayBuffer.empty), fakeCapture(mono)).source
      .take(1)
      .chunks
      .compile
      .lastOrError
      .map(chunk => assertEquals(chunk.size, 1))
  }

  test("source flushes what the microphone recorded before it was read") {
    val events = ArrayBuffer.empty[String]
    val source =
      streaming(
        fakePlayback(mono, ArrayBuffer.empty),
        fakeCapture(mono, events)
      ).source
    source.take(1).compile.drain.map { _ =>
      assertEquals(events.headOption, Some("flush"))
      assertEquals(events.count(_ == "flush"), 1)
    }
  }

  test("each reading of source starts from now") {
    val events = ArrayBuffer.empty[String]
    val source =
      streaming(
        fakePlayback(mono, ArrayBuffer.empty),
        fakeCapture(mono, events)
      ).source
    (source.take(1).compile.drain >> source.take(1).compile.drain).map { _ =>
      assertEquals(events.count(_ == "flush"), 2)
    }
  }

  test("a sink that is cancelled drops what it queued in the speakers") {
    val events = ArrayBuffer.empty[String]
    val sink   =
      makeSink(fakePlayback(mono, ArrayBuffer.empty, events), fakeCapture(mono))
    val endless = Stream.emits(Array.fill[Byte](320)(1)).repeat.covary[IO]
    IO.race(endless.through(sink).compile.drain, IO.sleep(50.millis)).map { r =>
      assert(r.isRight)
      assertEquals(events.toList, List("flush"))
    }
  }

  test("a sink that is stopped with interruptWhen drops what it queued") {
    val events = ArrayBuffer.empty[String]
    val sink   =
      makeSink(fakePlayback(mono, ArrayBuffer.empty, events), fakeCapture(mono))
    val endless = Stream.emits(Array.fill[Byte](320)(1)).repeat.covary[IO]
    endless
      .through(sink)
      .interruptWhen(IO.sleep(50.millis).attempt)
      .compile
      .drain
      .map(_ => assertEquals(events.toList, List("flush")))
  }

  test("a sink that fails drops what it queued") {
    val events = ArrayBuffer.empty[String]
    val sink   =
      makeSink(fakePlayback(mono, ArrayBuffer.empty, events), fakeCapture(mono))
    Stream
      .emits(Array.fill[Byte](320)(1))
      .covary[IO]
      .append(Stream.raiseError[IO](new RuntimeException("boom")))
      .through(sink)
      .compile
      .drain
      .attempt
      .map { result =>
        assert(result.isLeft)
        assertEquals(events.toList, List("flush"))
      }
  }

  test("a sink that ends plays what it was given to the end") {
    val events = ArrayBuffer.empty[String]
    val sink   =
      makeSink(fakePlayback(mono, ArrayBuffer.empty, events), fakeCapture(mono))
    Stream
      .emits(Array.fill[Byte](640)(1))
      .through(sink)
      .compile
      .drain
      .map(_ => assertEquals(events.toList, Nil))
  }
}
