package sound4s

import fs2.{Pipe, Stream}

import scala.concurrent.duration.FiniteDuration

/** A microphone and speakers opened together, chunked to a common frame size so
  * `source` output can be piped straight into `sink` (e.g. for loopback or RTP
  * relaying).
  *
  * Instances are only obtained via [[SoundStreaming.resource]].
  */
final class SoundStreaming[F[_]] private[sound4s] (
    microphone: Microphone[F],
    speakers: Speakers[F],
    rtpPacketInterval: FiniteDuration
) {

  private val chunkSize: Int = microphone.format.chunkSize(rtpPacketInterval)

  /** Raw PCM captured from the microphone, in `chunkSize`-byte chunks; see
    * [[Microphone.source]].
    */
  def source: Stream[F, Byte] = microphone.source(chunkSize)

  /** Writes raw PCM to the speakers; see [[Speakers.sink]]. */
  def sink: Pipe[F, Byte, Unit] = speakers.sink(chunkSize)
}

object SoundStreaming extends SoundStreamingCompanionPlatform
