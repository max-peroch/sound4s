# Plan: cross-publishing sound4s to Scala.js and Scala Native

Status: draft, nothing implemented.

## Problem

All audio code is built on `javax.sound.sampled` (JVM-only), and those types
are the public API (`AudioFormat`, `Mixer.Info`, `TargetDataLine`,
`SourceDataLine`). Cross-publishing therefore needs an API redesign, not only
a build change.

| File                | JVM-only? | Notes                                          |
|---------------------|-----------|------------------------------------------------|
| `AudioError`        | no        | shareable as is                                |
| `ResourceName`      | no        | shareable (check for `java.io`/classloader use)|
| `SoundStreaming`    | partly    | pure chunking/timing is shareable; `javax.sound` types are not |
| `AudioFile`         | yes       | `AudioInputStream`, `AudioSystem`, `java.io`   |
| `Microphone`        | yes       | `TargetDataLine`, `Mixer`                      |
| `Speakers`          | yes       | `SourceDataLine`, `Mixer`                      |
| `MixerLookup`       | yes       | `AudioSystem.getMixerInfo`                     |

## Decisions needed before starting

1. Target platforms: JS in browser, JS in Node, Native on desktop. Which ones?
2. Breaking change: new major version of the existing artifact, or a new
   `core` artifact alongside the current one?
3. Native audio backend: miniaudio (single header, simplest), PortAudio, or
   per-OS APIs (ALSA/CoreAudio/WASAPI).

## Phase 0: tooling (decided)

