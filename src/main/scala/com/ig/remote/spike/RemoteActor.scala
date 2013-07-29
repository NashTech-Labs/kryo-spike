package com.ig.remote.spike

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import com.typesafe.config.ConfigFactory
import akka.actor.Actor
import akka.actor.ActorPath
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.OneForOneStrategy
import akka.actor.Props
import akka.actor.SupervisorStrategy.Resume
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import akka.actor.ExtendedActorSystem
import akka.remote.RemoteActorRefProvider
import net.liftweb.common.Box
import net.liftweb.common.Empty

object RemoteApplication extends App {

  val hostAddress: String = java.net.InetAddress.getLocalHost.getHostAddress()

  /* Timeout definition after which it would not wait for result */
  implicit val timeout = Timeout(5 seconds)
  val remoteSystemName = "Node1"

  // NOTE: ** These settings can be external to the program
  val configString = """ include "reference" """
  val configuration = ConfigFactory.parseString(configString)
  val remoteSystem = ActorSystem(remoteSystemName, ConfigFactory.load(configuration))

  val port: Int = NettyPort(remoteSystem).port

  /* Central system for fetching actor references */
  val discoveryService = remoteSystem.actorFor("akka://Node3@" + hostAddress + ":3557" + "/user/Discovery")

  // --------------------
  // Actor Creation
  // --------------------

  //Create a supervisor
  val gameSupervisor = remoteSystem.actorOf(Props[GameSupervisor], "GameSupervisor")

  //Ask the supervisor to create a game actor
  val chessGameInstance = createNewGame("CHESS")
  println(chessGameInstance.path)
  addToDiscoveryService("CHESS", chessGameInstance.path)

  // GameSupervisor would now pass the reference of this cageInstance to a central location

  private def createNewGame(gameName: String): ActorRef = {
    val future = gameSupervisor ? (Props[Game], gameName)
    val gameInstance = Await.result(future, timeout.duration).asInstanceOf[ActorRef]
    gameInstance
  }

  def addToDiscoveryService(name: String, path: ActorPath) = {
    discoveryService ! (ActorDetails(remoteSystemName, hostAddress, port.toString, name, path.toString()))
  }

}

class GameSupervisor extends Actor {
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 second) {

    case _: Exception =>
      println("Game Instance dead... Recreating ...")
      Resume
  }
  /** Prepares and sends an actor recognised by props */
  def receive = {
    case (props: Props, name: String) => sender ! context.actorOf(props, name)
  }
}

class Game extends Actor {
  //private var myChats: List[Option[User]] = List(None)
  
  // Box-case
  private var myChats: List[Box[User]] = List(Empty)
  def receive = {
    case msg: ObjectGraph => {
      println("You said start a new game for " + msg.mb.message + ", I said sure I will")
    }
    case aRef: ActorRef => {
      println("Remote actor hit with an actor reference " + aRef)
      aRef ! "-- hello there"
    }
    case x: String => {
      println("Got the message " + x)
      sender ! " To the other actor"
    }

    case chatSubResp: ChatSubResp => {
      val cm = chatSubResp.chats.chat
      /*
       * If myChats is List(Empty) or List(None) and 
       * cm.channel is Empty/None then
       * myChats.contains(cm.channel) must be true.
       */
      println("myChats="+myChats)
      println("cm.channel="+cm.channel)
      println("-Checking  in ChatSubResp -myChats.contains(cm.channel)=" + myChats.contains(cm.channel))
      if (myChats.contains(cm.channel)) {
        println("--Show Message--------------------------" + cm.msg)
      }
    }

    case update: UpdateChatChannels => {
      /*
       * If myChats is List(Empty) or List(None) and 
       * cm.channel is Empty/None then
       * myChats.contains(cm.channel) must be true.
       */
      println("myChats="+myChats)
      println("update.what="+update.what)
      println("-Checking  in UpdateChatChannels case -myChats.contains(cm.channel)=" + myChats.contains(update.what))
    }
    case _ => println("Huh! say something!")
  }
}

