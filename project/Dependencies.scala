import sbt._

object Dependencies {
  val akkaStream   = "com.typesafe.akka" %% "akka-stream-experimental" % "1.0"
  val sprayJson    = "io.spray"          %% "spray-json"               % "1.3.2"
  val playJson     = "com.typesafe.play" %% "play-json"                % "2.3.9"
  val liftJson     = "net.liftweb"       %% "lift-json"                % "2.6.2"
  val circeCore    = "io.circe"          %% "circe-core"               % "0.1.1"
  val circeGeneric = "io.circe"          %% "circe-generic"            % "0.1.1"
  val circeJawn    = "io.circe"          %% "circe-jawn"               % "0.1.1"
  val scalaTest    = "org.scalatest"     %% "scalatest"                % "2.2.4"

  val connectorDeps = Seq(akkaStream, sprayJson % Provided, playJson % Provided, liftJson % Provided,
    circeCore % Provided, scalaTest % Test, circeGeneric % Test, circeJawn % Test)
  val examplesDeps  = Seq(sprayJson)
}
