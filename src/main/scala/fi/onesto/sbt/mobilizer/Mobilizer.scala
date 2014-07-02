package fi.onesto.sbt.mobilizer

import scala.concurrent.{Future, ExecutionContext, Await, future}
import scala.concurrent.duration.Duration.Inf
import org.slf4j.impl.StaticLoggerBinder
import net.schmizz.sshj.SSHClient
import sbt._
import sbt.classpath.ClasspathUtilities


object Mobilizer extends Plugin {
  import ExecutionContext.Implicits.global
  type Connections = Map[String, SSHClient]

  val deployEnvironments = settingKey[Map[Symbol, DeploymentEnvironment]]("A map of deployment environments")
  val deployDependencies = taskKey[Seq[File]]("Dependencies for deployment")
  val deploy = inputKey[String]("Deploy to given environment")

  val deploySettings = Seq(
    deployDependencies := Seq.empty,

    deploy := {
      implicit val log = Keys.streams.value.log
      StaticLoggerBinder.startSbt(log.asInstanceOf[AbstractLogger])

      implicit val releaseId = generateReleaseId()
      val args: Seq[String] = Def.spaceDelimited("<arg>").parsed
      val environmentName = args(0)
      val env = deployEnvironments.value(Symbol(environmentName))
      val startupScriptName = Keys.name.value
      val mainClass = (Keys.mainClass in Runtime).value getOrElse "Main"
      val pkg = (sbt.Keys.`package` in Compile).value
      val deps = deployDependencies.value.filter(ClasspathUtilities.isArchive).map(_.getPath)
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
