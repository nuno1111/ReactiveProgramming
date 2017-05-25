package actors

import akka.actor.{Actor, ActorRef, Props}
import play.Logger
import play.api.libs.json.Json
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Iteratee
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumeratee
import play.extras.iteratees.JsonIteratees
import play.api.libs.ws.WS
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.oauth.RequestToken
import play.api.libs.oauth.ConsumerKey
import play.extras.iteratees.Encoding
import play.api.Play
import play.api.libs.iteratee.Concurrent
import scala.concurrent.Future
import play.api.Play.current

class TwitterStreamer(out: ActorRef) extends Actor {
  def receive = {
    case "subscribe" =>
      Logger.info("Received subscription from a client")
      TwitterStreamer.subscribe(out)
  }
}

object TwitterStreamer {
  def props(out: ActorRef) = Props(new TwitterStreamer(out))
  private var broadcaseEnumerator: Option[Enumerator[JsObject]] = None
  
  def connect(): Unit = {
    credentials.map { case (consumerKey, requestToken) =>
      val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]
      
      val jsonStream: Enumerator[JsObject] = 
        enumerator &>
        Encoding.decode() &>
        Enumeratee.grouped(JsonIteratees.jsSimpleObject)
        
      val (be, _) = Concurrent.broadcast(jsonStream)
      broadcaseEnumerator = Some(be)
      
      WS
      .url("https://stream.twitter.com/1.1/statuses/filter.json")
      .sign(OAuthCalculator(consumerKey, requestToken))
      .withQueryString("track" -> "문재인")
      .get { response =>
        Logger.info("status:"+response.status)
        iteratee
      }
      .map { _ =>
        Logger.info("Twitter stream closed")
      }
    } getOrElse {
      Logger.info("Twitter credential missing")
    }
  }
  
  def subscribe(out: ActorRef): Unit = {
    if (broadcaseEnumerator.isEmpty) {
      connect()
    }
    
    val twitterClient = Iteratee.foreach[JsObject] { t =>
      out ! t
    }
    
    broadcaseEnumerator.foreach { enumerator =>
      enumerator run twitterClient
    }
  }
  
  val credentials: Option[(ConsumerKey, RequestToken)] = for {
    apiKey <- Play.configuration.getString("twitter.apiKey")
    apiSecret <- Play.configuration.getString("twitter.apiSecret")
    token <- Play.configuration.getString("twitter.token")
    tokenSecret <- Play.configuration.getString("twitter.tokenSecret")
  } yield(ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))
}