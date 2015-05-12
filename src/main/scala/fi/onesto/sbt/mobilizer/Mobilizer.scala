package fi.onesto.sbt.mobilizer

import scala.concurrent.ExecutionContext.Implicits.global
import org.slf4j.impl.StaticLoggerBinder
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import sbt.{Keys => SK, _}
import sbt.classpath.ClasspathUtilities
import sbt.complete.{Parsers, FixedSetExamples, Parser}


object Mobilizer extends AutoPlugin {
  import Keys._

  type Connections = Map[String, (SSHClient, SFTPClient)]
  type Environments = Map[Symbol, DeploymentEnvironment]

  object Keys {
    lazy val deployEnvironments = settingKey[Environments]("A map of deployment environments")
    lazy val deployRevision     = taskKey[Option[String]]("The content of the REVISION file in the release directory")
    lazy val deploy             = inputKey[String]("Deploy to given environment")
  }

  val autoImport = Keys

  override val trigger = noTrigger

  override lazy val projectSettings = Seq(
    deployRevision := None,

    deploy := {
      val log = SK.streams.value.log
      StaticLoggerBinder.startSbt(log.asInstanceOf[AbstractLogger])

      val (environmentName, environment) = environmentParser(deployEnvironments.value).parsed

      val currentState    = SK.state.value
      val moduleName      = SK.name.value
      val dependencyTasks = SK.thisProject.value.dependencies.map(SK.`package` in Compile in _.project)
      val dependencies    = dependencyTasks map { dependencyTask =>
        Project.runTask(dependencyTask, currentState) match {
          case Some((_, Value(f))) => f
          case _                   => sys.error(s"Failed to package dependency $dependencyTask")
        }
      }
      val testResults     = (SK.test in Test).value
      val mainPackage     = (SK.`package` in Compile).value
      val mainClass       = (SK.mainClass in Runtime).value getOrElse sys.error("No main class detected.")
      val libraries       = (SK.fullClasspath in Runtime).value.map(_.data).filter(ClasspathUtilities.isArchive)
      val revision        = deployRevision.value
      val releaseId       = generateReleaseId()

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
