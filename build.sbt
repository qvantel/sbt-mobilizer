sbtPlugin := true

name := "mobilizer"

organization := "fi.onesto.sbt"

description := "Deployment plugin for SBT"

startYear := Some(2013)

homepage := Some(url("https://github.com/onesto/sbt-mobilizer"))


publishTo := Some(Classpaths.sbtPluginReleases) 

publishMavenStyle := false

publishArtifact in Test := false


scalaVersion := "2.10.2"

sbtVersion := "0.13.0"


net.virtualvoid.sbt.graph.Plugin.graphSettings


testOptions in Test += Tests.Argument("-oD")

javacOptions in ThisBuild ++= Seq(
  "-deprecation",
  "-g",
  "-source", "7",
  "-Xlint")

scalacOptions in ThisBuild ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  //"-explaintypes",
  //"-optimise",
  "-target:jvm-1.7",
  //"-Xcheck-null",
  "-Xcheckinit",
  "-Xlint",
  "-Yclosure-elim",
  "-Ydead-code",
  "-Yinline",
  "-Yinline-handlers",
  "-Ywarn-all",
  "-Xlog-reflective-calls",
  "-Xmax-classfile-name", "130"  // avoid problems on eCryptFS
)

libraryDependencies ++= Seq(
  "com.github.nscala-time" %% "nscala-time"                 % "0.6.0",
  "org.bouncycastle"        % "bcprov-jdk16"                % "1.46",
  "com.jcraft"              % "jzlib"                       % "1.1.2",
  "ch.qos.logback"          % "logback-classic"             % "1.0.13",
  "net.schmizz"             % "sshj"                        % "0.9.0" excludeAll(ExclusionRule(organization = "org.bouncycastle")),
  "com.jcraft"              % "jsch.agentproxy.usocket-jna" % "0.0.6",
  "com.jcraft"              % "jsch.agentproxy.sshagent"    % "0.0.6",
  "com.jcraft"              % "jsch.agentproxy.pageant"     % "0.0.6",
  "com.jcraft"              % "jsch.agentproxy.sshj"        % "0.0.6",
  "commons-pool"            % "commons-pool"                % "1.6"
)
