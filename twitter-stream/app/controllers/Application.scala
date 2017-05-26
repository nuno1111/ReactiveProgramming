package controllers

import play.api._
import play.api.mvc._
import play.api.Play.current
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.oauth.{RequestToken, ConsumerKey}
import play.api.libs.ws.WS
import play.api.libs.ws.WSResponse
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.iteratee.Iteratee
import play.extras.iteratees.JsonIteratees
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsObject
import play.extras.iteratees.Encoding
import play.api.libs.iteratee.Enumeratee
import actors.TwitterStreamer
import play.api.libs.json.JsValue

class Application extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index("Tweets"))
  }
  
  def tweets = WebSocket.acceptWithActor[String, JsValue] { request =>
    out => TwitterStreamer.props(out);
  }
  
  def test = Action.async {
    val response: Future[WSResponse] = WS.url("http://playframework.com").get();
    
    val siteOnline: Future[Boolean] = response.map { r =>
      r.status == 200
    }
    
    siteOnline.foreach { online =>
      if (online) {
        println("site is up")
      } else {
        println("site is down")
      }
    }
    
    siteOnline.map { status =>
      if (status) {
        Ok("site is up")
      } else {
        Ok("site is down")
      }
    }
    
  }
  
  def test2 = Action.async {
    val response: Future[WSResponse] = WS.url("http://playframework.com").get()
    
    val siteAvailable: Future[Option[Boolean]] = response.map { r => 
      Some(r.status == 200)
    } recover {
      case ce: java.net.ConnectException => None
    }
    
    siteAvailable.map { option =>
      Ok(option.toString)
    }
  }
  
/*
  def tweets = Action.async {
    val loggingIteratee = Iteratee.foreach[JsObject] { value =>
      Logger.info(value.toString)
    }
    credentials.map { case (consumerKey, requestToken) =>
      val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]
      
      val jsonStream: Enumerator[JsObject] = 
        enumerator &>
        Encoding.decode() &>
        Enumeratee.grouped(JsonIteratees.jsSimpleObject)
        
      jsonStream run loggingIteratee
      
      WS
      .url("https://stream.twitter.com/1.1/statuses/filter.json")
      .sign(OAuthCalculator(consumerKey, requestToken))
      .withQueryString("track" -> "문재인")
      .get { response =>
        Logger.info("status:"+response.status)
        iteratee
      }
      .map { _ =>
        Ok("Stream closed")
      }
    } getOrElse {
      Future.successful(InternalServerError("Twitter credential missing."))
    }
  	
  }
  
  */
  
  val credentials: Option[(ConsumerKey, RequestToken)] = for {
    apiKey <- Play.configuration.getString("twitter.apiKey")
    apiSecret <- Play.configuration.getString("twitter.apiSecret")
    token <- Play.configuration.getString("twitter.token")
    tokenSecret <- Play.configuration.getString("twitter.tokenSecret")
  } yield(ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))

}