package sound4s.otel4s

import cats.effect.kernel.{Resource, Temporal}
import cats.syntax.all.*
import fs2.Stream
import org.typelevel.otel4s.{Attribute, Attributes}
import org.typelevel.otel4s.metrics.{
  Counter,
  Histogram,
  MeterProvider,
  UpDownCounter
}
import org.typelevel.otel4s.trace.{Tracer, TracerProvider}
import sound4s.*

import scala.concurrent.duration.*

/** Metrics and traces for sound4s, on [[https://typelevel.org/otel4s otel4s]].
  *
  * Wrap the `Resource` that opens a [[sound4s.Microphone]],
  * [[sound4s.Speakers]] or [[sound4s.SoundStreaming]] to get one that reports:
  *
  *   - a span for opening the device, `sound4s.microphone.open`,
  *     `sound4s.speakers.open` or `sound4s.streaming.open`, with the audio
  *     format as attributes; a failure to open marks the span as an error
  *   - `sound4s.audio.open.duration` (s): how long opening took
  *   - `sound4s.audio.streams.active`: the streams currently open
  *   - `sound4s.audio.data.size` (By): the audio read from the microphone or
  *     written to the speakers
  *   - `sound4s.audio.flushes`: the times buffered audio was discarded; on
  *     input, at the start of each reading, and on output, when a sink was
  *     cancelled or failed
  *   - `sound4s.audio.errors`: failures to open and failures while streaming,
  *     by `error.type`
  *
  * Each metric but the duration carries `sound4s.audio.direction`, either
  * `input` or `output`.
  */
final class AudioTelemetry[F[_]: Temporal] private (
    streamsActive: UpDownCounter[F, Long],
    dataSize: Counter[F, Long],
    flushes: Counter[F, Long],
    errors: Counter[F, Long],
    openDuration: Histogram[F, Double],
    tracer: Tracer[F]
) {

  import AudioTelemetry.*

  /** Instruments the microphone that `open` opens. */
  def microphone(
      open: Resource[F, Microphone[F]]
  ): Resource[F, Microphone[F]] =
    instrumented("microphone", List(Input), open)(
      _.format,
      mic => new Microphone[F](capture(mic.capture))
    )

  /** Instruments the speakers that `open` opens. */
  def speakers(open: Resource[F, Speakers[F]]): Resource[F, Speakers[F]] =
    instrumented("speakers", List(Output), open)(
      _.format,
      spk => new Speakers[F](playback(spk.playback))
    )

  /** Instruments the microphone and speakers that `open` opens together. */
  def soundStreaming(
      open: Resource[F, SoundStreaming[F]]
  ): Resource[F, SoundStreaming[F]] =
    instrumented("streaming", List(Input, Output), open)(
      _.microphone.format,
      ss =>
        new SoundStreaming[F](
          new Microphone[F](capture(ss.microphone.capture)),
          new Speakers[F](playback(ss.speakers.playback)),
          ss.rtpPacketInterval
        )
    )

  private def instrumented[A](
      kind: String,
      directions: List[Direction],
      open: Resource[F, A]
  )(format: A => AudioFormat, wrap: A => A): Resource[F, A] = {
    val failedToOpen: PartialFunction[Throwable, F[Unit]] = { case e =>
      directions.traverse_(recordError(_, e))
    }
    val opened: F[(A, F[Unit])] =
      tracer.span(s"sound4s.$kind.open").use { span =>
        Temporal[F]
          .timed(open.allocated.onError(failedToOpen))
          .flatMap { case (elapsed, (a, release)) =>
            span.addAttributes(formatAttributes(format(a))) *>
              openDuration.record(
                elapsed.toUnit(SECONDS),
                Attribute("sound4s.audio.kind", kind)
              ) as ((wrap(a), release))
          }
      }
    for {
      allocated <- Resource.makeFull[F, (A, F[Unit])](poll => poll(opened))(
        _._2
      )
      _ <- Resource.make(adjustStreams(directions, 1))(_ =>
        adjustStreams(directions, -1)
      )
    } yield allocated._1
  }

  private def adjustStreams(
      directions: List[Direction],
      by: Long
  ): F[Unit] =
    directions.traverse_(d => streamsActive.add(by, direction(d)))

  private def recordError(d: Direction, e: Throwable): F[Unit] =
    errors.add(1L, direction(d), Attribute("error.type", e.getClass.getName))

  private def capture(inner: AudioCapture[F]): AudioCapture[F] =
    new AudioCapture[F] {
      def format: AudioFormat = inner.format

      def read(chunkSize: Int): Stream[F, Byte] =
        inner
          .read(chunkSize)
          .chunks
          .evalTap(chunk => dataSize.add(chunk.size.toLong, direction(Input)))
          .flatMap(Stream.chunk)
          .onFinalizeCase {
            case Resource.ExitCase.Errored(e) => recordError(Input, e)
            case _                            => Temporal[F].unit
          }

      def flush: F[Unit] = inner.flush *> flushes.add(1L, direction(Input))
    }

  private def playback(inner: AudioPlayback[F]): AudioPlayback[F] =
    new AudioPlayback[F] {
      def format: AudioFormat = inner.format

      def write(bytes: Array[Byte], length: Int): F[Unit] =
        inner
          .write(bytes, length)
          .onError { case e => recordError(Output, e) } *>
          dataSize.add(length.toLong, direction(Output))

      def flush: F[Unit] = inner.flush *> flushes.add(1L, direction(Output))
    }
}

