import sbt.Keys._
import scalariform.formatter.preferences._

lazy val commonSettings = scalariformSettings ++ Seq(
  organization := "com.reactivehub",
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.11.7",
  ScalariformKeys.preferences := FormattingPreferences()
    .setPreference(RewriteArrowSymbols, true)
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)
)

lazy val root = (project in file("."))
  .aggregate(connector)
  .settings(commonSettings)
  .settings(
    name := "akka-stream-apns-root"
  )

lazy val connector = (project in file("connector"))
  .settings(commonSettings)
  .settings(
    name := "akka-stream-apns",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream-experimental" % "1.0",
      "io.spray"          %% "spray-json"               % "1.3.2" % "provided",
      "com.typesafe.play" %% "play-json"                % "2.3.9" % "provided",
      "net.liftweb"       %% "lift-json"                % "2.6.2" % "provided"
    )
  )
