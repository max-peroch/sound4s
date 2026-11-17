package sound4s

import cats.effect.Async
import cats.effect.kernel.Resource
import org.typelevel.log4cats.Logger

private[sound4s] trait SpeakersCompanionPlatform {

  /** Opens the speakers, starting playback on acquisition and draining and
    * stopping it on release.
    *
    * @param audioFormat
    *   the PCM format to play: 8-bit unsigned or 16, 24 or 32-bit signed,
    *   little-endian. It is converted to the device's own rate.
    * @param logger
    *   receives an info message when the speakers are opened and closed
    * @param device
    *   the device to play through, as returned by [[AudioDevices.findByName]];
    *   `None` uses the system default
    * @return
    *   fails with [[AudioError.DeviceNotFound]] if `device` is no longer
    *   installed, [[AudioError.UnsupportedFormat]] if the format can't be
    *   played, or [[AudioError.DeviceFailure]] if the device can't be opened
    */
  def resource[F[_]: Async](
      audioFormat: AudioFormat,
      logger: Logger[F],
      device: Option[AudioDevice] = None
  ): Resource[F, Speakers[F]] =
    NativeBackend
      .openPlayback[F](audioFormat, device, logger)
      .map(new Speakers(_))
}
