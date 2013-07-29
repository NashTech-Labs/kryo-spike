package com.ig.remote.spike

import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.pattern.ask
import scala.concurrent.Await
import akka.util._
import scala.concurrent.duration._
import net.liftweb.common._

object LocalApp extends App {

  val hostAddress: String = java.net.InetAddress.getLocalHost.getHostAddress()

  /* Configuration */
  val configString = """include "reference""""
  val configuration = ConfigFactory.parseString(configString)
  val system = ActorSystem("Node2", ConfigFactory.load(configuration))

  val port: Int = NettyPort(system).port

  /* Central system for fetching actor references */
  val discoveryService = system.actorFor("akka://Node3@" + hostAddress + ":3557" + "/user/Discovery")

  implicit val timeout = Timeout(5 seconds)

  // Fetch actor references from discovery service and make calls on actor references
  val future = discoveryService ? ("CHESS")
  val chessPath = Await.result(future, timeout.duration).asInstanceOf[String]
  val remoteChessGameReference = system.actorFor(chessPath)
  val messageBundle = MessageBundle(1, "CHESS")
  val chessBoxedItem = (ObjectGraph(12, messageBundle))
  remoteChessGameReference ! chessBoxedItem

  // Sending chat message to FOOTBALL player
  println("I sent a Message")
  /*
   *Sending to DiscoverService to check, whether
   * Box/Option is serializing or not
   */

  //In case of Option
  //discoveryService ! SubscribeChat(Some(chessPath))
  // In case of Box
  discoveryService ! SubscribeChat(Full(chessPath))

}

case class ObjectGraph(id: Int, mb: MessageBundle)
case class MessageBundle(id: Int, message: String)

case class UpdateChatChannels(who: User, what: Box[Admin], whoWhere: Map[User, Box[Admin]])
//case class UpdateChatChannels(who: User, what: Option[Admin], whoWhere: Map[User, Option[Admin]])

case class SubscribeChat(actPath: Box[String])
//case class SubscribeChat(actPath: Option[String])

case class ChatSubResp(chats: AllChats)
case class AllChats(chat: ChatMessage)

//case class ChatMessage(userId: Long, from: Option[User], msg: String, to: Option[Admin], channel: Option[String])
case class ChatMessage(userId: Long, from: Box[User], msg: String, to: Box[Admin], channel: Box[String])

case class User(userId: Long, name: String)
case class Admin(adminId: Long, name: String)