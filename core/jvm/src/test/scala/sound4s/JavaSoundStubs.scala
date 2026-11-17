package sound4s

import scala.collection.mutable.ArrayBuffer

import javax.sound.sampled.*

/** Inert `javax.sound.sampled` lines and mixers for tests. */
object JavaSoundStubs {

  class StubLine extends Line {
    def getLineInfo: Line.Info                       = null
    def open(): Unit                                 = ()
    def close(): Unit                                = ()
    def isOpen: Boolean                              = false
    def getControls: Array[Control]                  = Array.empty
    def isControlSupported(t: Control.Type): Boolean = false
    def getControl(t: Control.Type): Control         = null
    def addLineListener(l: LineListener): Unit       = ()
    def removeLineListener(l: LineListener): Unit    = ()
  }

  class StubMixer(line: Line) extends Mixer {
    def getLine(info: Line.Info): Line                               = line
    def getMixerInfo: Mixer.Info                                     = ???
    def getSourceLineInfo(): Array[Line.Info]                        = ???
    def getTargetLineInfo(): Array[Line.Info]                        = ???
    def getSourceLineInfo(info: Line.Info): Array[Line.Info]         = ???
    def getTargetLineInfo(info: Line.Info): Array[Line.Info]         = ???
    def isLineSupported(info: Line.Info): Boolean                    = ???
    def getMaxLines(info: Line.Info): Int                            = ???
    def getSourceLines(): Array[Line]                                = ???
    def getTargetLines(): Array[Line]                                = ???
    def synchronize(lines: Array[Line], maintainSync: Boolean): Unit = ???
    def unsynchronize(lines: Array[Line]): Unit                      = ???
    def isSynchronizationSupported(
        lines: Array[Line],
        maintainSync: Boolean
    ): Boolean                                       = ???
    def getLineInfo: Line.Info                       = ???
    def open(): Unit                                 = ???
    def close(): Unit                                = ???
    def isOpen: Boolean                              = ???
    def getControls: Array[Control]                  = ???
    def isControlSupported(t: Control.Type): Boolean = ???
    def getControl(t: Control.Type): Control         = ???
    def addLineListener(l: LineListener): Unit       = ???
    def removeLineListener(l: LineListener): Unit    = ???
  }

  def sourceDataLine(
      fmt: AudioFormat,
      writes: ArrayBuffer[Array[Byte]] = ArrayBuffer.empty,
      events: ArrayBuffer[String] = ArrayBuffer.empty
  ): SourceDataLine = new SourceDataLine {
    def write(b: Array[Byte], off: Int, len: Int): Int = {
      writes += java.util.Arrays.copyOfRange(b, off, off + len)
      len
    }
    def getFormat: AudioFormat                       = fmt
    def open(f: AudioFormat, bufSize: Int): Unit     = ()
    def open(f: AudioFormat): Unit                   = ()
    def drain(): Unit                                = ()
    def flush(): Unit                                = { events += "flush"; () }
    def start(): Unit                                = ()
    def stop(): Unit                                 = ()
    def isRunning: Boolean                           = false
    def isActive: Boolean                            = false
    def getBufferSize: Int                           = 0
    def available(): Int                             = 0
    def getFramePosition: Int                        = 0
    def getLongFramePosition: Long                   = 0L
    def getMicrosecondPosition: Long                 = 0L
    def getLevel: Float                              = 0f
    def getLineInfo: Line.Info                       = null
    def open(): Unit                                 = ()
    def close(): Unit                                = ()
    def isOpen: Boolean                              = false
    def getControls: Array[Control]                  = Array.empty
    def isControlSupported(t: Control.Type): Boolean = false
    def getControl(t: Control.Type): Control         = null
    def addLineListener(l: LineListener): Unit       = ()
    def removeLineListener(l: LineListener): Unit    = ()
  }

  def targetDataLine(
      fmt: AudioFormat,
      events: ArrayBuffer[String] = ArrayBuffer.empty
  ): TargetDataLine = new TargetDataLine {
    def read(b: Array[Byte], off: Int, len: Int): Int = {
      events += "read"; len
    }
    def getFormat: AudioFormat                       = fmt
    def open(f: AudioFormat, bufSize: Int): Unit     = ()
    def open(f: AudioFormat): Unit                   = ()
    def drain(): Unit                                = ()
    def flush(): Unit                                = { events += "flush"; () }
    def start(): Unit                                = ()
    def stop(): Unit                                 = ()
    def isRunning: Boolean                           = false
    def isActive: Boolean                            = false
    def getBufferSize: Int                           = 0
    def available(): Int                             = 0
    def getFramePosition: Int                        = 0
    def getLongFramePosition: Long                   = 0L
    def getMicrosecondPosition: Long                 = 0L
    def getLevel: Float                              = 0f
    def getLineInfo: Line.Info                       = null
    def open(): Unit                                 = ()
    def close(): Unit                                = ()
    def isOpen: Boolean                              = false
    def getControls: Array[Control]                  = Array.empty
    def isControlSupported(t: Control.Type): Boolean = false
    def getControl(t: Control.Type): Control         = null
    def addLineListener(l: LineListener): Unit       = ()
    def removeLineListener(l: LineListener): Unit    = ()
  }
}
