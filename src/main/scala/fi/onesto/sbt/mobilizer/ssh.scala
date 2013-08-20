package fi.onesto.sbt.mobilizer

import net.schmizz.sshj.SSHClient
import sbt.Logger

import util._


object ssh {
  def connect(hostname: String, username: String = currentUser, port: Int = SSHClient.DEFAULT_PORT)(implicit log: Logger): SSHClient = {
    import collection.JavaConverters._
    log.debug(s"opening SSH connection to $username@$hostname")
    new SSHClient tap { client =>
      client.loadKnownHosts()
      client.connect(hostname, port)
      client.auth(username, Auth(client).methods.asJava)
      client.useCompression()
    }
  }
}
