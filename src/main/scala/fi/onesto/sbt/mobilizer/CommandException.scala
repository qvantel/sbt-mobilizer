package fi.onesto.sbt.mobilizer


class CommandException(name: String, message: String, exitStatus: Int)
  extends Exception("Command " + name + " exited with status " + exitStatus + ": " + message)