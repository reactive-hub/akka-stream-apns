import sbt._

object Dependencies {
  val akkaStream = "com.typesafe.akka" %% "akka-stream-experimental" % "1.0"
  val sprayJson  = "io.spray"          %% "spray-json"               % "1.3.2"
  val playJson   = "com.typesafe.play" %% "play-json"                % "2.3.9"
  val liftJson   = "net.liftweb"       %% "lift-json"                % "2.6.2"

  val connectorDeps = Seq(akkaStream, sprayJson % Provided, playJson % Provided, liftJson % Provided)
  val examplesDeps  = Seq(sprayJson)
}