object AudioTelemetry {

  private sealed trait Direction
  private case object Input  extends Direction
  private case object Output extends Direction

  private def direction(d: Direction): Attribute[String] =
    Attribute(
      "sound4s.audio.direction",
      d match {
        case Input  => "input"
        case Output => "output"
      }
    )

  private def formatAttributes(format: AudioFormat): Attributes =
    Attributes(
      Attribute("sound4s.audio.sample_rate", format.sampleRate.toLong),
      Attribute("sound4s.audio.bit_depth", format.bitDepth.toLong),
      Attribute("sound4s.audio.channels", format.channels.toLong)
    )

  /** Telemetry reported through the providers in scope, under the
    * instrumentation scope `sound4s`.
    */
  def create[F[_]: Temporal: MeterProvider: TracerProvider]
      : F[AudioTelemetry[F]] =
    for {
      meter           <- MeterProvider[F].get("sound4s")
      tracerOfSound4s <- TracerProvider[F].get("sound4s")
      streamsActive   <- meter
        .upDownCounter[Long]("sound4s.audio.streams.active")
        .withUnit("{stream}")
        .withDescription("The audio streams that are open.")
        .create
      dataSize <- meter
        .counter[Long]("sound4s.audio.data.size")
        .withUnit("By")
        .withDescription("The audio read from the microphone or played.")
        .create
      flushes <- meter
        .counter[Long]("sound4s.audio.flushes")
        .withUnit("{flush}")
        .withDescription(
          "The times buffered audio was discarded: at the start of each " +
            "reading on input, and on output when a sink was cancelled or failed."
        )
        .create
      errors <- meter
        .counter[Long]("sound4s.audio.errors")
        .withUnit("{error}")
        .withDescription("The failures to open a device or while streaming.")
        .create
      openDuration <- meter
        .histogram[Double]("sound4s.audio.open.duration")
        .withUnit("s")
        .withDescription("How long it took to open a device.")
        .create
    } yield new AudioTelemetry[F](
      streamsActive,
      dataSize,
      flushes,
      errors,
      openDuration,
      tracerOfSound4s
    )

  /** Telemetry that reports nothing, for when none is configured. */
  def noop[F[_]: Temporal]: F[AudioTelemetry[F]] =
    create[F](using Temporal[F], MeterProvider.noop[F], TracerProvider.noop[F])
}