Decision: use `sbt-crossproject` on sbt 2.1.0, without `sbt-typelevel`.
(Typelevel projects like fs2 use `sbt-crossproject` + `sbt-typelevel` on sbt 1;
`sbt-typelevel` has no sbt 2 release yet, see typelevel/sbt-typelevel#899.)

Verified on Maven Central (2026-11-17):
- sbt 2 plugin releases exist: `sbt-scalajs-crossproject` 1.4.0,
  `sbt-scala-native-crossproject` 1.4.0, `sbt-scalajs` 1.23.0,
  `sbt-scala-native` 0.5.12, `sbt-ci-release` 1.12.1, `sbt-scalafmt` 2.6.2.
- The two crossproject plugins depend on `sbt-crossproject` and on
  `sbt-scalajs` / `sbt-scala-native`, but pin older versions of the latter
  (1.22.0 and 0.5.11). Policy: always use the latest of everything, so declare
  the platform plugins explicitly to override the pins. `project/plugins.sbt`:
  ```scala
  addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.4.0")
  addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.4.0")
  addSbtPlugin("org.scala-js"       % "sbt-scalajs"                   % "1.23.0")
  addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.12")
  ```
  Add only the platforms actually targeted. Confirm with `sbt plugins` or
  `sbt update` that the explicit versions are the ones that resolve.
- JS and Native artifacts exist at current versions for cats-effect, fs2-core,
  fs2-io, log4cats-core/noop, munit, munit-cats-effect.

Still to check:
- Re-check latest versions of all plugins and dependencies when starting
  Phase 1 (these numbers are from 2026-11-17).
- That Scala 3.9.0 works with the latest Scala.js and Scala Native releases.
- The plugins load and work together on this build (try in Phase 1).
- What is used from `fs2-io` and whether it behaves on JS/Native.

What is lost without `sbt-typelevel`: generated CI workflows (maintain
`.github/workflows` by hand), `tl*` settings, and probably fs2's combined source
dirs (`jvm-native`, `js-jvm`, `js-native`), which would need manual
`unmanagedSourceDirectories` or be skipped.

## Phase 1: build restructure, JVM only

- Replace `rootProject` with `crossProject(JVMPlatform)` for `core` (shared)
  plus a JVM backend, JVM only for now; add the JS/Native platforms in later
  phases. Use `%%` for dependencies: in sbt 2, `%%` is platform-aware and
  `%%%` no longer exists (`%%%` fails to compile). While JVM-only, add
  `addSbtPlugin("org.portable-scala" % "sbt-crossproject" % "1.4.0")` directly.
- Use the fs2 source layout: `shared`, `jvm`, `js`, `native`. Add combined
  dirs (`jvm-native`, `js-jvm`, `js-native`) via `unmanagedSourceDirectories`
  only if code is genuinely shared by two platforms.
- Keep the Scala version, scalac options, scalafmt, and the ci-release setup.
- Move tests with the code they cover.
- Exit criterion: `sbt test` is green and the JVM artifact is published
  unchanged in behavior.

### Phase 1 status: done on branch `cross-publish-phase-1` (uncommitted)

- `build.sbt`: explicit `root` (aggregates `core.jvm`, `publish / skip`) and
  `core = crossProject(JVMPlatform).crossType(CrossType.Full).in(file("core"))`
  with `name := "sound4s"`, so the published artifact id is unchanged.
- Layout: `core/shared` holds `AudioError` and its suite (the only code that
  is platform-neutral today; `ResourceName` uses the JVM classloader). Everything
  else is in `core/jvm`, including the test resource.
- Verified: `sbt test` (all 6 suites), `scalafmtSbtCheck;scalafmtCheckAll`,
  `doc`, artifact id `io.github.max-peroch:sound4s`.
- Dependency versions deliberately unchanged in this phase. Newer releases
  exist (fs2 3.15.0, Scala 3.10.1 as of 2026-11-17); bump separately.
- Not yet done: CI is untouched (`sbt testFull`/`doc`/`ci-release` still apply
  to the root); `ci-release` publishing of `coreJVM` not exercised.

## Phase 2: platform-neutral core API

- Introduce own types in `core`: `AudioFormat` (sample rate, bit depth,
  channels, signed, endianness), a device descriptor replacing `Mixer.Info`,
  and capture/playback handles (stream-based, e.g. `Stream[F, Byte]` in and
  `Pipe[F, Byte, Nothing]` out).
- Move `AudioError`, `ResourceName`, and the pure parts of `SoundStreaming`
  into `core`.
- Define the shared API in `core/shared` and follow the fs2 pattern of
  `FooCompanionPlatform` traits mixed into each companion (`Microphone`,
  `Speakers`, `MixerLookup`), implemented per platform. Verify the exact
  pattern in fs2's `io/` sources before copying it.
- Rewrite the JVM backend to implement them by wrapping `javax.sound` and
  converting types. Port the existing JVM tests.
- Exit criterion: JVM API works through the neutral core; a shared test suite
  runs against any backend.

## Phase 3: first additional platform

Pick one, not both.

- **Native**: `@extern` bindings to miniaudio (vendored C, compiled via
  `nativeCompileOptions`); implement capture/playback/device enumeration;
  CI on macOS and Linux (clang installed), written by hand. Following fs2:
  limit native linking to one job at a time
  (`Global / concurrentRestrictions += Tags.limit(NativeTags.Link, 1)`),
  run a separate macOS job for native-specific tests, and set
  `Test / nativeConfig ~= (_.withEmbedResources(true))` for test resources.
- **JS (browser)**: Web Audio API and `getUserMedia`; handle permission and
  user-gesture requirements, async-only access, sample-rate negotiation, and
  device lookup via `enumerateDevices` (IDs/labels, no mixer names).
  Node is out of scope unless there is a concrete need. If Node is later
  supported, follow fs2: `jsEnv := new NodeJSEnv()` and CommonJS module kind
  for tests.

## Phase 4: second platform and release

- Repeat phase 3 for the other platform if still wanted.
- Add platforms to the CI matrix and verify `sbt-ci-release` publishes all
  cells (`_sjs1_3`, `_native0.5_3`, JVM).
- Update README (platform support matrix, migration notes) and CHANGELOG.

## Risks

- Plugin interactions on sbt 2 are untested until Phase 1 (versions exist, but
  not yet tried together on this build).
- Hand-maintained CI and source-dir setup, since `sbt-typelevel` is not used.
- Neutral API that fits JVM but not Web Audio (push vs pull, async-only).
  Mitigation: validate the API against a second backend early, before
  freezing it.
- Native: C dependency in the release process; per-OS builds and testing.
- Breaking change for existing users (mitigated by versioning decision 2).
