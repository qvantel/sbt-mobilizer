package fi.onesto.sbt

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.Session.Shell
import sbt.Logger


package object mobilizer {
  import util._

  implicit class RichSSHClient(underlying: SSHClient) {
    def withSession[A](action: Session => A): A = {
      val session = underlying.startSession()
      try {
        action(session)
      }
      finally {
        session.close()
      }
    }

    def withShell[A](pty: Symbol = 'noPty)(action: Shell => A): A = {
      withSession { session =>
        if (pty != 'noPty)
          session.allocateDefaultPTY()
        action(session.startShell())
      }
    }

    def run(commandName: String, args: String*): Iterator[String] =
      runWithOptionalInput(commandName, None, args: _*)

    def runWithInput(commandName: String, input: String, args: String*): Iterator[String] =
      runWithOptionalInput(commandName, Some(input), args: _*)

    def runWithOptionalInput(commandName: String, inputOption: Option[String], args: String*): Iterator[String] = {
      withSession { session =>
        val command = session.exec(shellQuote(commandName, args: _*))
        inputOption foreach { input =>
          command.getOutputStream.write(input.getBytes)
          command.getOutputStream.close()
        }
        io.Source.fromInputStream(command.getInputStream).getLines() tap { output =>
          val errors = io.Source.fromInputStream(command.getErrorStream).getLines().mkString("\n")
          command.join()
          val exitStatus = command.getExitStatus
          if (exitStatus != 0)
            throw new CommandException(commandName, errors, exitStatus)
        }
      }
    }

    def mkdir(paths: String*) = run("mkdir", paths: _*)

    def mkdirWithParents(paths: String*) = run("mkdir", "-p" +: paths: _*)

    def symlink(source: String, destination: String) = run("ln", "-nsf", source, destination)

    def listFiles(): Iterator[String] = run("ls")

    def listFiles(path: String): Iterator[String] = run("ls", path)

    def createFile(path: String, content: String, mode: String = "0644") =
      runWithInput("sh", content, "-c", "cat > " + path + " && chmod " + mode + " " + path)

    def rmTree(path: String) =
      run("rm", "-rf", path)
  }

  implicit class RichDeploymentEnvironment(env: DeploymentEnvironment)(implicit connections: Map[String, SSHClient], releaseId: String, log: Logger) {
    def startupScriptContent(mainPackage: String, mainClass: String): String = {
      val pkgPath = s"${env.releaseDirectory(releaseId)}/$mainPackage"
      val libPath = env.libDirectory(releaseId)

      s"""#!/bin/sh
        |
        |CLASSPATH="$pkgPath:`find $libPath -type f -name '*.jar' | paste -sd:`"
        |export CLASSPATH
        |exec ${env.javaBin} $mainClass "$$@"
        |""".stripMargin
    }

    def onEachHost[A](action: SSHClient => A): Map[String, A] = {
      val results = env.hosts map { host =>
        host -> action(connections(host))
      }
      results.toMap
    }

    def run(commandName: String, args: String*): Map[String, Iterator[String]] =
      runWithOptionalInput(commandName, None, args: _*)

    def runWithInput(commandName: String, input: String, args: String*): Map[String, Iterator[String]] =
      runWithOptionalInput(commandName, Some(input), args: _*)

    def runWithOptionalInput(commandName: String, input: Option[String], args: String*): Map[String, Iterator[String]] =
      onEachHost(_.runWithOptionalInput(commandName, input, args: _*))

    def mkdir(paths: String*) = run("mkdir", paths: _*)

    def mkdirWithParents(paths: String*) = run("mkdir", "-p" +: paths: _*)

    def symlink(source: String, destination: String) = run("ln", "-nsf", source, destination)

    def listFiles(): Map[String, Iterator[String]] = run("ls")

    def listFiles(path: String): Map[String, Iterator[String]] = run("ls", path)

    def createFile(path: String, content: String, mode: String = "0644") =
      runWithInput("sh", content, "-c", "cat > " + path + " && chmod " + mode + " " + path)

    def rmTree(path: String) =
      run("rm", "-rf", path)

    def createReleasesDirectories() {
      log.info(s"Creating releases directory ${env.releasesDirectory}")
      mkdirWithParents(env.releasesDirectory)
    }

    def createReleaseDirectories() {
      log.info(s"Creating release directory ${env.releaseDirectory(releaseId)}")
      mkdirWithParents(env.releaseDirectory(releaseId))
    }

    def findPreviousReleaseDirectories(): Map[String, Option[String]] = {
      val results = env.hosts map { host =>
        host -> connections(host).run("find", env.releasesDirectory, "-mindepth", "1", "-maxdepth", "1", "-type", "d").toSeq.sorted.lastOption
      }
      results.toMap
    }

    def updateSymlinks() {
      val releaseDirectory = env.releaseDirectory(releaseId)
      log.info(s"Updating current symlink to $releaseDirectory")
      symlink(releaseDirectory, env.currentDirectory)
    }

    def removeCurrentRelease() {
      log.warn(s"Removing release directory ${env.releaseDirectory(releaseId)}")
      rmTree(env.releaseDirectory(releaseId))
    }

    def restoreSymlinks(previousReleaseDirectories: Map[String, Option[String]]) {
      log.info(s"Rolling back current symlink")
      previousReleaseDirectories map { case (host, previousDirectoryOption) =>
        previousDirectoryOption map { previousDirectory =>
          connections(host).symlink(previousDirectory, env.currentDirectory)
        }
      }
    }
  }
}
