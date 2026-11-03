ThisBuild / scalaVersion := "3.9.0"

val tapirVersion = "1.14.1"
val PekkoVersion = "1.7.1"
val PekkoHttpVersion = "1.4.2"

lazy val protocol  = project
lazy val simulator = project.dependsOn(protocol).settings(fork := true)
lazy val app       = project.dependsOn(protocol).settings(
  libraryDependencies ++= Seq(
    "com.softwaremill.sttp.tapir" %% "tapir-pekko-http-server" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-circe"        % tapirVersion,
    "org.apache.pekko"            %% "pekko-actor-typed"       % PekkoVersion,
    "org.apache.pekko"            %% "pekko-stream"            % PekkoVersion,
    "org.apache.pekko"            %% "pekko-http"              % PekkoHttpVersion,
  ),
  Compile / unmanagedJars += (LocalRootProject / baseDirectory).value / "lib" / "phidget22.jar"
)