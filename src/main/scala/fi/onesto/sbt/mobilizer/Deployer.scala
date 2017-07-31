package fi.onesto.sbt.mobilizer

import scala.util.control.NonFatal
import scala.sys.process.{Process, ProcessLogger}
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration.Inf
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SSHException
import net.schmizz.sshj.sftp.SFTPClient
import util._


final class Deployer(
    moduleName:   String,
    releaseId:    String,
    sbtLogger:    sbt.Logger,
    environment:  DeploymentEnvironment,
    mainPackage:  sbt.File,
    mainClass:    String,
    dependencies: Seq[sbt.File],
    libraries:    Seq[sbt.File],
    revision:     Option[String],
    connections:  Deployer.Connections)
   (implicit ec:  ExecutionContext) {
  import Deployer._

  import environment.{currentDirectory, releasesRoot, restartCommand, checkCommand}

  private[this] val releaseDirectory = environment.releaseDirectory(releaseId)
  private[this] val libDirectory = environment.libDirectory(releaseId)
  private[this] val startupScriptName = moduleName
  private[this] val startupScriptPath = s"${environment.releaseDirectory(releaseId)}/$startupScriptName"
  private[this] val startupScriptContent = {
    val pkgPath = s"${environment.releaseDirectory(releaseId)}/${mainPackage.getName}"
    val libPath = environment.libDirectory(releaseId)
    import environment.{javaBin, javaOpts}

    s"""#!/bin/sh
        |
        |main_package="$pkgPath"
        |libraries="`find $libPath -type f -name '*.jar' | paste -sd:`"
        |
        |CLASSPATH="$$main_package:$$libraries"
        |export CLASSPATH
        |
        |exec $javaBin ${javaOpts.mkString(" ")} $mainClass "$$@"
        |""".stripMargin
  }
  private[this] val startupScriptFile = stringSourceFile(startupScriptName, startupScriptContent)
  private[this] val revisionFilePath = s"${environment.releaseDirectory(releaseId)}/REVISION"
  private[this] val executableMode = Integer.parseInt("0775", 8)

  def run(): Unit = {
    try {
      createReleaseRoot()

      val previousReleaseDirectories = findPreviousReleaseDirectories()

      createReleaseDirectory()

      copyFiles(previousReleaseDirectories)

      createStartupScript()
      createRevisionFile()

      updateSymlink()

      try {
        restart()
      } catch { case NonFatal(e) =>
        restoreSymlink(previousReleaseDirectories)
        try {
          logger.warn("Restarting previous release after a rollback")
          restart()
        } catch { case NonFatal(re) =>
          logger.error(s"Failed to restart after rolling back to previous release: $re")
        }
        throw e
      }
    } catch { case NonFatal(e) =>
      removeThisRelease()
      throw e
    }
  }

  private[this] def restart(): Unit = {
    for {
      runRestart <- restartCommand
      runCheck   <- checkCommand orElse DefaultCheckCommand
    } yield {
      for (hostEntry <- environment.hosts) {
        val (_, ssh, _) = connections(hostEntry)
        logger.info(hostEntry, "Restarting")
        try {
          ssh.runShAndDiscard(runRestart, WithPty)
        } catch { case e: SSHException =>
          logger.error(hostEntry, s"Error restarting: ${e.getMessage}")
          throw e
        }
        logger.info(hostEntry, "Checking for successful startup")
        try {
          ssh.runShAndDiscard(runCheck, WithPty)
        } catch { case e: SSHException =>
          logger.error(hostEntry, s"Startup check failed: ${e.getMessage}")
          throw e
        }
      }
    }
  }

  private[this] def copyFiles(previousReleaseDirectories: Map[String, Option[String]]): Unit = {
    val packageCopyTask = copyPackages(previousReleaseDirectories)
    val libraryCopyTask = copyLibraries(previousReleaseDirectories)
    val copyTask = for {
      _ <- packageCopyTask
      _ <- libraryCopyTask
    } yield ()

    Await.result(copyTask, Inf)
  }

  private[this] def copyPackages(previousReleaseDirectories: Map[String, Option[String]]): Future[Unit] = {
    Future sequence {
      for (hostEntry <- environment.hosts ++ environment.standbyHosts) yield {
        Future {
          val (remote, _, _) = connections(hostEntry)
          val target = s"${remote.username}@${remote.hostname}:$releaseDirectory/"
          logger.info(hostEntry, s"Copying package ${mainPackage.getName} to $releaseDirectory")
          rsync(Seq(mainPackage.getPath), target, remote.port, previousReleaseDirectories(hostEntry))
        }
      }
    } map (_ => ())
  }

  private[this] def copyLibraries(previousReleaseDirectory: Map[String, Option[String]]): Future[Unit] = {
    Future sequence {
      for (hostEntry <- environment.hosts ++ environment.standbyHosts) yield {
        Future {
          val (remote, _, _) = connections(hostEntry)
          val target = s"${remote.username}@${remote.hostname}:$libDirectory"
          val jars = libraries ++ dependencies
          logger.info(hostEntry, s"Copying libraries to $libDirectory")
          rsync(jars.map(_.getPath), target, remote.port, previousReleaseDirectory(hostEntry).map(_ + "/lib/"))
        }
      }
    } map (_ => ())
  }

  private[this] def rsync(sources: Seq[String], target: String, port: Int, linkDest: Option[String]): Unit = {
    val linkDestOpt = linkDest.map("--link-dest=" + _).toList
    val rsyncOpts = s"--port=$port" +: (RsyncBaseOpts ++ environment.rsyncOpts ++ linkDestOpt)
    val command = environment.rsyncCommand +: (rsyncOpts ++ sources) :+ target
    logger.debug(s"Running rsync command: $command")
    val exitCode = Process(command) ! ProcessLogger(sbtLogger.info(_), sbtLogger.error(_))
    if (exitCode != 0) {
      throw new RsyncException(s"rsync command failed with exit status $exitCode")
    }
  }

  private[this] def createReleaseRoot(): Unit = {
    logger.debug(s"creating release root $releasesRoot")
    for ((hostEntry, (_, _, sftp)) <- connections) {
      try {
        sftp.mkdirs(releasesRoot)
      } catch { case e: SSHException =>
        logger.error(hostEntry, s"Could not create release root $releasesRoot: ${e.getMessage}")
        throw e
      }
    }
  }

  private[this] def createReleaseDirectory(): Unit = {
    logger.info(s"Creating release directory $releaseDirectory")
    for ((hostEntry, (_, _, sftp)) <- connections) {
      try {
        sftp.mkdirs(releaseDirectory)
      } catch { case e: SSHException =>
        logger.error(hostEntry, s"Could not create release directory $releaseDirectory: ${e.getMessage}")
        throw e
      }
    }
  }

  private[this] def createStartupScript(): Unit = {
    logger.info(s"Creating startup script $startupScriptPath")
    for ((hostEntry, (_, _, sftp)) <- connections) {
      try {
        sftp.put(startupScriptFile, startupScriptPath)
        sftp.chmod(startupScriptPath, executableMode)
      } catch { case e: SSHException =>
        logger.error(hostEntry, s"Error creating startup script $startupScriptPath: ${e.getMessage}")
        throw e
      }
    }
  }

  private[this] def createRevisionFile(): Unit = {
    revision map { content =>
      logger.info(s"Creating revision file $revisionFilePath")
      for ((hostEntry, (_, _, sftp)) <- connections) yield {
        try {
          sftp.put(stringSourceFile("REVISION", content), revisionFilePath)
        } catch { case e: SSHException =>
          logger.error(hostEntry, s"Error creating REVISION file: ${e.getMessage}")
          throw e
        }
      }
    } getOrElse {
      logger.info(s"Revision information not available, not creating $revisionFilePath")
    }
  }

  private[this] def updateSymlink(): Unit = {
    logger.info(s"Setting “current” symlink to $releaseDirectory")
    for ((hostEntry, (_, ssh, _)) <- connections) {
      try {
        ssh.symlink(releaseDirectory, currentDirectory)
      } catch { case e: SSHException =>
        logger.error(hostEntry, s"Error setting “current” symlink: ${e.getMessage}")
        throw e
      }
    }
  }

  private[this] def findPreviousReleaseDirectories(): Map[String, Option[String]] = {
    val results = for ((hostEntry, (_, _, sftp)) <- connections) yield {
      hostEntry -> (try {
        sftp.ls(releasesRoot).asScala.filter(_.isDirectory).sortBy(_.getName).lastOption.map(_.getPath)
      } catch { case e: SSHException =>
        logger.error(hostEntry, s"Could not find list of previous releases: ${e.getMessage}")
        throw e
      })
    }
    results.toMap
  }

  private[this] def restoreSymlink(previousReleaseDirectories: Map[String, Option[String]]): Unit = {
    logger.info(s"Restoring “current” symlink to previous release")
    previousReleaseDirectories foreach { case (hostname, previousDirectoryOption) =>
      previousDirectoryOption foreach { previousDirectory =>
        val (_, ssh, _) = connections(hostname)
        try {
          ssh.symlink(previousDirectory, currentDirectory)
          logger.info(hostname, s"Restored “current” symlink to $previousDirectory")
        } catch { case e: SSHException =>
          logger.error(hostname, s"Error restoring “current” symlink: ${e.getMessage}")
          throw e
        }
      }
    }
  }

  private[this] def removeThisRelease(): Unit = {
    logger.warn(s"Removing release directory $releaseDirectory")
    for ((hostEntry, (_, ssh, sftp)) <- connections) {
      try {
        ssh.rmTree(releaseDirectory)
      } catch { case e: SSHException =>
        logger.error(hostEntry, s"Error removing release directory $releaseDirectory: ${e.getMessage}")
        throw e
      }
    }
  }

  private[this] object logger {
    final def debug(message: => String): Unit = sbtLogger.debug(s"[$moduleName] $message")
    final def info(message: => String): Unit = sbtLogger.info(s"[$moduleName] $message")
    final def warn(message: => String): Unit = sbtLogger.warn(s"[$moduleName] $message")
    final def error(message: => String): Unit = sbtLogger.error(s"[$moduleName] $message")
    final def debug(hostname: => String, message: => String): Unit = sbtLogger.debug(s"[$moduleName] on $hostname: $message")
    final def info(hostname: => String, message: => String): Unit = sbtLogger.info(s"[$moduleName] on $hostname: $message")
    final def warn(hostname: => String, message: => String): Unit = sbtLogger.warn(s"[$moduleName] on $hostname: $message")
    final def error(hostname: => String, message: => String): Unit = sbtLogger.error(s"[$moduleName] on $hostname: $message")
  }
}

