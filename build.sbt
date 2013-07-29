

name:="RemoteLookupSpike"

version :="1.0"

scalaVersion :="2.10.0"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

unmanagedBase <<= baseDirectory { base => base / "libs" }

libraryDependencies ++= Seq(
        "com.typesafe.akka" % "akka-actor_2.10" % "2.1.1",
        "com.typesafe.akka" % "akka-remote_2.10" % "2.1.1",
        "com.typesafe.akka" % "akka-testkit_2.10" % "2.1.1",
        "com.typesafe.akka" % "akka-kernel_2.10" % "2.1.1",
        "net.liftweb" %% "lift-webkit" % "2.5" % "compile->default",
        "com.esotericsoftware.kryo" % "kryo" % "2.20"
)


libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1"

libraryDependencies += "junit" % "junit" % "4.9"

