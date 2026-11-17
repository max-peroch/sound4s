package sound4s

import cats.effect.kernel.Async
import cats.effect.std.Queue
import fs2.{Chunk, Stream}

/** Capture from the blocks of PCM that the capture worklet posts, which the
  * backend offers to `queue`; if the reader falls behind, the oldest are
  * dropped.
  */
private[sound4s] final class WebCapture[F[_]: Async](
    queue: Queue[F, Array[Byte]],
    audioFormat: AudioFormat
) extends AudioCapture[F] {

  def format: AudioFormat = audioFormat

  def read(chunkSize: Int): Stream[F, Byte] =
    Stream
      .fromQueueUnterminated(queue)
      .flatMap(block => Stream.chunk(Chunk.array(block)))
      .chunkN(chunkSize)
      .flatMap(Stream.chunk)

  def flush: F[Unit] = {
    import cats.syntax.all.*
    queue.tryTakeN(None).void
  }
}
