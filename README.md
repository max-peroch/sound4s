# sound4s

A thin, resource-safe wrapper around `javax.sound.sampled` for the Typelevel
ecosystem, exposing microphone and speaker hardware as
[fs2](https://fs2.io) streams.

| sound4s | Scala | Cats Effect | fs2    | log4cats |
|---------|-------|-------------|--------|----------|
| 0.2.0   | 3.3.x | 3.7.x       | 3.13.x | 2.8.x    |

## Installation

```scala
libraryDependencies += "io.github.max-peroch" %% "sound4s" % "<latest version>"
```

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
import sound4s.SoundStreaming
import org.typelevel.log4cats.slf4j.Slf4jLogger
import javax.sound.sampled.AudioFormat

val audioFormat = AudioFormat(8000f, 16, 1, true, false)

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
even if the stream is interrupted. Pass a specific `Mixer.Info` (found via
`MixerLookup.findByName`) to target a device other than the system default.

Need only one direction? Use `Microphone.resource` or `Speakers.resource`
directly instead of `SoundStreaming.resource`.

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

## License

Apache License 2.0 — see [LICENSE](LICENSE).
