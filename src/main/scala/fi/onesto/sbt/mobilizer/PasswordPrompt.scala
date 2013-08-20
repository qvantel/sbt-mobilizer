package fi.onesto.sbt.mobilizer

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.password.{Resource, PasswordFinder}

import util._


class PasswordPrompt(client: SSHClient) extends PasswordFinder {
  private var tries = 0

  def reqPassword(resource: Resource[_]): Array[Char] = {
    System.console().readPassword(client.getRemoteHostname + " Password: ") tap { _ =>
      tries += 1
    }
  }

  def shouldRetry(resource: Resource[_]): Boolean = tries < 3
}
