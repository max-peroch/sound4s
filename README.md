# sound4s

A thin, resource-safe wrapper around the platform's audio API for the Typelevel
ecosystem, exposing microphone and speaker hardware as
[fs2](https://fs2.io) streams.

| Platform           | Audio backend                              | `AudioFile` |
|--------------------|--------------------------------------------|-------------|
| JVM                | `javax.sound.sampled`                      | yes         |
| Scala Native       | [miniaudio](https://miniaud.io) (vendored) | no          |
| Scala.js (browser) | Web Audio (AudioWorklet)                   | no          |

- **Formats** on Scala Native and Scala.js are 8-bit unsigned or 16, 24 or
  32-bit signed PCM, little-endian. On Scala Native, miniaudio converts the
  sample rate; in a browser the audio context runs at the requested rate, which
  the browser must support.
- **Scala Native** needs a C toolchain (clang) to build, and the platform's
  audio libraries at run time (CoreAudio on macOS, ALSA or PulseAudio on
  Linux).
- **Scala.js** runs in browsers only (there is no audio in Node). The browser
  asks the user for microphone permission and may only start audio from a user
  gesture such as a click. Device names are empty until permission is granted,
  and a browser lists each direction of a device separately.

| sound4s | Scala | Cats Effect | fs2    | log4cats |
|---------|-------|-------------|--------|----------|
| 0.3.0   | 3.9.x | 3.7.x       | 3.14.x | 2.8.x    |
| 0.2.1   | 3.3.x | 3.7.x       | 3.13.x | 2.8.x    |

## Installation

```scala
libraryDependencies += "io.github.max-peroch" %% "sound4s" % "<latest version>"
```

On Scala.js and Scala Native, use `%%%` with sbt 1 (sbt 2's `%%` picks the
platform itself), or `::` with scala-cli.

See the [releases page](https://github.com/max-peroch/sound4s/releases) for
the latest published version, and [`build.sbt`](build.sbt) for the exact
Scala, Cats Effect, and fs2 versions this release was built against.

## Overview

sound4s opens the default (or a named) system audio mixer inside a
`Resource[F, _]` and gives you:

- **`source: Stream[F, Byte]`** — raw PCM from the microphone
- **`sink: Pipe[F, Byte, Unit]`** — raw PCM to the speaker
- **`AudioFile`** — stream PCM from a classpath WAV / AU / AIFF resource,
  transcoded on-the-fly

Frames are emitted and consumed in 320-byte chunks (160 samples × 2 bytes)
by default, matching a 20 ms RTP packet at 8 kHz / 16-bit PCM.

Every entry point takes an explicit
[`org.typelevel.log4cats.Logger[F]`](https://typelevel.org/log4cats/). This
library depends only on `log4cats-core`, so you choose the backend: wire in
`log4cats-slf4j` for real logging, or pass a `log4cats-noop` logger to
discard log output.

## Quick Start

The examples below can be run directly with
[scala-cli](https://scala-cli.virtuslab.org/) — save the directives and code
to a `.scala` file and run `scala-cli run <file>`:

```scala
//> using scala 3.3.8
//> using dep "io.github.max-peroch::sound4s:<latest version>"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.typelevel::log4cats-slf4j:2.8.0"
//> using dep "ch.qos.logback:logback-classic:1.6.2"
```

### Hardware streaming

```scala
import cats.effect.IO
import sound4s.{AudioFormat, SoundStreaming}
import org.typelevel.log4cats.slf4j.Slf4jLogger

val audioFormat = AudioFormat(sampleRate = 8000, bitDepth = 16, channels = 1)

Slf4jLogger.create[IO].flatMap { logger =>
  SoundStreaming.resource[IO](audioFormat, logger).use { audio =>
    audio.source // Stream[IO, Byte] from microphone
      .through(audio.sink) // route straight to speaker (loopback)
      .compile
      .drain
  }
}
```

Hardware is opened when the `Resource` is acquired and closed on release,
even if the stream is interrupted. Pass a specific `AudioDevice` (found via
`AudioDevices.findByName`, or picked from `AudioDevices.list`) to target a
device other than the system default.

Need only one direction? `Microphone.resource` gives a `Microphone[F]` with a
`source`, and `Speakers.resource` a `Speakers[F]` with a `sink`, instead of
`SoundStreaming.resource`.

### Playing an audio file

```scala
import sound4s.{AudioFile, ResourceName}

ResourceName("announcement.wav") match {
  case Right(name) =>
    val file = new AudioFile[IO](name, audioFormat)
    file.source.through(audio.sink).compile.drain
  case Left(error) =>
    IO.raiseError(new RuntimeException(error))
}
```

`AudioFile` reads the resource from the classpath and uses
`AudioSystem.getAudioInputStream` to transcode its native format to
`audioFormat`, so files encoded at any sample rate or bit depth are handled
transparently.

## Telemetry (otel4s)

The `sound4s-otel4s` module, on [otel4s](https://typelevel.org/otel4s/), adds
metrics and traces. It is built for the JVM, Scala.js and Scala Native, and the
core library does not depend on it.

```scala
libraryDependencies += "io.github.max-peroch" %% "sound4s-otel4s" % "<latest version>"
```

Create an `AudioTelemetry` from the otel4s providers in scope, and wrap the
`Resource` that opens the device:

```scala
import sound4s.otel4s.AudioTelemetry

AudioTelemetry.create[IO].flatMap { telemetry =>
  telemetry
    .soundStreaming(SoundStreaming.resource[IO](audioFormat, logger))
    .use(audio => audio.source.through(audio.sink).compile.drain)
}
```

`telemetry.microphone` and `telemetry.speakers` do the same for one direction,
and `AudioTelemetry.noop` reports nothing. Opening a device is a span
(`sound4s.microphone.open`, `sound4s.speakers.open` or `sound4s.streaming.open`)
that carries the audio format and is marked as an error if opening fails.
Metrics, each with a `sound4s.audio.direction` of `input` or `output`:

| Metric                          | Type           | Unit       | What it counts |
|---------------------------------|----------------|------------|----------------|
| `sound4s.audio.streams.active`  | up-down counter | `{stream}` | streams currently open |
| `sound4s.audio.data.size`       | counter        | `By`       | audio read from the microphone or played |
| `sound4s.audio.flushes`         | counter        | `{flush}`  | times buffered audio was discarded: at the start of each reading on input, and on output when a sink was cancelled or failed |
| `sound4s.audio.errors`          | counter        | `{error}`  | failures to open and while streaming, by `error.type` |
| `sound4s.audio.open.duration`   | histogram      | `s`        | how long opening took, by `sound4s.audio.kind` |

## License

Apache License 2.0 — see [LICENSE](LICENSE).
