package fi.onesto.sbt.mobilizer

import java.io.File
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.method._
import com.jcraft.jsch.agentproxy.usocket.JNAUSocketFactory
import com.jcraft.jsch.agentproxy.connector.{PageantConnector, SSHAgentConnector}
import com.jcraft.jsch.agentproxy.{AgentProxy, Connector, Identity}
import com.jcraft.jsch.agentproxy.sshj.AuthAgent
import util._


final class Auth(client: SSHClient) {
  val idTypes: List[String] = List("id_ecdsa", "id_rsa", "id_dsa")
  val keyFiles: List[File] = idTypes.map(new File(sshDirectory, _)).filter(_.exists())

  def agentConnector: Option[Connector] = {
    if (SSHAgentConnector.isConnectorAvailable) {
      Some(new SSHAgentConnector(new JNAUSocketFactory))
    }
    else if (PageantConnector.isConnectorAvailable) {
      Some(new PageantConnector())
    }
    else {
      None
    }
  }

  val agentProxy: Option[AgentProxy] = agentConnector.map(new AgentProxy(_))
  val agentIdentities: List[Identity] = agentProxy.map(_.getIdentities.toList) getOrElse Nil

  val agentMethods: List[AuthAgent] = agentProxy.toList.flatMap(agent => agentIdentities.map(new AuthAgent(agent, _)))
  val pubkeyMethods: List[AuthPublickey] = keyFiles.map(file => new AuthPublickey(client.loadKeys(file.getPath)))

  val interactiveMethod = new AuthKeyboardInteractive(new PasswordResponseProvider(new PasswordPrompt(client)))
  val passwordMethod = new AuthPassword(new PasswordPrompt(client))

  val methods: List[AuthMethod] = agentMethods ++ pubkeyMethods :+ interactiveMethod :+ passwordMethod
}

object Auth {
  def apply(client: SSHClient) = new Auth(client)
}
