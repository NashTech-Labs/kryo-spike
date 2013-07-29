package com.ig.remote.spike

import scala.collection.mutable.HashMap
import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.actor.Kill
import akka.actor.PoisonPill
import akka.actor.Terminated
import net.liftweb.common.Full
import net.liftweb.common.Empty

object DiscoveryService extends App {

  /* A directory to hold all remote references */
  val directory: HashMap[String, ActorDetails] = new HashMap
  val hostAddress: String = java.net.InetAddress.getLocalHost.getHostAddress()

  /** These settings can be externalized  */
  val configString = """include "reference"
    akka.actor.provider = "akka.remote.RemoteActorRefProvider"
    akka.remote.netty.hostname = """" + hostAddress + """"
    akka.remote.netty.port     = 3557"""
  val configuration = ConfigFactory.parseString(configString)

  val remoteSystem = ActorSystem("Node3", ConfigFactory.load(configuration))

  val discoveryActor = remoteSystem.actorOf(Props[DiscoveryServiceActor], "Discovery")
  println(discoveryActor.path)

  /**
   * Discovery actor which would receive registrations and hand out references
   */
  class DiscoveryServiceActor extends Actor {
    def receive = {
      case gameActorDetails: ActorDetails => addChildReference(gameActorDetails)

      case gameName: String => sender ! fetchChildActor(gameName)
      // In case pf Option
     /* case sc @ SubscribeChat(Some(chessPath)) => {
        val remoteChessGameReference = remoteSystem.actorFor(chessPath)
        val chatMessage = ChatMessage(1, None, "Welcome in FOOTBALL Game", None, None)
        remoteChessGameReference ! ChatSubResp(AllChats(chatMessage))
        remoteChessGameReference ! UpdateChatChannels(User(1, "user1"), None, Map.empty)
      }*/
      //In case Of Box
      case sc @ SubscribeChat(Full(chessPath)) => {
        val remoteChessGameReference = remoteSystem.actorFor(chessPath)
        val chatMessage = ChatMessage(1, Empty, "Welcome in FOOTBALL Game", Empty, Empty)
        remoteChessGameReference ! ChatSubResp(AllChats(chatMessage))
        remoteChessGameReference ! UpdateChatChannels(User(1, "user1"), Empty, Map.empty)
      }
    }

    def fetchChildActor(gameName: String): String = {
      generateChildPath(directory.get(gameName))
    }

    def addChildReference(actorDetails: ActorDetails) = {
      directory += ((actorDetails.gameName) -> actorDetails)
    }

    private def generateChildPath(actorDetails: Option[ActorDetails]): String = {
      val actualActorDetails = actorDetails.get

      val path = "akka://" + actualActorDetails.actorSystem + "@" + actualActorDetails.hostName + ":" +
        actualActorDetails.port + actualActorDetails.gamePath.substring(actualActorDetails.gamePath.indexOf("/user"))
      path
    }

  }
}

/** Actor registration template used for registering an actor with Discovery Service */
case class ActorDetails(actorSystem: String, hostName: String, port: String, gameName: String, gamePath: String)