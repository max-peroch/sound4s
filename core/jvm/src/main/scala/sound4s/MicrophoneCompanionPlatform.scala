package sound4s

import cats.effect.Async
import cats.effect.kernel.Resource
import org.typelevel.log4cats.Logger

private[sound4s] trait MicrophoneCompanionPlatform {

  /** Opens the microphone, starting capture on acquisition and stopping it on
    * release.
    *
    * @param audioFormat
    *   the PCM format to capture in
    * @param logger
    *   receives an info message when the microphone is opened and closed
    * @param device
    *   the device to capture from, as returned by [[AudioDevices.findByName]];
    *   `None` uses the system default
    * @return
    *   fails with [[AudioError.DeviceNotFound]] if `device` is no longer
    *   installed, or [[AudioError.UnexpectedLineType]] if the device's line for
    *   this format cannot capture
    */
  def resource[F[_]: Async](
      audioFormat: AudioFormat,
      logger: Logger[F],
      device: Option[AudioDevice] = None
  ): Resource[F, Microphone[F]] =
    for {
      mixer <- JavaSoundBackend.mixer(device, logger)
      tdl   <- JavaSoundBackend.openTargetDataLine(mixer, audioFormat, logger)
    } yield new Microphone(JavaSoundBackend.capture[F](tdl, audioFormat))
}
