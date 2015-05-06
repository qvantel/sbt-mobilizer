package fi.onesto.sbt.mobilizer

import scala.concurrent.ExecutionContext.Implicits.global
import org.slf4j.impl.StaticLoggerBinder
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import sbt._
import sbt.classpath.ClasspathUtilities
import sbt.complete.{Parsers, FixedSetExamples, Parser}


object Mobilizer extends AutoPlugin {
  import autoImport._

  type Connections = Map[String, (SSHClient, SFTPClient)]
  type Environments = Map[Symbol, DeploymentEnvironment]

  object autoImport {
    lazy val deployEnvironments = settingKey[Environments]("A map of deployment environments")
    lazy val deployDependencies = taskKey[Seq[File]]("Dependencies for deployment")
    lazy val deployRevision     = taskKey[Option[String]]("The content of the REVISION file in the release directory")
    lazy val deploy             = inputKey[String]("Deploy to given environment")
  }

  override val trigger = allRequirements

  override lazy val projectSettings = Seq(
    deployEnvironments := Map.empty,

    deployDependencies := Seq.empty,

    deployRevision := None,

    deploy := {
      val log = Keys.streams.value.log
      StaticLoggerBinder.startSbt(log.asInstanceOf[AbstractLogger])

      val (environmentName, environment) = environmentParser(deployEnvironments.value).parsed

      val moduleName   = Keys.name.value
      val dependencies = deployDependencies.value.filter(ClasspathUtilities.isArchive)
      val testResults  = (Keys.test in Test).value
      val mainPackage  = (Keys.`package` in Compile).value
      val mainClass    = (Keys.mainClass in Runtime).value getOrElse sys.error("No main class detected.")
      val libraries    = (Keys.fullClasspath in Runtime).value.map(_.data).filter(ClasspathUtilities.isArchive)
      val revision     = deployRevision.value
      val releaseId    = generateReleaseId()

      log.info(s"Deploying $moduleName to $environmentName (${environment.hosts.mkString(", ")})")

      Deployer.run(
        moduleName   = moduleName,
        releaseId    = releaseId,
        log          = log,
        environment  = environment,
        mainPackage  = mainPackage,
        mainClass    = mainClass,
        dependencies = dependencies,
        libraries    = libraries,
        revision     = revision)

      log.info(s"$moduleName deployed to $environmentName environment")

      releaseId
    }
  )

  private[this] def environmentParser(available: Map[Symbol, DeploymentEnvironment]): Parser[(String, DeploymentEnvironment)] = {
    import Parsers._

    Space ~> StringBasic
      .examples(new FixedSetExamples(available.keys.map(_.name)))
      .map { name => (name, available(Symbol(name))) }
  }

  private[this] def generateReleaseId(): String = {
    import java.text.SimpleDateFormat
    import java.util.Date
    val fmt = new SimpleDateFormat("yyyyMMddHHmmss")
    fmt.format(new Date())
  }
}
