package fi.onesto.sbt.mobilizer

import sbt.File


object SbtCompat {
  val isArchive = sbt.classpath.ClasspathUtilities.isArchive(_: File)
}
