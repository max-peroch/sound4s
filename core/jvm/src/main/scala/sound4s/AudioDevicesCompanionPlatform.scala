package sound4s

import cats.effect.Async

import javax.sound.sampled.AudioSystem

private[sound4s] trait AudioDevicesCompanionPlatform {

  /** Lists the installed audio devices. */
  def list[F[_]: Async]: F[List[AudioDevice]] =
    Async[F].blocking(
      AudioSystem.getMixerInfo.toList.map(JavaSoundBackend.deviceOf)
    )
}
