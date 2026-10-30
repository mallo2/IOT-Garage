ThisBuild / scalaVersion := "3.3.4"

lazy val protocol  = project
lazy val simulator = project.dependsOn(protocol).settings(fork := true)
lazy val app       = project.dependsOn(protocol).settings(
  Compile / unmanagedJars += (LocalRootProject / baseDirectory).value / "lib" / "phidget22.jar"
)