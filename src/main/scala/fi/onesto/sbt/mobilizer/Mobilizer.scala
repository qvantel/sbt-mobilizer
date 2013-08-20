package fi.onesto.sbt.mobilizer

import scala.concurrent.{ExecutionContext, future}
import net.schmizz.sshj.SSHClient
import sbt._
import sbt.classpath.ClasspathUtilities


object Mobilizer extends Plugin {
  import ExecutionContext.Implicits.global
  type Connections = Map[String, SSHClient]

  val deployEnvironments = settingKey[Map[Symbol, DeploymentEnvironment]]("A map of deployment environments")
  val deploy = inputKey[String]("Deploy to given environment")

  val deploySettings = Seq(
    deploy := {
      implicit val log = Keys.streams.value.log
      val args: Seq[String] = Def.spaceDelimited("<arg>").parsed
      val releaseId = generateReleaseId()
      val environmentName = args(0)
      val env = deployEnvironments.value(Symbol(environmentName))
      val rsync = Rsync()

      log.info(s"Deploying to $environmentName")

      withConnections(env) { connections =>
        log.debug(s"connections are open")

        val previousReleaseDirectories = findPreviousReleaseDirectories(env, connections)
        val releaseDirectory = createReleaseDirectory(env, releaseId, connections)
        val pkg = (sbt.Keys.`package` in Compile).value
        val jars = (Keys.fullClasspath in Runtime).value.map(_.data).filter(ClasspathUtilities.isArchive).map(_.getPath)

        val copyPackageTasks = env.hosts map { host =>
          future {
            val target = s"${env.username}@$host:${env.releaseDirectory(releaseId)}/"
            log.info(s"$host: Copying package $pkg to ${env.releaseDirectory(releaseId)}")
            rsync(Seq(pkg.getPath), target)
          }
        }

        val copyJarsTasks = env.hosts map { host =>
          future {
            val target = s"${env.username}@$host:${env.libDirectory(releaseId)}"
            log.info(s"$host: Copying libraries to ${env.libDirectory(releaseId)}")
            rsync.withLinkDest(jars, previousReleaseDirectories(host), target)
          }
        }
      }

      releaseId
    }
  )

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