object Deployer {
  type Connections = Map[String, (Remote, SSHClient, SFTPClient)]

  val DefaultCheckCommand = Option("true")

  val RsyncBaseOpts = Seq(
    "--checksum",
    "--times",
    "--compress",
    "--rsh=ssh -o ControlMaster=no")

  def run(moduleName:   String,
          releaseId:    String,
          log:          sbt.Logger,
          environment:  DeploymentEnvironment,
          mainPackage:  sbt.File,
          mainClass:    String,
          dependencies: Seq[sbt.File],
          libraries:    Seq[sbt.File],
          revision:     Option[String])
         (implicit ec:  ExecutionContext): Unit = {
    val connections = openConnections(environment)
    try {
      val deployer = new Deployer(moduleName, releaseId, log, environment, mainPackage, mainClass, dependencies, libraries, revision, connections)
      deployer.run()
    } finally {
      closeConnections(connections)
    }
  }

  private[this] def connect(remote: Remote): SSHClient = {
    import collection.JavaConverters._
    val client = new SSHClient
    try {
      client.loadKnownHosts()
      client.useCompression()
      client.connect(remote.hostname, remote.port)
      client.auth(remote.username, Auth(client).methods.asJava)
      client
    } catch { case NonFatal(e) =>
      client.close()
      throw e
    }
  }

