import sbt._

object Dependencies {
  val akkaStream        = "com.typesafe.akka" %% "akka-stream"         % "2.5.0"
  val netty             = "io.netty"          %  "netty-codec-http2"   % "4.1.7.Final"

  val sprayJson         = "io.spray"          %% "spray-json"          % "1.3.3"
  val playJson          = "com.typesafe.play" %% "play-json"           % "2.5.10"
  val liftJson          = "net.liftweb"       %% "lift-json"           % "3.0.1"
  val circeParser       = "io.circe"          %% "circe-parser"        % "0.7.1"
  val circeGeneric      = "io.circe"          %% "circe-generic"       % "0.7.1"

  val scalaTest         = "org.scalatest"     %% "scalatest"           % "3.0.1"
  val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.0"

  def connectorDeps(scalaVersion: String): Seq[ModuleID] =
    Seq(akkaStream, netty) ++
    Seq(sprayJson, liftJson, circeParser).map(_ % Provided) ++
    (if (scalaVersion.startsWith("2.11.")) Seq(playJson % Provided) else Nil) ++
    Seq(scalaTest, akkaStreamTestkit, circeGeneric).map(_ % Test)

  val examplesDeps = Seq(sprayJson)
}
