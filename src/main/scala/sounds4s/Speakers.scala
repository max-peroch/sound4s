package sounds4s

import cats.effect.Sync
import cats.effect.kernel.Resource
import cats.syntax.all.*
import org.typelevel.log4cats.Logger

import javax.sound.sampled.{AudioFormat, DataLine, Mixer, SourceDataLine}

object Speakers {

  /** Opens the speakers as a [[javax.sound.sampled.SourceDataLine]], starting
    * playback on acquisition and draining and stopping it on release.
    *
    * @param audioFormat
    *   the PCM format to play
    * @param logger
    *   receives an info message when the speakers are opened and closed
    * @param mixerInfo
    *   the mixer to play through, as returned by [[MixerLookup.findByName]];
    *   `None` uses the system default mixer
    * @return
    *   fails with [[AudioError.UnexpectedLineType]] if the mixer's line for
    *   this format is not a `SourceDataLine`
    */
  def resource[F[_]: Sync](
      audioFormat: AudioFormat,
      logger: Logger[F],
      mixerInfo: Option[Mixer.Info] = None
  ): Resource[F, SourceDataLine] =
    for {
      mixer <- MixerLookup.resource(mixerInfo, logger)
      sdl   <- openSourceDataLine(mixer, audioFormat, logger)
    } yield sdl

  private[sounds4s] def openSourceDataLine[F[_]: Sync](
      mixer: Mixer,
      audioFormat: AudioFormat,
      logger: Logger[F]
  ): Resource[F, SourceDataLine] =
    Resource.make(
      for {
        raw <- Sync[F].blocking(
          mixer.getLine(new DataLine.Info(classOf[SourceDataLine], audioFormat))
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
        _ <- Sync[F].blocking { sdl.open(audioFormat); sdl.start() }
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
