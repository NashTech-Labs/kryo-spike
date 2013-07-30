package com.ig.core.kryo

import com.esotericsoftware.kryo.util.MapReferenceResolver
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.ListReferenceResolver

import org.objenesis.strategy.StdInstantiatorStrategy

import scala.util.Failure
import scala.util.Success
import scala.collection.JavaConversions._

import akka.serialization.Serializer
import akka.event.Logging
import akka.actor.ExtendedActorSystem

import com.romix.akka.serialization.kryo.ObjectPool
import com.romix.akka.serialization.kryo.KryoSerialization
import com.romix.akka.serialization.kryo.KryoSerialization.Settings
import com.romix.akka.serialization.kryo.KryoBasedSerializer
import com.romix.scala.serialization.kryo.KryoClassResolver

import com.esotericsoftware.minlog.{Log => MiniLog}

/**
 * This is an extension of Kryo Serializer.
 * It provides helper classes for
 * serialization of some types such as Box.
 */

class ExtendedKryoSerializer(val system: ExtendedActorSystem) extends Serializer {

  /**
   * This class help us to serialize class
   * and to add serializers as kryo default serializer.
   */
  import KryoSerialization._
  val log = Logging(system, getClass.getName)

  val settings = new Settings(system.settings.config)

  val mappings = settings.ClassNameMappings

  locally {
    log.debug("Got mappings: {}", mappings)
  }

  val classnames = settings.ClassNames

  locally {
    log.debug("Got classnames for incremental strategy: {}", classnames)
  }

  val bufferSize = settings.BufferSize

  locally {
    log.debug("Got buffer-size: {}", bufferSize)
  }

  val serializerPoolSize = settings.SerializerPoolSize

  val idStrategy = settings.IdStrategy

  locally {
    log.debug("Got id strategy: {}", idStrategy)
  }

  val serializerType = settings.SerializerType

  locally {
    log.debug("Got serializer type: {}", serializerType)
  }

  val implicitRegistrationLogging = settings.ImplicitRegistrationLogging
  locally {
    log.debug("Got implicit registration logging: {}", implicitRegistrationLogging)
  }

  val useManifests = settings.UseManifests
  locally {
    log.debug("Got use manifests: {}", useManifests)
  }

  val serializer = try new KryoBasedSerializer(getKryo(idStrategy, serializerType),
    bufferSize,
    serializerPoolSize,
    useManifests)
  catch {
    case e: Exception => {
      log.error("exception caught during akka-kryo-serialization startup: {}", e)
      throw e
    }
  }

  locally {
    log.debug("Got serializer: {}", serializer)
  }

  // This is whether "fromBinary" requires a "clazz" or not
  def includeManifest: Boolean = useManifests

  // A unique identifier for this Serializer
  def identifier = 123454323

  def toBinary(obj: AnyRef): Array[Byte] = {
    val ser = getSerializer
    val bin = ser.toBinary(obj)
    releaseSerializer(ser)
    bin
  }

  def fromBinary(bytes: Array[Byte], clazz: Option[Class[_]]): AnyRef = {
    val ser = getSerializer
    val obj = ser.fromBinary(bytes, clazz)
    releaseSerializer(ser)
    obj
  }

  val serializerPool = new ObjectPool[Serializer](serializerPoolSize, () => {
    new KryoBasedSerializer(getKryo(idStrategy, serializerType),
      bufferSize,
      serializerPoolSize,
      useManifests)
  })

  private def getSerializer = serializerPool.fetch
  private def releaseSerializer(ser: Serializer) = serializerPool.release(ser)

  private def getKryo(strategy: String, serializerType: String): Kryo = {
    val referenceResolver = if (settings.KryoReferenceMap) new MapReferenceResolver() else new ListReferenceResolver()
    val kryo = new Kryo(new KryoClassResolver(implicitRegistrationLogging), referenceResolver)
    // Support deserialization of classes without no-arg constructors
    kryo.setInstantiatorStrategy(new StdInstantiatorStrategy())
    kryo.addDefaultSerializer(classOf[net.liftweb.common.Box[_]], classOf[LiftBoxSerializer])

    if (settings.KryoTrace)
      MiniLog.TRACE()

    strategy match {
      case "default" => {}

      case "incremental" => {
        kryo.setRegistrationRequired(false)

        for ((fqcn: String, idNum: String) <- mappings) {
          val id = idNum.toInt
          // Load class
          system.dynamicAccess.getClassFor[AnyRef](fqcn) match {
            case Success(clazz) => kryo.register(clazz, id)
            case Failure(e) => {
              log.error("Class could not be loaded and/or registered: {} ", fqcn)
              throw e
            }
          }
        }

        for (classname <- classnames) {
          // Load class
          system.dynamicAccess.getClassFor[AnyRef](classname) match {
            case Success(clazz) => kryo.register(clazz)
            case Failure(e) => {
              log.warning("Class could not be loaded and/or registered: {} ", classname)
              throw e
            }
          }
        }
      }

      case "explicit" => {
        kryo.setRegistrationRequired(false)

        for ((fqcn: String, idNum: String) <- mappings) {
          val id = idNum.toInt
          // Load class
          system.dynamicAccess.getClassFor[AnyRef](fqcn) match {
            case Success(clazz) => kryo.register(clazz, id)
            case Failure(e) => {
              log.error("Class could not be loaded and/or registered: {} ", fqcn)
              throw e
            }
          }
        }

        for (classname <- classnames) {
          // Load class
          system.dynamicAccess.getClassFor[AnyRef](classname) match {
            case Success(clazz) => kryo.register(clazz)
            case Failure(e) => {
              log.warning("Class could not be loaded and/or registered: {} ", classname)
              throw e
            }
          }
        }
        kryo.setRegistrationRequired(true)
      }
    }

    serializerType match {
      case "graph" => kryo.setReferences(true)
      case _ => kryo.setReferences(false)
    }

    kryo
  }
}