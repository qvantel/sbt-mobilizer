package fi.onesto.sbt.mobilizer

import scala.util.control.NonFatal
import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration.Inf
import scala.concurrent.ExecutionContext.Implicits.global
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import sbt._

import util._


class Deployer(
    moduleName:   String,
    releaseId:    String,
    log:          sbt.Logger,
    environment:  DeploymentEnvironment,
    mainPackage:  sbt.File,
    mainClass:    String,
    dependencies: Seq[sbt.File],
    libraries:    Seq[sbt.File],
    connections:  Deployer.Connections) {
  import Deployer._

  import environment.{username, currentDirectory, releasesRoot, restartCommand, checkCommand}

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

  def run() {
    val previousReleaseDirectories = findPreviousReleaseDirectories()
    try {
      createReleaseDirectory()

      val packageCopyTasks = copyPackages(previousReleaseDirectories)
      val libraryCopyTasks = copyLibraries(previousReleaseDirectories)
      val copyTask = Future.sequence(packageCopyTasks ++ libraryCopyTasks)

      createStartupScript()

      Await.result(copyTask, Inf)

      updateSymlink()

      try {
        restart()
      } catch { case NonFatal(e) =>
        restoreSymlink(previousReleaseDirectories)
        try {
          log.warn("Restarting previous release after a rollback")
          restart()
        } catch { case NonFatal(re) =>
          log.error(s"Failed to restart after rolling back to previous release: $re")
        }
        throw e
      }
    } catch { case NonFatal(e) =>
      removeThisRelease()
      throw e
    }
  }

  private[this] def restart() {
    for (runRestart <- restartCommand;
         runCheck   <- checkCommand orElse DefaultCheckCommand) {
      for (hostname <- environment.hosts) {
        val (ssh, _) = connections(hostname)
        log.info(s"Restarting on $hostname")
        ssh.runShAndDiscard(runRestart, WithPty)
        ssh.runShAndDiscard(runCheck, WithPty)
      }
    }
  }

  private[this] def copyPackages(previousReleaseDirectories: Map[String, Option[String]]): Seq[Future[Unit]] = {
    for (hostname <- environment.hosts) yield {
      Future {
        val target = s"$username@$hostname:$releaseDirectory/"
        log.info(s"[$moduleName] $hostname: Copying package ${mainPackage.getName} to $releaseDirectory")
        rsync(Seq(mainPackage.getPath), target, previousReleaseDirectories(hostname))
      }
    }
  }

  private[this] def copyLibraries(previousReleaseDirectory: Map[String, Option[String]]): Seq[Future[Unit]] = {
    for (hostname <- environment.hosts) yield {
      Future {
        val target = s"$username@$hostname:$libDirectory"
        val jars = libraries ++ dependencies
        log.info(s"[$moduleName] $hostname: Copying libraries to $libDirectory")
        rsync(jars.map(_.getPath), target, previousReleaseDirectory(hostname).map(_ + "/lib/"))
      }
    }
  }

  private[this] def rsync(sources: Seq[String], target: String, linkDest: Option[String] = None) {
    val linkDestOpt = linkDest.map("--link-dest=" + _).toList
    val rsyncOpts = RsyncBaseOpts ++ environment.rsyncOpts ++ linkDestOpt
    val command = environment.rsyncCommand +: (rsyncOpts ++ sources) :+ target
    log.debug(s"[$moduleName] Running rsync command: $command")
    Process(command) ! log
  }

  private[this] def createReleaseDirectory() {
    log.info(s"[$moduleName] Creating release directory $releaseDirectory")
    for ((hostname, (ssh, sftp)) <- connections) {
      sftp.mkdirs(releaseDirectory)
    }
  }

  private[this] def createStartupScript() {
    log.info(s"[$moduleName] Creating startup script $startupScriptPath")
    for ((hostname, (ssh, sftp)) <- connections) {
      sftp.put(startupScriptFile, startupScriptPath)
      sftp.chmod(startupScriptPath, 0775)
    }
  }

  private[this] def updateSymlink() {
    log.info(s"[$moduleName] Setting “current” symlink to $releaseDirectory")
    for ((_, (ssh, _)) <- connections) {
      ssh.symlink(releaseDirectory, currentDirectory)
    }
  }

  private[this] def findPreviousReleaseDirectories(): Map[String, Option[String]] = {
    val results = for ((hostname, (ssh, sftp)) <- connections) yield {
      hostname -> sftp.ls(releasesRoot).asScala.filter(_.isDirectory).sortBy(_.getName).lastOption.map(_.getPath)
    }
    results.toMap
  }

  private[this] def restoreSymlinkOn(hostname: String, previousReleaseDirectoryOption: Option[String]) {
    log.info(s"[$moduleName] Restoring “current” symlink to previous release")
    previousReleaseDirectoryOption map { previousReleaseDirectory =>
      val (ssh, _) = connections(hostname)
      ssh.symlink(previousReleaseDirectory, currentDirectory)
      log.info(s"Restored “current” symlink on $hostname to $previousReleaseDirectory")
    }
  }

  private[this] def restoreSymlink(previousReleaseDirectories: Map[String, Option[String]]) {
    log.info(s"[$moduleName] Restoring “current” symlink to previous release")
    previousReleaseDirectories map { case (hostname, previousDirectoryOption) =>
      previousDirectoryOption map { previousDirectory =>
        val (ssh, _) = connections(hostname)
        ssh.symlink(previousDirectory, currentDirectory)
        log.info(s"Restored “current” symlink on $hostname to $previousDirectory")
      }
    }
  }

  private[this] def removeThisRelease() {
    log.warn(s"[$moduleName] Removing release directory $releaseDirectory")
    for ((hostname, (ssh, sftp)) <- connections) {
      ssh.rmTree(releaseDirectory)
    }
  }
}

object Deployer {
  type Connections = Map[String, (SSHClient, SFTPClient)]

  val DefaultCheckCommand = Some("true")

  val RsyncBaseOpts = Seq(
    "--checksum",
    "--times",
    "--compress")

  def run(moduleName:   String,
            releaseId:    String,
            log:          sbt.Logger,
            environment:  DeploymentEnvironment,
            mainPackage:  sbt.File,
            mainClass:    String,
            dependencies: Seq[sbt.File],
            libraries:    Seq[sbt.File]) {
    val connections = openConnections(environment)
    try {
      val deployer = new Deployer(moduleName, releaseId, log, environment, mainPackage, mainClass, dependencies, libraries, connections)
      deployer.run()
    } finally {
      closeConnections(connections)
    }
  }

  private[this] def connect(hostname: String, username: String = currentUser, port: Int = SSHClient.DEFAULT_PORT): SSHClient = {
    import collection.JavaConverters._
    new SSHClient tap { client =>
      client.loadKnownHosts()
      client.connect(hostname, port)
      client.auth(username, Auth(client).methods.asJava)
      client.useCompression()
    }
  }

  private[this] def openConnections(environment: DeploymentEnvironment): Connections = {
    val connections = environment.hosts map { hostname =>
      val client = connect(hostname, environment.username, environment.port)
      val sftp   = client.newSFTPClient()
      (hostname, (client, sftp))
    }
    connections.toMap
  }

  private[this] def closeConnections(connections: Connections) {
    for ((_, (ssh, sftp)) <- connections) {
      sftp.close()
      ssh.close()
    }
  }
}
