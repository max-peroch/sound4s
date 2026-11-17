package sound4s

import scala.scalanative.unsafe.*

/** The C wrapper over miniaudio in `scala-native/sound4s_audio.c`. */
@link("pthread")
@link("m")
@link("dl")
@extern
private[sound4s] object Miniaudio {
  def s4s_list_devices(playback: CInt, out: Ptr[CChar], outLen: CInt): CInt =
    extern

  def s4s_open(
      result: Ptr[Ptr[Byte]],
      playback: CInt,
      name: CString,
      sampleRate: CInt,
      channels: CInt,
      bitDepth: CInt,
      isSigned: CInt,
      bufferMillis: CInt
  ): CInt = extern

  def s4s_read(
      stream: Ptr[Byte],
      dst: Ptr[Byte],
      bytes: CInt,
      timeoutMillis: CInt
  ): CInt = extern

  def s4s_write(
      stream: Ptr[Byte],
      src: Ptr[Byte],
      bytes: CInt,
      timeoutMillis: CInt
  ): CInt = extern

  def s4s_flush(stream: Ptr[Byte]): Unit = extern

  def s4s_close(stream: Ptr[Byte], drainTimeoutMillis: CInt): Unit = extern
}
