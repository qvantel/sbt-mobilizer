package fi.onesto.sbt.mobilizer

import java.io.File
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.method._
import com.jcraft.jsch.agentproxy.usocket.JNAUSocketFactory
import com.jcraft.jsch.agentproxy.connector.{PageantConnector, SSHAgentConnector}
import com.jcraft.jsch.agentproxy.{AgentProxy, Connector}
import com.jcraft.jsch.agentproxy.sshj.AuthAgent

import util._


class Auth(client: SSHClient) {
  val sshDirectory = new File(currentUser, ".ssh")
  val idTypes = List("id_ecdsa", "id_rsa", "id_dsa")
  val keyFiles = idTypes.map(new File(sshDirectory, _)).filter(_.exists())

  def agentConnector: Option[Connector] = {
    if (SSHAgentConnector.isConnectorAvailable) {
      Option(new SSHAgentConnector(new JNAUSocketFactory))
    }
    else if (PageantConnector.isConnectorAvailable) {
      Option(new PageantConnector())
    }
    else {
      Option.empty
    }
  }

  val agentProxy = agentConnector map(new AgentProxy(_))

  val agentIdentities = agentProxy map(_.getIdentities.toList) getOrElse Nil

  val agentMethods = agentProxy.map { agent => agentIdentities.map(new AuthAgent(agent, _)) }.toList.flatten
  val pubkeyMethods = keyFiles map { file => client.loadKeys(file.getPath) } map (new AuthPublickey(_))
  val interactiveMethod = new AuthKeyboardInteractive(new PasswordResponseProvider(new PasswordPrompt(client)))
  val passwordMethod = new AuthPassword(new PasswordPrompt(client))

  val methods: List[AuthMethod] = agentMethods ++ pubkeyMethods :+ interactiveMethod :+ passwordMethod
}

object Auth {
  def apply(client: SSHClient) = new Auth(client)
}
