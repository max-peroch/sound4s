package sound4s

import cats.effect.Async
import cats.effect.kernel.Resource
import org.typelevel.log4cats.Logger

private[sound4s] trait SpeakersCompanionPlatform {

  /** Opens the speakers, starting playback on acquisition and draining and
    * stopping it on release.
    *
    * @param audioFormat
    *   the PCM format to play
    * @param logger
    *   receives an info message when the speakers are opened and closed
    * @param device
    *   the device to play through, as returned by [[AudioDevices.findByName]];
    *   `None` uses the system default
    * @return
    *   fails with [[AudioError.DeviceNotFound]] if `device` is no longer
    *   installed, or [[AudioError.UnexpectedLineType]] if the device's line for
    *   this format cannot play
    */
  def resource[F[_]: Async](
      audioFormat: AudioFormat,
      logger: Logger[F],
      device: Option[AudioDevice] = None
  ): Resource[F, Speakers[F]] =
    for {
      mixer <- JavaSoundBackend.mixer(device, logger)
      sdl   <- JavaSoundBackend.openSourceDataLine(mixer, audioFormat, logger)
    } yield new Speakers(JavaSoundBackend.playback[F](sdl, audioFormat))
}
