package sound4s.otel4s

import cats.effect.{IO, Ref}
import cats.effect.kernel.{Resource, Temporal}
import fs2.{Chunk, Stream}
import munit.CatsEffectSuite
import org.typelevel.otel4s.sdk.metrics.data.{
  MetricData,
  MetricPoints,
  PointData
}
import org.typelevel.otel4s.sdk.testkit.OpenTelemetrySdkTestkit
import org.typelevel.otel4s.sdk.trace.data.SpanData
import org.typelevel.otel4s.trace.StatusCode
import sound4s.*

import scala.concurrent.duration.*

class AudioTelemetrySuite extends CatsEffectSuite {

  private val mono = AudioFormat(8000, 16, 1)

  private def fakeCapture(
      failWith: Option[Throwable] = None
  ): AudioCapture[IO] = new AudioCapture[IO] {
    def format: AudioFormat                    = mono
    def read(chunkSize: Int): Stream[IO, Byte] =
      failWith.fold(
        Stream.chunk(Chunk.array(new Array[Byte](chunkSize))).repeat.covary[IO]
      )(e => Stream.raiseError[IO](e))
    def flush: IO[Unit] = IO.unit
  }

  private def fakePlayback(
      failWith: Option[Throwable] = None
  ): AudioPlayback[IO] = new AudioPlayback[IO] {
    def format: AudioFormat                              = mono
    def write(bytes: Array[Byte], length: Int): IO[Unit] =
      failWith.fold(IO.unit)(IO.raiseError)
    def flush: IO[Unit] = IO.unit
  }

  private def microphone(c: AudioCapture[IO] = fakeCapture()) =
    Resource.pure[IO, Microphone[IO]](new Microphone[IO](c))

  private def speakers(p: AudioPlayback[IO] = fakePlayback()) =
    Resource.pure[IO, Speakers[IO]](new Speakers[IO](p))

  private def withTelemetry[A](
      f: (AudioTelemetry[IO], OpenTelemetrySdkTestkit[IO]) => IO[A]
  ): IO[A] =
    OpenTelemetrySdkTestkit.inMemory[IO]().use { testkit =>
      AudioTelemetry
        .create[IO](using
          Temporal[IO],
          testkit.meterProvider,
          testkit.tracerProvider
        )
        .flatMap(f(_, testkit))
    }

  private type Attrs = Map[String, String]

  /** The value of each point of the long sum `name`, by its attributes. */
  private def sums(metrics: List[MetricData], name: String): Map[Attrs, Long] =
    metrics
      .filter(_.name == name)
      .flatMap(_.data match {
        case sum: MetricPoints.Sum =>
          sum.points.collect { case p: PointData.LongNumber =>
            attributes(p) -> p.value
          }
        case _ => Nil
      })
      .toMap

  private def attributes(point: PointData): Attrs =
    point.attributes.map(a => a.key.name -> a.value.toString).toMap

  private def input  = Map("sound4s.audio.direction" -> "input")
  private def output = Map("sound4s.audio.direction" -> "output")

  private def attributes(span: SpanData): Attrs =
    span.attributes.elements.map(a => a.key.name -> a.value.toString).toMap

  test(
    "opening is a span with the format, and the stream is active until released"
  ) {
    withTelemetry { (telemetry, testkit) =>
      for {
        during <- telemetry
          .microphone(microphone())
          .use(_ => testkit.collectMetrics)
        after <- testkit.collectMetrics
        spans <- testkit.finishedSpans
      } yield {
        assertEquals(
          sums(during, "sound4s.audio.streams.active"),
          Map(input -> 1L)
        )
        assertEquals(
          sums(after, "sound4s.audio.streams.active"),
          Map(input -> 0L)
        )
        val span = spans.filter(_.name == "sound4s.microphone.open")
        assertEquals(span.size, 1)
        assertEquals(
          attributes(span.head),
          Map(
            "sound4s.audio.sample_rate" -> "8000",
            "sound4s.audio.bit_depth"   -> "16",
            "sound4s.audio.channels"    -> "1"
          )
        )
        assertNotEquals(span.head.status.status, StatusCode.Error)
      }
    }
  }

  test("how long opening took is recorded") {
    withTelemetry { (telemetry, testkit) =>
      telemetry.speakers(speakers()).use_ >> testkit.collectMetrics.map {
        metrics =>
          assert(metrics.exists(_.name == "sound4s.audio.open.duration"))
      }
    }
  }

  test(
    "the audio read from the microphone is counted, and each reading flushes"
  ) {
    withTelemetry { (telemetry, testkit) =>
      for {
        _ <- telemetry.microphone(microphone()).use { mic =>
          mic.source(320).take(960).compile.drain
        }
        metrics <- testkit.collectMetrics
      } yield {
        assertEquals(
          sums(metrics, "sound4s.audio.data.size"),
          Map(input -> 960L)
        )
        assertEquals(sums(metrics, "sound4s.audio.flushes"), Map(input -> 1L))
      }
    }
  }

