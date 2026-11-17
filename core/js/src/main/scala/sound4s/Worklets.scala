package sound4s

/** The AudioWorklet processors, loaded into the browser from a `Blob`. */
private[sound4s] object Worklets {

  val CaptureName  = "sound4s-capture"
  val PlaybackName = "sound4s-playback"

  /** Posts each block of input as interleaved `Float32Array` to the main
    * thread. Its output stays silent.
    */
  private val capture =
    s"""class Sound4sCapture extends AudioWorkletProcessor {
       |  process(inputs) {
       |    const input = inputs[0];
       |    if (input && input.length > 0 && input[0].length > 0) {
       |      const frames = input[0].length;
       |      const channels = input.length;
       |      const out = new Float32Array(frames * channels);
       |      for (let c = 0; c < channels; c++) {
       |        const data = input[c];
       |        for (let i = 0; i < frames; i++) out[i * channels + c] = data[i];
       |      }
       |      this.port.postMessage(out, [out.buffer]);
       |    }
       |    return true;
       |  }
       |}
       |registerProcessor('$CaptureName', Sound4sCapture);
       |""".stripMargin

  /** Plays the interleaved `Float32Array` blocks posted to it, silence when it
    * has none, and reports the running total of frames it has taken, played or
    * dropped by a flush.
    */
  private val playback =
    s"""class Sound4sPlayback extends AudioWorkletProcessor {
       |  constructor(options) {
       |    super();
       |    this.channels = options.processorOptions.channels;
       |    this.queue = [];
       |    this.offset = 0;
       |    this.queued = 0;
       |    this.consumed = 0;
       |    this.reported = 0;
       |    this.quanta = 0;
       |    this.port.onmessage = (event) => {
       |      const message = event.data;
       |      if (message.flush) {
       |        this.consumed += this.queued;
       |        this.queue = [];
       |        this.offset = 0;
       |        this.queued = 0;
       |        this.report();
       |      } else {
       |        this.queue.push(message);
       |        this.queued += message.length / this.channels;
       |      }
       |    };
       |  }
       |  report() {
       |    if (this.consumed !== this.reported) {
       |      this.reported = this.consumed;
       |      this.port.postMessage({ consumed: this.consumed });
       |    }
       |  }
       |  process(inputs, outputs) {
       |    const out = outputs[0];
       |    const frames = out[0].length;
       |    let written = 0;
       |    while (written < frames && this.queue.length > 0) {
       |      const head = this.queue[0];
       |      const headFrames = head.length / this.channels;
       |      const n = Math.min(frames - written, headFrames - this.offset);
       |      for (let c = 0; c < out.length; c++) {
       |        const channel = out[c];
       |        const source = Math.min(c, this.channels - 1);
       |        for (let i = 0; i < n; i++) {
       |          channel[written + i] = head[(this.offset + i) * this.channels + source];
       |        }
       |      }
       |      written += n;
       |      this.offset += n;
       |      if (this.offset >= headFrames) {
       |        this.queue.shift();
       |        this.offset = 0;
       |      }
       |    }
       |    this.queued -= written;
       |    this.consumed += written;
       |    if (++this.quanta % 8 === 0 || this.queued === 0) this.report();
       |    return true;
       |  }
       |}
       |registerProcessor('$PlaybackName', Sound4sPlayback);
       |""".stripMargin

  val Source: String = capture + playback
}
