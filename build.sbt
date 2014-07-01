sbtPlugin := true

name := "mobilizer"

organization := "fi.onesto.sbt"

version := "0.0.1-SNAPSHOT"

description := "Deployment plugin for SBT"

startYear := Some(2013)

homepage := Some(url("https://github.com/onesto/sbt-mobilizer"))


publishTo := Some(Classpaths.sbtPluginReleases) 

publishMavenStyle := false

publishArtifact in Test := false


scalaVersion := "2.10.4"

sbtVersion := "0.13.2"


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
  "com.github.nscala-time" %% "nscala-time"                 % "1.2.0",
  "org.bouncycastle"        % "bcprov-jdk15on"              % "1.50",
  "com.jcraft"              % "jzlib"                       % "1.1.3",
  "org.slf4j"               % "slf4j-api"                   % "1.7.7",
  "net.schmizz"             % "sshj"                        % "0.9.0",
  "com.jcraft"              % "jsch.agentproxy.usocket-jna" % "0.0.7",
  "com.jcraft"              % "jsch.agentproxy.sshagent"    % "0.0.7",
  "com.jcraft"              % "jsch.agentproxy.pageant"     % "0.0.7",
  "com.jcraft"              % "jsch.agentproxy.sshj"        % "0.0.7",
  "commons-pool"            % "commons-pool"                % "1.6"
)
