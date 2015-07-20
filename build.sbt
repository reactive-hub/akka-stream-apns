organization := "com.reactivehub"

name := "akka-stream-apns"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream-experimental" % "1.0",
  "io.spray"          %% "spray-json"               % "1.3.2" % "provided",
  "com.typesafe.play" %% "play-json"                % "2.3.9" % "provided",
  "net.liftweb"       %% "lift-json"                % "2.6.2" % "provided")
