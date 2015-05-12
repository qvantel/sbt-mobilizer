sbtPlugin := true

scalaVersion := "2.10.5"

sbtVersion := "0.13.8"

name := "sbt-mobilizer"

version := "0.1.0"

organization := "fi.onesto.sbt"

organizationName := "Onesto Services Oy"

organizationHomepage := Option(new java.net.URL("http://onesto.fi"))

description := "Deployment plugin for SBT"

startYear := Option(2013)

homepage := Option(url("https://github.com/onesto/sbt-mobilizer"))

scmInfo := Option(ScmInfo(new java.net.URL("https://github.com/onesto/sbt-mobilizer"), "scm:git:github.com/onesto/sbt-mobilizer.git", Option("scm:git:git@github.com:onesto/sbt-mobilizer.git")))

publishMavenStyle := false

publishArtifact in Test := false

bintrayOrganization := Option("onesto")

licenses += ("MIT", url("https://github.com/onesto/sbt-mobilizer/blob/master/LICENSE"))


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
  "com.github.nscala-time" %% "nscala-time"                 % "2.0.0",
  "org.bouncycastle"        % "bcprov-jdk15on"              % "1.52",
  "org.bouncycastle"        % "bcpkix-jdk15on"              % "1.52",
  "com.jcraft"              % "jzlib"                       % "1.1.3",
  "org.slf4j"               % "slf4j-api"                   % "1.7.12",
  "com.hierynomus"          % "sshj"                        % "0.12.0",
  "com.jcraft"              % "jsch.agentproxy.usocket-jna" % "0.0.9",
  "com.jcraft"              % "jsch.agentproxy.sshagent"    % "0.0.9",
  "com.jcraft"              % "jsch.agentproxy.pageant"     % "0.0.9",
  "com.jcraft"              % "jsch.agentproxy.sshj"        % "0.0.9" excludeAll(ExclusionRule(organization = "net.schmizz")),
  "commons-pool"            % "commons-pool"                % "1.6"
)
