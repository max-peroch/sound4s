package sound4s

import cats.effect.Sync
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fs2.{Chunk, Stream}
import org.typelevel.log4cats.Logger

import scala.scalanative.unsafe.*

/** The miniaudio implementation of the audio backend, for Scala Native. */
private[sound4s] object NativeBackend {

  private val Ok          = 0
  private val NotFound    = 1
  private val Unsupported = 2

  /** How long the ring buffer between the audio thread and Scala holds. */
  private val BufferMillis = 500

  /** The longest a single read or write waits before giving control back, so a
    * stalled device doesn't hold a fiber that was cancelled.
    */
  private val WaitMillis = 50

  /** The longest closing a playback stream waits for its audio to play. */
  private val DrainMillis = BufferMillis + 200

  private val ListBufferSize = 64 * 1024

  private final class Handle(val pointer: Ptr[Byte]) extends AnyVal

  def devices[F[_]: Sync]: F[List[AudioDevice]] =
    Sync[F].blocking {
      val outputs = deviceNames(playback = true)
      val inputs  = deviceNames(playback = false)
      (outputs ++ inputs).distinct.map { name =>
        AudioDevice(
          id = name,
          name = name,
          kinds = Set(
            Option.when(inputs.contains(name))(AudioDevice.Kind.Input),
            Option.when(outputs.contains(name))(AudioDevice.Kind.Output)
          ).flatten
        )
      }
    }

  private def deviceNames(playback: Boolean): List[String] =
    Zone {
      val out    = alloc[CChar](ListBufferSize)
      val status =
        Miniaudio.s4s_list_devices(
          if (playback) 1 else 0,
          out,
          ListBufferSize
        )
      if (status == Ok) fromCString(out).split('\n').toList.filter(_.nonEmpty)
      else Nil
    }

  /** Opens a stream on `device`, or the system default if it is `None`, closing
    * it on release.
    */
  private def open[F[_]: Sync](
      playback: Boolean,
      what: String,
      format: AudioFormat,
      device: Option[AudioDevice],
      logger: Logger[F]
  ): Resource[F, Ptr[Byte]] =
    Resource.make(
      for {
        _ <- Sync[F].raiseWhen(format.bigEndian)(
          AudioError.UnsupportedFormat(format, "big-endian samples")
        )
        pointer <- Sync[F]
          .blocking {
            Zone {
              val result = alloc[Ptr[Byte]](1)
              val name   = device.fold(null: CString)(d => toCString(d.id))
              val status = Miniaudio.s4s_open(
                result,
                if (playback) 1 else 0,
                name,
                format.sampleRate,
                format.channels,
                format.bitDepth,
                if (format.signed) 1 else 0,
                BufferMillis
              )
              (status, !result)
            }
          }
          .flatMap {
            case (Ok, pointer) => Sync[F].pure(pointer)
            case (NotFound, _) =>
              Sync[F].raiseError[Ptr[Byte]](
                AudioError.DeviceNotFound(device.fold("default")(_.id))
              )
            case (Unsupported, _) =>
              Sync[F].raiseError[Ptr[Byte]](
                AudioError.UnsupportedFormat(
                  format,
                  "only 8-bit unsigned and 16, 24 or 32-bit signed PCM"
                )
              )
            case _ =>
              Sync[F].raiseError[Ptr[Byte]](
                AudioError.DeviceFailure(
                  device.fold("default")(_.id),
                  s"$what could not be opened"
                )
              )
          }
        _ <- logger.info(s"$what opened")
      } yield pointer
    )(pointer =>
      Sync[F].blocking(
        Miniaudio.s4s_close(pointer, if (playback) DrainMillis else 0)
      ) <* logger.info(s"$what closed")
    )

  def openCapture[F[_]: Sync](
      format: AudioFormat,
      device: Option[AudioDevice],
      logger: Logger[F]
  ): Resource[F, AudioCapture[F]] =
    open(playback = false, "Microphone", format, device, logger)
      .map(capture(_, format))

  def openPlayback[F[_]: Sync](
      format: AudioFormat,
      device: Option[AudioDevice],
      logger: Logger[F]
  ): Resource[F, AudioPlayback[F]] =
    open(playback = true, "Speakers", format, device, logger)
      .map(playback(_, format))

  private def capture[F[_]: Sync](
      stream: Ptr[Byte],
      audioFormat: AudioFormat
  ): AudioCapture[F] = new AudioCapture[F] {
    def format: AudioFormat = audioFormat

    def read(chunkSize: Int): Stream[F, Byte] =
      Stream.repeatEval(readChunk(chunkSize)).flatMap(Stream.chunk)

    private def readChunk(chunkSize: Int): F[Chunk[Byte]] = {
      val bytes                           = new Array[Byte](chunkSize)
      def fill(from: Int): F[Chunk[Byte]] =
        Sync[F]
          .blocking(
            Miniaudio.s4s_read(
              stream,
              bytes.atUnsafe(from),
              chunkSize - from,
              WaitMillis
            )
          )
          .flatMap { got =>
            if (from + got >= chunkSize) Sync[F].pure(Chunk.array(bytes))
            else fill(from + got)
          }
      fill(0)
    }

    def flush: F[Unit] = Sync[F].blocking(Miniaudio.s4s_flush(stream))
  }

  private def playback[F[_]: Sync](
      stream: Ptr[Byte],
      audioFormat: AudioFormat
  ): AudioPlayback[F] = new AudioPlayback[F] {
    def format: AudioFormat = audioFormat

    def write(bytes: Array[Byte], length: Int): F[Unit] = {
      def put(from: Int): F[Unit] =
        Sync[F]
          .blocking(
            Miniaudio.s4s_write(
              stream,
              bytes.atUnsafe(from),
              length - from,
              WaitMillis
            )
          )
          .flatMap(wrote => put(from + wrote).whenA(from + wrote < length))
      put(0)
    }

    def flush: F[Unit] = Sync[F].blocking(Miniaudio.s4s_flush(stream))
  }
}