  private[this] def connectViaJump(remote: Remote, jumpThrough: Remote): SSHClient = {
    import collection.JavaConverters._
    val jumpClient = new SSHClient
    try {
      jumpClient.loadKnownHosts()
      jumpClient.useCompression()
      jumpClient.connect(jumpThrough.hostname, jumpThrough.port)
      jumpClient.auth(jumpThrough.username, Auth(jumpClient).methods.asJava)

      val channel = new ProxyChannel(jumpClient.getConnection, remote.hostname, remote.port)
      channel.open()

      val client = new SSHClient
      try {
        client.loadKnownHosts()
        client.useCompression()
        client.connect(remote.hostname, remote.port, channel.getInputStream, channel.getOutputStream)
        client.auth(remote.username, Auth(client).methods.asJava)
        client
      } catch {
        case NonFatal(e) =>
          client.close()
          throw e
      }
    } catch {
      case NonFatal(e) =>
        jumpClient.close()
        throw e
    }
  }

  private[this] def openConnections(environment: DeploymentEnvironment)(implicit ec: ExecutionContext): Connections = {
    def parseRemote(str: String): Remote = {
      def parseHostPort(str: String): (String, Int) = str.split(':') match {
        case Array(h, p) => (h, p.toInt)
        case Array(h) => (h, environment.port)
        case invalid => throw new Exception(s"Invalid host entry $invalid")
      }

      str.split('@') match {
        case Array(u, rest) =>
          val (h, p) = parseHostPort(rest)
          Remote(u, h, p)

        case Array(rest) =>
          val (h, p) = parseHostPort(rest)
          Remote(environment.username, h, p)

        case invalid =>
          throw new Exception(s"Invalid host entry $invalid")
      }
    }

    val eventualConnections = Future sequence {
      environment.hosts ++ environment.standbyHosts map { hostEntry =>
        val remote = parseRemote(hostEntry)
        for {
          client <- Future {
            environment.jumpServer(remote.hostname) match {
              case None => connect(remote)
              case Some(jump) => connectViaJump(remote, parseRemote(jump))
            }

          }
          sftp   <- Future(client.newSFTPClient())
        } yield (hostEntry, (remote, client, sftp))
      }
    }
    Await.result(eventualConnections, Inf).toMap
  }

  private[this] def closeConnections(connections: Connections): Unit = {
    for ((_, (_, ssh, sftp)) <- connections) {
      sftp.close()
      ssh.close()
    }
  }
}
