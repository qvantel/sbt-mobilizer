package fi.onesto.sbt.mobilizer

import org.slf4j.impl.StaticLoggerBinder
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import sbt._
import sbt.classpath.ClasspathUtilities
import sbt.complete.{Parsers, FixedSetExamples, Parser, ExampleSource}


object Mobilizer extends Plugin {
  type Connections = Map[String, (SSHClient, SFTPClient)]
  type Environments = Map[Symbol, DeploymentEnvironment]

  val deployEnvironments = settingKey[Environments]("A map of deployment environments")
  val deployDependencies = taskKey[Seq[File]]("Dependencies for deployment")
  val deploy = inputKey[String]("Deploy to given environment")

  val deploySettings = Seq(
    deployDependencies := Seq.empty,

    deploy := {
      val (environmentName, environment) = environmentParser(deployEnvironments.value).parsed

      val moduleName   = Keys.name.value
      val dependencies = deployDependencies.value.filter(ClasspathUtilities.isArchive)
      val testResults  = (Keys.test in Test).value
      val mainPackage  = (Keys.`package` in Compile).value
      val mainClass    = (Keys.mainClass in Runtime).value getOrElse "Main"
      val libraries    = (Keys.fullClasspath in Runtime).value.map(_.data).filter(ClasspathUtilities.isArchive)
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
        libraries    = libraries)

      log.info(s"$moduleName deployed to $environmentName")

      releaseId
    }
  )

  def environmentParser(available: Map[Symbol, DeploymentEnvironment]): Parser[(String, DeploymentEnvironment)] = {
    import Parsers._

    Space ~> StringBasic
      .examples(new FixedSetExamples(available.keys.map(_.name)))
      .map { name => (name, available(Symbol(name))) }
  }

  private[this] def generateReleaseId() = {
    import java.text.SimpleDateFormat
    import java.util.Date
    val fmt = new SimpleDateFormat("yyyyMMddHHmmss")
    fmt.format(new Date())
  }
}
