name := """sources"""

organization := "no.met.data"

version := "0.2-SNAPSHOT"

publishTo := {
  val nexus = "http://maven.met.no/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.6"

PlayKeys.devSettings += ("play.http.router", "sources.Routes")

// Test Settings
javaOptions += "-Djunit.outdir=target/test-report"

ScoverageSbtPlugin.ScoverageKeys.coverageHighlighting := true

ScoverageSbtPlugin.ScoverageKeys.coverageMinimum := 95

ScoverageSbtPlugin.ScoverageKeys.coverageFailOnMinimum := true

ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := """
  <empty>;
  value.ApiResponse;
  ReverseApplication;
  ReverseAssets;
  sources.*;
"""



// Dependencies
libraryDependencies ++= Seq(
  jdbc,
  cache,
  evolutions,
  ws,
 "com.typesafe.play" %% "anorm" % "2.4.0",
 "pl.matisoft" %% "swagger-play24" % "1.4",
  "org.postgresql" % "postgresql" % "9.4-1201-jdbc41",
 "com.github.nscala-time" %% "nscala-time" % "2.0.0",
 "no.met.data" %% "util" % "0.2-SNAPSHOT",
 "no.met.data" %% "auth" % "0.2-SNAPSHOT",
  specs2 % Test
)

resolvers ++= Seq("metno repo" at "http://maven.met.no/content/groups/public",
                  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
                  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
                  "amateras-repo" at "http://amateras.sourceforge.jp/mvn/"
                 )

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator
