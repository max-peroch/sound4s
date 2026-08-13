package sound4s

import cats.effect.Sync
import cats.effect.kernel.Resource
import cats.syntax.all.*
import org.typelevel.log4cats.Logger

import javax.sound.sampled.{AudioFormat, DataLine, Mixer, TargetDataLine}

object Microphone {

  /** Opens the microphone as a [[javax.sound.sampled.TargetDataLine]], starting
    * capture on acquisition and stopping it on release.
    *
    * @param audioFormat
    *   the PCM format to capture in
    * @param logger
    *   receives an info message when the microphone is opened and closed
    * @param mixerInfo
    *   the mixer to capture from, as returned by [[MixerLookup.findByName]];
    *   `None` uses the system default mixer
    * @return
    *   fails with [[AudioError.UnexpectedLineType]] if the mixer's line for
    *   this format is not a `TargetDataLine`
    */
  def resource[F[_]: Sync](
      audioFormat: AudioFormat,
      logger: Logger[F],
      mixerInfo: Option[Mixer.Info] = None
  ): Resource[F, TargetDataLine] =
    for {
      mixer <- MixerLookup.resource(mixerInfo, logger)
      tdl   <- openTargetDataLine(mixer, audioFormat, logger)
    } yield tdl

  private[sound4s] def openTargetDataLine[F[_]: Sync](
      mixer: Mixer,
      audioFormat: AudioFormat,
      logger: Logger[F]
  ): Resource[F, TargetDataLine] =
    Resource.make {
      for {
        raw <- Sync[F].blocking(
          mixer.getLine(new DataLine.Info(classOf[TargetDataLine], audioFormat))
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
        _ <- Sync[F].blocking { tdl.open(audioFormat); tdl.start() }
        _ <- logger.info("Microphone opened")
      } yield tdl
    } { tdl =>
      Sync[F].blocking {
        tdl.stop()
        tdl.close()
      } <* logger.info("Microphone closed")
    }
}
