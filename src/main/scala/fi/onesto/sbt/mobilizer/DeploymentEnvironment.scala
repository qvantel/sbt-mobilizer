package fi.onesto.sbt.mobilizer

import util._
import net.schmizz.sshj.SSHClient


/**
  * Specifies a target environment for deployment.
  *
  * @param hosts List of hosts in the format `[USER@]HOSTNAME[:PORT]` where USER defaults to [[username]] and
  *              PORT defaults to [[port]] if not given.
  * @param standbyHosts List of stand-by hosts: files are copied but [[restartCommand]] or [[checkCommand]] are not run.
  * @param jumpServer A function which receives a hostname and returns an jump server address (in above host format)
  *                   if such is required for the connection.
  * @param port Default SSH port; deprecated in favour of `HOSTNAME:PORT` syntax.
  * @param username Name of the user to log in as to the target hosts.
  * @param rootDirectory Target directory to deploy to on the hosts.
  * @param releasesDirectoryName Name of the directory containing releases under [[rootDirectory]].
  * @param currentDirectoryName Name of the current symbolic link under [[rootDirectory]].
  * @param libDirectoryName Name of the directory containing library files under [[rootDirectory]].
  * @param javaBin Path to the Java executable on the target hosts.
  * @param javaOpts Additional options to pass to the `java` command on startup.
  * @param rsyncCommand Name or path to the `rsync` command on the target hosts.
  * @param rsyncOpts Additional options to pass to `rsync` when copying files.
  * @param restartCommand Shell command for restarting the application on the target hosts.
  * @param checkCommand Shell command for checking whether application is up on the target hosts.
  */
final case class DeploymentEnvironment(
    hosts:                 Seq[String]              = Seq("localhost"),
    standbyHosts:          Seq[String]              = Seq.empty,
    jumpServer:            String => Option[String] = _ => None,
    @deprecated("use host:port syntax for hosts", "0.2.0")
    port:                  Int                      = SSHClient.DEFAULT_PORT,
    @deprecated("use user@host:port syntax for hosts", "0.3.0")
    username:              String                   = currentUser,
    rootDirectory:         String                   = "/tmp/deploy",
    releasesDirectoryName: String                   = "releases",
    currentDirectoryName:  String                   = "current",
    libDirectoryName:      String                   = "lib",
    javaBin:               String                   = "java",
    javaOpts:              Seq[String]              = Seq.empty,
    rsyncCommand:          String                   = "rsync",
    rsyncOpts:             Seq[String]              = Seq.empty,
    restartCommand:        Option[String]           = None,
    checkCommand:          Option[String]           = None) {

  val releasesRoot: String = s"$rootDirectory/$releasesDirectoryName"
  val currentDirectory:  String = s"$rootDirectory/$currentDirectoryName"

  def releaseDirectory(releaseId: String): String =
    s"$releasesRoot/$releaseId"

  def libDirectory(releaseId: String): String =
    s"${releaseDirectory(releaseId)}/$libDirectoryName"
}