  test("the audio written to the speakers is counted") {
    withTelemetry { (telemetry, testkit) =>
      for {
        _ <- telemetry.speakers(speakers()).use { spk =>
          Stream
            .emits(new Array[Byte](640))
            .covary[IO]
            .through(spk.sink(320))
            .compile
            .drain
        }
        metrics <- testkit.collectMetrics
      } yield {
        assertEquals(
          sums(metrics, "sound4s.audio.data.size"),
          Map(output -> 640L)
        )
        assertEquals(
          sums(metrics, "sound4s.audio.flushes"),
          Map.empty[Attrs, Long]
        )
      }
    }
  }

  test("a sink that is cancelled counts the audio it dropped") {
    withTelemetry { (telemetry, testkit) =>
      for {
        _ <- telemetry.speakers(speakers()).use { spk =>
          Stream
            .emits(new Array[Byte](320))
            .repeat
            .covary[IO]
            .through(spk.sink(320))
            .interruptAfter(50.millis)
            .compile
            .drain
        }
        metrics <- testkit.collectMetrics
      } yield assertEquals(
        sums(metrics, "sound4s.audio.flushes"),
        Map(output -> 1L)
      )
    }
  }

  test("a failure while capturing is counted by type") {
    withTelemetry { (telemetry, testkit) =>
      for {
        result <- telemetry
          .microphone(
            microphone(fakeCapture(Some(new IllegalStateException("x"))))
          )
          .use(_.source.compile.drain)
          .attempt
        metrics <- testkit.collectMetrics
      } yield {
        assert(result.isLeft)
        assertEquals(
          sums(metrics, "sound4s.audio.errors"),
          Map(input + ("error.type" -> "java.lang.IllegalStateException") -> 1L)
        )
      }
    }
  }

  test("a failure while playing is counted by type, and the audio isn't") {
    withTelemetry { (telemetry, testkit) =>
      for {
        result <- telemetry
          .speakers(
            speakers(fakePlayback(Some(new IllegalStateException("x"))))
          )
          .use(spk =>
            Stream
              .emits(new Array[Byte](320))
              .through(spk.sink(320))
              .compile
              .drain
          )
          .attempt
        metrics <- testkit.collectMetrics
      } yield {
        assert(result.isLeft)
        assertEquals(
          sums(metrics, "sound4s.audio.errors"),
          Map(
            output + ("error.type" -> "java.lang.IllegalStateException") -> 1L
          )
        )
        assertEquals(
          sums(metrics, "sound4s.audio.data.size"),
          Map.empty[Attrs, Long]
        )
      }
    }
  }

  test("failing to open marks the span as an error and counts the failure") {
    withTelemetry { (telemetry, testkit) =>
      for {
        result <- telemetry
          .microphone(
            Resource.raiseError[IO, Microphone[IO], Throwable](
              AudioError.DeviceNotFound("x")
            )
          )
          .use_
          .attempt
        metrics <- testkit.collectMetrics
        spans   <- testkit.finishedSpans
      } yield {
        assertEquals(result, Left(AudioError.DeviceNotFound("x")))
        assertEquals(spans.map(_.name), List("sound4s.microphone.open"))
        assertEquals(spans.head.status.status, StatusCode.Error)
        val errors = sums(metrics, "sound4s.audio.errors")
        assertEquals(errors.values.toList, List(1L))
        assert(errors.keys.head("error.type").contains("DeviceNotFound"))
        assertEquals(
          sums(metrics, "sound4s.audio.streams.active"),
          Map.empty[Attrs, Long]
        )
      }
    }
  }

  test("sound streaming reports both directions under one span") {
    withTelemetry { (telemetry, testkit) =>
      val open = Resource.pure[IO, SoundStreaming[IO]](
        new SoundStreaming[IO](
          new Microphone[IO](fakeCapture()),
          new Speakers[IO](fakePlayback()),
          20.millis
        )
      )
      for {
        during <- telemetry.soundStreaming(open).use { ss =>
          ss.source
            .through(ss.sink)
            .take(1)
            .compile
            .drain >> testkit.collectMetrics
        }
        spans <- testkit.finishedSpans
      } yield {
        assertEquals(
          sums(during, "sound4s.audio.streams.active"),
          Map(input -> 1L, output -> 1L)
        )
        assertEquals(spans.map(_.name), List("sound4s.streaming.open"))
        assert(
          sums(during, "sound4s.audio.data.size").keySet == Set(input, output)
        )
      }
    }
  }

  test("noop telemetry passes everything through") {
    AudioTelemetry.noop[IO].flatMap { telemetry =>
      telemetry.microphone(microphone()).use(_.source.take(1).compile.drain) >>
        telemetry.speakers(speakers()).use_
    }
  }

  test("instrumenting doesn't change the audio") {
    withTelemetry { (telemetry, _) =>
      val seen     = Ref.unsafe[IO, List[Int]](Nil)
      val playback = new AudioPlayback[IO] {
        def format: AudioFormat                              = mono
        def write(bytes: Array[Byte], length: Int): IO[Unit] =
          seen.update(_ :+ length)
        def flush: IO[Unit] = IO.unit
      }
      telemetry.speakers(speakers(playback)).use { spk =>
        Stream
          .emits(new Array[Byte](640))
          .covary[IO]
          .through(spk.sink(320))
          .compile
          .drain
      } >> seen.get.map(assertEquals(_, List(320, 320)))
    }
  }
}
