package sound4s

import cats.effect.Sync
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fs2.Stream
import org.typelevel.log4cats.Logger

import javax.sound.sampled.{
  AudioFormat as JavaAudioFormat,
  AudioInputStream,
  AudioSystem,
  DataLine,
  Mixer,
  SourceDataLine,
  TargetDataLine
}

/** The `javax.sound.sampled` implementation of the audio backend. */
private[sound4s] object JavaSoundBackend {

  def toJavaFormat(format: AudioFormat): JavaAudioFormat =
    new JavaAudioFormat(
      format.sampleRate.toFloat,
      format.bitDepth,
      format.channels,
      format.signed,
      format.bigEndian
    )

  private def idOf(info: Mixer.Info): String =
    List(info.getName, info.getVendor, info.getVersion, info.getDescription)
      .mkString("|")

  def deviceOf(info: Mixer.Info): AudioDevice = {
    val mixer = AudioSystem.getMixer(info)
    val plays = mixer.getSourceLineInfo.exists(i =>
      classOf[SourceDataLine].isAssignableFrom(i.getLineClass)
    )
    val record = mixer.getTargetLineInfo.exists(i =>
      classOf[TargetDataLine].isAssignableFrom(i.getLineClass)
    )
    AudioDevice(
      id = idOf(info),
      name = info.getName,
      kinds = Set(
        Option.when(record)(AudioDevice.Kind.Input),
        Option.when(plays)(AudioDevice.Kind.Output)
      ).flatten
    )
  }

  def mixerInfoOf(device: AudioDevice): Option[Mixer.Info] =
    AudioSystem.getMixerInfo.find(idOf(_) == device.id)

  /** Opens the mixer of `device`, or the system default if it is `None`,
    * closing it on release.
    *
    * @return
    *   fails with [[AudioError.DeviceNotFound]] if `device` is no longer
    *   installed
    */
  def mixer[F[_]: Sync](
      device: Option[AudioDevice],
      logger: Logger[F]
  ): Resource[F, Mixer] =
    Resource.make(
      for {
        info <- device.traverse(d =>
          Sync[F]
            .blocking(mixerInfoOf(d))
            .flatMap(_.liftTo[F](AudioError.DeviceNotFound(d.id)))
        )
        mixer <- Sync[F].blocking(AudioSystem.getMixer(info.orNull))
        _     <- logger.info("Mixer opened")
      } yield mixer
    )(mixer => Sync[F].blocking(mixer.close()) <* logger.info("Mixer closed"))

  def openTargetDataLine[F[_]: Sync](
      mixer: Mixer,
      format: AudioFormat,
      logger: Logger[F]
  ): Resource[F, TargetDataLine] = {
    val javaFormat = toJavaFormat(format)
    Resource.make {
      for {
        raw <- Sync[F].blocking(
          mixer.getLine(new DataLine.Info(classOf[TargetDataLine], javaFormat))
        )
        tdl <- raw match {
          case tdl: TargetDataLine => Sync[F].pure(tdl)
          case other               =>
            Sync[F].raiseError[TargetDataLine](
              AudioError.UnexpectedLineType(
                "TargetDataLine",
                other.getClass.getSimpleName
              )
            )
        }
        _ <- Sync[F].blocking { tdl.open(javaFormat); tdl.start() }
        _ <- logger.info("Microphone opened")
      } yield tdl
    } { tdl =>
      Sync[F].blocking {
        tdl.stop()
        tdl.close()
      } <* logger.info("Microphone closed")
    }
  }

  def openSourceDataLine[F[_]: Sync](
      mixer: Mixer,
      format: AudioFormat,
      logger: Logger[F]
  ): Resource[F, SourceDataLine] = {
    val javaFormat = toJavaFormat(format)
    Resource.make(
      for {
        raw <- Sync[F].blocking(
          mixer.getLine(new DataLine.Info(classOf[SourceDataLine], javaFormat))
        )
        sdl <- raw match {
          case sdl: SourceDataLine => Sync[F].pure(sdl)
          case other               =>
            Sync[F].raiseError[SourceDataLine](
              AudioError.UnexpectedLineType(
                "SourceDataLine",
                other.getClass.getSimpleName
              )
            )
        }
        _ <- Sync[F].blocking { sdl.open(javaFormat); sdl.start() }
        _ <- logger.info("Speakers opened")
      } yield sdl
    ) { sdl =>
      Sync[F].blocking {
        sdl.drain()
        sdl.stop()
        sdl.close()
      } <* logger.info("Speakers closed")
    }
  }

  def capture[F[_]: Sync](
      tdl: TargetDataLine,
      audioFormat: AudioFormat
  ): AudioCapture[F] = new AudioCapture[F] {
    def format: AudioFormat = audioFormat

    def read(chunkSize: Int): Stream[F, Byte] =
      fs2.io.readInputStream(
        Sync[F].delay(new AudioInputStream(tdl)),
        chunkSize,
        false
      )

    def flush: F[Unit] = Sync[F].delay(tdl.flush())
  }

  def playback[F[_]: Sync](
      sdl: SourceDataLine,
      audioFormat: AudioFormat
  ): AudioPlayback[F] = new AudioPlayback[F] {
    def format: AudioFormat = audioFormat

    def write(bytes: Array[Byte], length: Int): F[Unit] =
      Sync[F].blocking {
        sdl.write(bytes, 0, length)
        ()
      }

    def flush: F[Unit] = Sync[F].delay(sdl.flush())
  }
}
