package fi.onesto.sbt.mobilizer

import scala.concurrent.{Future, ExecutionContext, Await, future}
import scala.concurrent.duration.Duration.Inf
import net.schmizz.sshj.SSHClient
import sbt._
import sbt.classpath.ClasspathUtilities


object Mobilizer extends Plugin {
  import ExecutionContext.Implicits.global
  type Connections = Map[String, SSHClient]

  val deployEnvironments = settingKey[Map[Symbol, DeploymentEnvironment]]("A map of deployment environments")
  val deployDependencies = taskKey[Keys.Classpath]("Dependencies for deployment")
  val deploy = inputKey[String]("Deploy to given environment")
  val hello = taskKey[Unit]("hello")

  val deploySettings = Seq(
    deployDependencies := Seq.empty,

    deploy := {
      implicit val log = Keys.streams.value.log
      implicit val releaseId = generateReleaseId()
      val args: Seq[String] = Def.spaceDelimited("<arg>").parsed
      val environmentName = args(0)
      val env = deployEnvironments.value(Symbol(environmentName))
      val startupScriptName = Keys.name.value
      val mainClass = (Keys.mainClass in Runtime).value getOrElse "Main"
      val pkg = (sbt.Keys.`package` in Compile).value
      val deps = deployDependencies.value.map(_.data).filter(ClasspathUtilities.isArchive).map(_.getPath)
      val libs = (Keys.fullClasspath in Runtime).value.map(_.data).filter(ClasspathUtilities.isArchive).map(_.getPath)
      val rsync = Rsync()

      log.info(s"Deploying to $environmentName")

      withConnections(env) { implicit connections =>
        log.debug(s"connections are open")
        env.createReleasesDirectories()
        val previousReleaseDirectories = env.findPreviousReleaseDirectories()
        env.createReleaseDirectories()
        try {
          val startupScriptPath = s"${env.releaseDirectory(releaseId)}/$startupScriptName"

          log.info(s"Creating startup script $startupScriptPath")
          env.createFile(startupScriptPath, env.startupScriptContent(pkg.getName, mainClass), "0755")

          val copyPackageTasks = env.hosts map { host =>
            future {
              val target = s"${env.username}@$host:${env.releaseDirectory(releaseId)}/"
              log.info(s"$host: Copying package $pkg to ${env.releaseDirectory(releaseId)}")
              rsync.withLinkDest(Seq(pkg.getPath), previousReleaseDirectories(host), target)
            }
          }

          val copyLibsTasks = env.hosts map { host =>
            future {
              val target = s"${env.username}@$host:${env.libDirectory(releaseId)}"
              val jars = libs ++ deps
              log.info(s"$host: Copying libraries to ${env.libDirectory(releaseId)}")
              rsync.withLinkDest(jars, previousReleaseDirectories(host).map(_ + "/lib/"), target)
            }
          }

          Await.result(Future.sequence(copyPackageTasks ++ copyLibsTasks), Inf)

          env.updateSymlinks()
        } catch {
          case e: Exception =>
            log.error(s"Deployment error: $e")
            env.restoreSymlinks(previousReleaseDirectories)
            env.removeCurrentRelease()
            throw e
        }
      }
      releaseId
    }
  )


/*
  class DeploymentTask(env: DeploymentEnvironment, releaseId: String, connections: Connections)(implicit log: Logger) {

    private def updateSymlinks(env: DeploymentEnvironment, releaseId: String, connections: Connections)(implicit log: Logger) {
      connections foreach { case (hostname: String, client: SSHClient) =>
        val releaseDirectory = env.releaseDirectory(releaseId)
        log.debug(s"$hostname: updating current symlink to $releaseDirectory")
        client.symlink(releaseDirectory, env.currentDirectory)
      }
    }

    private def createReleaseDirectory(env: DeploymentEnvironment, releaseId: String, connections: Connections)(implicit log: Logger): String = {
      connections map { case (hostname: String, client: SSHClient) =>
        val releaseDirectory = env.releaseDirectory(releaseId)
        log.debug(s"$hostname: creating release directory $releaseDirectory")
        client.mkdirWithParents(releaseDirectory)
      }
      env.releaseDirectory(releaseId)
    }

    private def findPreviousReleaseDirectories(env: DeploymentEnvironment, connections: Connections)(implicit log: Logger): Map[String, Option[String]] = {
      connections map { case (hostname: String, client: SSHClient) =>
        log.debug(s"$hostname: creating releases directory ${env.releasesDirectory}")
        client.mkdirWithParents(env.releasesDirectory)
        hostname -> client.run("find", env.releasesDirectory, "-mindepth", "1", "-maxdepth", "1", "-type", "d").toSeq.sorted.lastOption
      }
    }
  }

  private def updateSymlinks(env: DeploymentEnvironment, releaseId: String, connections: Connections)(implicit log: Logger) {
    connections foreach { case (hostname: String, client: SSHClient) =>
      val releaseDirectory = env.releaseDirectory(releaseId)
      log.debug(s"$hostname: updating current symlink to $releaseDirectory")
      client.symlink(releaseDirectory, env.currentDirectory)
    }
  }

  private def createReleaseDirectory(env: DeploymentEnvironment, releaseId: String, connections: Connections)(implicit log: Logger): String = {
    connections map { case (hostname: String, client: SSHClient) =>
      val releaseDirectory = env.releaseDirectory(releaseId)
      log.debug(s"$hostname: creating release directory $releaseDirectory")
      client.mkdirWithParents(releaseDirectory)
    }
    env.releaseDirectory(releaseId)
  }

  private def findPreviousReleaseDirectories(env: DeploymentEnvironment, connections: Connections)(implicit log: Logger): Map[String, Option[String]] = {
    connections map { case (hostname: String, client: SSHClient) =>
      log.debug(s"$hostname: creating releases directory ${env.releasesDirectory}")
      client.mkdirWithParents(env.releasesDirectory)
      hostname -> client.run("find", env.releasesDirectory, "-mindepth", "1", "-maxdepth", "1", "-type", "d").toSeq.sorted.lastOption
    }
  }
*/

  private def withConnections[A](environment: DeploymentEnvironment)(action: Connections => A)(implicit log: Logger): A = {
    val connections: Map[String, SSHClient] = openConnections(environment)
    try {
      action(connections)
    } finally {
      closeConnections(connections)
    }
  }

  private def openConnections(environment: DeploymentEnvironment)(implicit log: Logger): Connections = {
    val connections = environment.hosts map { hostname: String =>
      hostname -> ssh.connect(hostname, environment.username, environment.port)
    }
    connections.toMap
  }

  private def closeConnections(connections: Connections) {
    connections.values.foreach(_.close())
  }

  private def generateReleaseId() = {
    import java.text.SimpleDateFormat
    import java.util.Date
    val fmt = new SimpleDateFormat("yyyyMMddHHmmss")
    fmt.format(new Date())
  }
}
