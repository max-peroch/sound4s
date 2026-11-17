package sound4s

import cats.effect.Sync
import cats.effect.kernel.Resource
import cats.syntax.all.*
import org.typelevel.log4cats.Logger

import javax.sound.sampled.{AudioSystem, Mixer}

object MixerLookup {

  /** Opens the given audio mixer, or the system default if `mixerInfo` is
    * `None`, closing it on release.
    *
    * @param mixerInfo
    *   the mixer to open, as returned by [[findByName]]; `None` opens the
    *   system default mixer
    * @param logger
    *   receives an info message when the mixer is opened and closed
    */
  def resource[F[_]: Sync](
      mixerInfo: Option[Mixer.Info],
      logger: Logger[F]
  ): Resource[F, Mixer] =
    Resource.make(
      Sync[F].blocking(AudioSystem.getMixer(mixerInfo.orNull))
        <* logger.info("Mixer opened")
    )(mixer => Sync[F].blocking(mixer.close()) <* logger.info("Mixer closed"))

  /** Looks up an installed mixer whose name contains `name`.
    *
    * @param name
    *   a substring to match against each mixer's name, e.g. a device name
    *   fragment reported by the OS
    * @return
    *   the first matching mixer's info, or `None` if no mixer matches
    */
  def findByName[F[_]: Sync](name: String): F[Option[Mixer.Info]] =
    Sync[F].blocking {
      AudioSystem.getMixerInfo.find(_.getName.contains(name))
    }
}
