package fi.onesto.sbt.mobilizer

import util._
import net.schmizz.sshj.SSHClient


case class DeploymentEnvironment(
    hosts:                 Seq[String]                   = Seq("localhost"),
    port:                  Int                           = SSHClient.DEFAULT_PORT,
    username:              String                        = currentUser,
    rootDirectory:         String                        = "/tmp/deploy",
    releasesDirectoryName: String                        = "releases",
    currentDirectoryName:  String                        = "current",
    libDirectoryName:      String                        = "lib",
    javaBin:               String                        = "java",
    startCommand:          Option[(String, Seq[String])] = None,
    restartCommand:        Option[(String, Seq[String])] = None,
    checkCommand:          Option[(String, Seq[String])] = None) {

  val releasesDirectory: String = s"$rootDirectory/$releasesDirectoryName"
  val currentDirectory:  String = s"$rootDirectory/$currentDirectoryName"

  def releaseDirectory(releaseId: String): String =
    s"$releasesDirectory/$releaseId"

  def libDirectory(releaseId: String): String =
    s"${releaseDirectory(releaseId)}/$libDirectoryName"
}
