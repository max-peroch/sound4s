package sound4s

import cats.effect.{Async, Sync}
import cats.effect.kernel.Resource
import fs2.Stream
import fs2.io.{readClassLoaderResource, readInputStream, toInputStream}

import java.io.{BufferedInputStream, InputStream}
import javax.sound.sampled.{AudioFormat, AudioInputStream, AudioSystem}

/** Streams PCM audio from a classpath resource (WAV / AU / AIFF), transcoded to
  * `audioFormat` regardless of the file's native encoding.
  *
  * @param name
  *   the classpath resource to read, validated to exist via
  *   [[ResourceName.apply]]
  * @param audioFormat
  *   the PCM format to transcode the file to
  * @param readChunkSize
  *   the number of bytes read per chunk; defaults to 4096
  */
final class AudioFile[F[_]: Async](
    name: ResourceName,
    audioFormat: AudioFormat,
    readChunkSize: Int = AudioFile.DefaultReadChunkSize
) {

  /** The file's PCM bytes, transcoded to `audioFormat`.
    *
    * @return
    *   fails with [[AudioError.ResourceNotFound]] if the resource can no longer
    *   be read from the classpath
    */
  def source: Stream[F, Byte] =
    readClassLoaderResource[F](name.value)
      .handleErrorWith { case _: java.io.IOException =>
        Stream.raiseError(AudioError.ResourceNotFound(name.value))
      }
      .through(toInputStream)
      .flatMap { is =>
        Stream
          .resource(convertedStream(is))
          .flatMap { converted =>
            readInputStream(
              Sync[F].pure[InputStream](converted),
              chunkSize = readChunkSize,
              closeAfterUse = false
            )
          }
      }

  private def convertedStream(
      is: InputStream
  ): Resource[F, AudioInputStream] =
    for {
      ais <- Resource.fromAutoCloseable(
        Sync[F].blocking(
          AudioSystem.getAudioInputStream(new BufferedInputStream(is))
        )
      )
      converted <- Resource.fromAutoCloseable(
        Sync[F].blocking(AudioSystem.getAudioInputStream(audioFormat, ais))
      )
    } yield converted
}

object AudioFile {
  private val DefaultReadChunkSize: Int = 4096
}
