package fi.onesto.sbt.mobilizer

import net.schmizz.sshj.common.SSHPacket
import net.schmizz.sshj.connection.Connection
import net.schmizz.sshj.connection.channel.direct.AbstractDirectChannel


final class ProxyChannel(
    conn:     Connection,
    hostname: String,
    port:     Int)
  extends AbstractDirectChannel(conn, "direct-tcpip") {

  override protected def buildOpenReq: SSHPacket =
    super.buildOpenReq
      .putString(hostname)
      .putUInt32(port)
      .putString("0.0.0.0")
      .putUInt32(65535)
}
