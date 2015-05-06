package fi.onesto.sbt.mobilizer

import util._
import net.schmizz.sshj.SSHClient


final case class DeploymentEnvironment(
    hosts:                 Seq[String]    = Seq("localhost"),
    port:                  Int            = SSHClient.DEFAULT_PORT,
    username:              String         = currentUser,
    rootDirectory:         String         = "/tmp/deploy",
    releasesDirectoryName: String         = "releases",
    currentDirectoryName:  String         = "current",
    libDirectoryName:      String         = "lib",
    javaBin:               String         = "java",
    javaOpts:              Seq[String]    = Seq.empty,
    rsyncCommand:          String         = "rsync",
    rsyncOpts:             Seq[String]    = Seq.empty,
    restartCommand:        Option[String] = None,
    checkCommand:          Option[String] = None) {

  val releasesRoot: String = s"$rootDirectory/$releasesDirectoryName"
  val currentDirectory:  String = s"$rootDirectory/$currentDirectoryName"

  def releaseDirectory(releaseId: String): String =
    s"$releasesRoot/$releaseId"

  def libDirectory(releaseId: String): String =
    s"${releaseDirectory(releaseId)}/$libDirectoryName"
}
