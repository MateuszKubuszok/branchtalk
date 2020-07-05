import sbt._

import Dependencies._

object Dependencies {

  // scala version
  val scalaOrganization  = "org.scala-lang"
  val scalaVersion       = "2.13.3"
  val crossScalaVersions = Seq("2.13.3")

  // libraries versions
  val catsVersion       = "2.1.1"
  val catsEffectVersion = "2.1.3"
  val declineVersion    = "1.2.0"
  val doobieVersion     = "0.8.8"
  val drosteVersion     = "0.8.0"
  val enumeratumVersion = "1.6.1"
  val fs2Version        = "2.4.2"
  val libGDXVersion     = "1.9.10"
  val monixVersion      = "3.2.2"
  val monocleVersion    = "2.0.5"
  val refinedVersion    = "0.9.14"
  val specs2Version     = "4.10.0"

  // resolvers
  val resolvers = Seq(
    Resolver sonatypeRepo "public",
    Resolver typesafeRepo "releases"
  )

  // functional libraries
  val cats              = "org.typelevel" %% "cats-core" % catsVersion
  val catsEffect        = "org.typelevel" %% "cats-effect" % catsEffectVersion
  val catsLaws          = "org.typelevel" %% "cats-laws" % catsVersion
  val droste            = "io.higherkindness" %% "droste-core" % drosteVersion
  val enumeratum        = "com.beachape" %% "enumeratum" % enumeratumVersion
  val fs2               = "co.fs2" %% "fs2-core" % fs2Version
  val fs2IO             = "co.fs2" %% "fs2-io" % fs2Version
  val magnolia          = "com.propensive" %% "magnolia" % "0.16.0"
  val monocle           = "com.github.julien-truffaut" %% "monocle-core" % monocleVersion
  val monocleMacro      = "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion
  val newtype           = "io.estatico" %% "newtype" % "0.4.4"
  val refined           = "eu.timepit" %% "refined" % refinedVersion
  val refinedCats       = "eu.timepit" %% "refined-cats" % refinedVersion
  val refinedDecline    = "com.monovore" %% "decline-refined" % declineVersion
  val refinedPureConfig = "eu.timepit" %% "refined-pureconfig" % refinedVersion
  // async
  val monixExecution = "io.monix" %% "monix-execution" % monixVersion
  val monixEval      = "io.monix" %% "monix-eval" % monixVersion
  val monixBio       = "io.monix" %% "monix-bio" % "0.1.0"
  // infrastructure
  val doobie         = "org.tpolecat" %% "doobie-core" % doobieVersion
  val doobieHikari   = "org.tpolecat" %% "doobie-hikari" % doobieVersion
  val doobiePostgres = "org.tpolecat" %% "doobie-postgres" % doobieVersion
  val doobieSpecs2   = "org.tpolecat" %% "doobie-specs2" % doobieVersion
  val flyway         = "org.flywaydb" % "flyway-core" % "6.5.0"
  val fs2Kafka       = "com.github.fd4s" %% "fs2-kafka" % "1.0.0"
  // config
  val decline     = "com.monovore" %% "decline" % declineVersion
  val scalaConfig = "com.typesafe" % "config" % "1.4.0"
  val pureConfig  = "com.github.pureconfig" %% "pureconfig" % "0.13.0"
  // logging
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
  val logback      = "ch.qos.logback" % "logback-classic" % "1.2.3"
  // testing
  val spec2Core       = "org.specs2" %% "specs2-core" % specs2Version
  val spec2Scalacheck = "org.specs2" %% "specs2-scalacheck" % specs2Version
}

trait Dependencies {

  val scalaOrganizationUsed  = scalaOrganization
  val scalaVersionUsed       = scalaVersion
  val crossScalaVersionsUsed = crossScalaVersions

  // resolvers
  val commonResolvers = resolvers

  val mainDeps = Seq(
    cats,
    catsEffect,
    enumeratum,
    magnolia,
    monocle,
    monocleMacro,
    newtype,
    refined,
    refinedCats,
    refinedDecline,
    refinedPureConfig,
    scalaLogging,
    logback
  )

  val testDeps = Seq(catsLaws, spec2Core, spec2Scalacheck)

  implicit final class ProjectRoot(project: Project) {

    def root: Project = project in file(".")
  }

  implicit final class ProjectFrom(project: Project) {

    private val commonDir = "modules"

    def from(dir: String): Project = project in file(s"$commonDir/$dir")
  }

  implicit final class DependsOnProject(project: Project) {

    private val testConfigurations = Set("test", "fun", "it")
    private def findCompileAndTestConfigs(p: Project) =
      (p.configurations.map(_.name).toSet intersect testConfigurations) + "compile"

    private val thisProjectsConfigs = findCompileAndTestConfigs(project)
    private def generateDepsForProject(p: Project) =
      p % (thisProjectsConfigs intersect findCompileAndTestConfigs(p) map (c => s"$c->$c") mkString ";")

    def compileAndTestDependsOn(projects: Project*): Project =
      project dependsOn (projects.map(generateDepsForProject): _*)
  }
}
