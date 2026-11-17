package sound4s

import cats.effect.Async
import cats.effect.kernel.Resource
import org.typelevel.log4cats.Logger

private[sound4s] trait SpeakersCompanionPlatform {

  /** Opens the speakers through the browser, starting playback on acquisition
    * and draining and stopping it on release.
    *
    * The browser may require this to be called from a user gesture such as a
    * click.
    *
    * @param audioFormat
    *   the PCM format to play: 8-bit unsigned or 16, 24 or 32-bit signed,
    *   little-endian. The browser must support audio at this sample rate.
    * @param logger
    *   receives an info message when the speakers are opened and closed
    * @param device
    *   the device to play through, as returned by [[AudioDevices.list]]; `None`
    *   uses the browser's default. Choosing one needs `AudioContext.setSinkId`
    *   (Chromium).
    * @return
    *   fails with [[AudioError.DeviceNotFound]] if `device` is gone,
    *   [[AudioError.UnsupportedFormat]] if the format or sample rate can't be
    *   used, or [[AudioError.DeviceFailure]] if the browser won't start audio
    *   or Web Audio is not available (e.g. outside a browser)
    */
  def resource[F[_]: Async](
      audioFormat: AudioFormat,
      logger: Logger[F],
      device: Option[AudioDevice] = None
  ): Resource[F, Speakers[F]] =
    WebAudioBackend
      .openPlayback[F](audioFormat, device, logger)
      .map(new Speakers(_))
}
