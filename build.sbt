ThisBuild / scalaVersion := "3.9.0"
ThisBuild / organization := "io.github.max-peroch"
ThisBuild / homepage     := Some(uri("https://github.com/max-peroch/sound4s"))
ThisBuild / licenses     := List(License.Apache2)
ThisBuild / developers   := List(
  Developer(
    "max-peroch",
    "Maxime Perocheau",
    "max.peroch@hotmail.fr",
    uri("https://perocheau.com")
  )
)
ThisBuild / scalafmtOnCompile := true
ThisBuild / scalacOptions     := Seq(
  "-Wunused:all",
  "-Werror"
)

// Linking a Scala Native binary needs a lot of memory; do one at a time.
Global / concurrentRestrictions += Tags.limit(NativeTags.Link, 1)

lazy val root = rootProject
  .aggregate(
    core.jvm,
    core.js,
    core.native,
    otel4s.jvm,
    otel4s.js,
    otel4s.native
  )
  .settings(publish / skip := true)

lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("core"))
  .settings(
    name := "sound4s",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect"       % "3.7.1",
      "co.fs2"        %% "fs2-io"            % "3.16.0",
      "co.fs2"        %% "fs2-core"          % "3.16.0",
      "org.typelevel" %% "log4cats-core"     % "2.8.0",
      "org.scalameta" %% "munit"             % "1.3.6" % Test,
      "org.typelevel" %% "munit-cats-effect" % "2.2.1" % Test,
      "org.typelevel" %% "log4cats-noop"     % "2.8.0" % Test
    )
  )

lazy val otel4s = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("otel4s"))
  .dependsOn(core)
  .settings(
    name := "sound4s-otel4s",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "otel4s-core-metrics" % "1.2.0",
      "org.typelevel" %% "otel4s-core-trace"   % "1.2.0",
      "org.scalameta" %% "munit"               % "1.3.6"  % Test,
      "org.typelevel" %% "munit-cats-effect"   % "2.2.1"  % Test,
      "org.typelevel" %% "otel4s-sdk-testkit"  % "0.19.5" % Test
    )
  )
