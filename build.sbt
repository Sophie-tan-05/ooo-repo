ThisBuild / scalaVersion := "3.3.3"
ThisBuild / version := "1.0.0"

lazy val root = (project in file("."))
  .settings(
    name := "sephora-collections-analytics",
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Wunused:all"
    ),
    libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.10"
  )
