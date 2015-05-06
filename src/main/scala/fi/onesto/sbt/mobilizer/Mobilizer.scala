package fi.onesto.sbt.mobilizer

import scala.concurrent.ExecutionContext.Implicits.global
import org.slf4j.impl.StaticLoggerBinder
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import sbt._
import sbt.classpath.ClasspathUtilities
import sbt.complete.{Parsers, FixedSetExamples, Parser}


object Mobilizer extends Plugin {
  type Connections = Map[String, (SSHClient, SFTPClient)]
  type Environments = Map[Symbol, DeploymentEnvironment]

  val deployEnvironments = settingKey[Environments]("A map of deployment environments")
  val deployDependencies = taskKey[Seq[File]]("Dependencies for deployment")
  val deployRevision     = taskKey[Option[String]]("The content of the REVISION file in the release directory")
  val deploy             = inputKey[String]("Deploy to given environment")

  val deploySettings = Seq(
    deployEnvironments := Map.empty,

    deployDependencies := Seq.empty,

    deploy := {
      val (environmentName, environment) = environmentParser(deployEnvironments.value).parsed

      val moduleName   = Keys.name.value
      val dependencies = deployDependencies.value.filter(ClasspathUtilities.isArchive)
      val testResults  = (Keys.test in Test).value
      val mainPackage  = (Keys.`package` in Compile).value
      val mainClass    = (Keys.mainClass in Runtime).value getOrElse sys.error("No main class detected.")
      val libraries    = (Keys.fullClasspath in Runtime).value.map(_.data).filter(ClasspathUtilities.isArchive)
      val revision     = deployRevision.value
      val log          = Keys.streams.value.log

      val releaseId = generateReleaseId()
      StaticLoggerBinder.startSbt(log.asInstanceOf[AbstractLogger])

      log.info(s"Deploying $moduleName to $environmentName")

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

      log.info(s"$moduleName deployed to $environmentName")

      releaseId
    },

    deployRevision := None
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
