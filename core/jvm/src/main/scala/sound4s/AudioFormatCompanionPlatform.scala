package sound4s

import javax.sound.sampled.{AudioFormat as JavaAudioFormat, AudioSystem}

private[sound4s] trait AudioFormatCompanionPlatform {

  /** Converts a `javax.sound.sampled.AudioFormat` into an [[AudioFormat]].
    *
    * @return
    *   the converted format, or an [[AudioError.InvalidFormat]] explaining why
    *   `format` cannot be represented: it isn't signed or unsigned PCM, or its
    *   sample rate, sample size or channel count is unspecified
    */
  def fromJavaFormat(
      format: JavaAudioFormat
  ): Either[AudioError, AudioFormat] = {
    val encoding                = format.getEncoding
    def invalid(reason: String) =
      AudioError.InvalidFormat(format.toString, reason)
    val signed =
      if (encoding == JavaAudioFormat.Encoding.PCM_SIGNED) Right(true)
      else if (encoding == JavaAudioFormat.Encoding.PCM_UNSIGNED) Right(false)
      else
        Left(invalid(s"unsupported encoding $encoding, only PCM is supported"))
    val sampleRate = format.getSampleRate
    val bitDepth   = format.getSampleSizeInBits
    val channels   = format.getChannels
    for {
      isSigned <- signed
      _        <- Either.cond(
        sampleRate != AudioSystem.NOT_SPECIFIED && sampleRate >= 1f,
        (),
        invalid(s"invalid sample rate $sampleRate")
      )
      _ <- Either.cond(
        bitDepth > 0,
        (),
        invalid(s"invalid sample size $bitDepth")
      )
      _ <- Either.cond(
        channels > 0,
        (),
        invalid(s"invalid channel count $channels")
      )
    } yield AudioFormat(
      sampleRate.toInt,
      bitDepth,
      channels,
      isSigned,
      format.isBigEndian
    )
  }
}
