package edu.neu.coe.csye7200.burningcrusade.ingest

import java.time.{LocalDate, LocalDateTime}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}

import sttp.client3._
import org.json4s._
import org.json4s.{StringInput, Formats, AsJsonInput}
//import org.json4s.native.JsonMethods.parse
import org.json4s.native.JsonMethods._
import org.json4s.jvalue2monadic
import scala.concurrent.duration._

object NewsFetcherActor {
  sealed trait Command

  case object Fetch extends Command

  // 第 20 行：声明仅一次 given（或 implicit） 后端实例，供整份代码使用
  // *** 修正1：去掉 “val”，直接写 `given sttpBackend: ...` 或用 `implicit val ...` ***
  given sttpBackend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

  // 第 25 行：声明 JSON 解析所需的 Formats
  // *** 修正2：同理，不要写 `given val jsonFormats`; 改成 “given” 或 “implicit val” ***
  given jsonFormats: Formats = DefaultFormats

  given stringAsJson: AsJsonInput[String] with {
    override def toJsonInput(str: String): JsonInput = StringInput(str)
  }

  case class Config(
    name: String,
    query: String,
    publishedAfter: String,   // 例如 "2025-04-01T00:00:00Z"
    publishedBefore: String,  // 例如 "2025-04-15T00:00:00Z"
    apiKey: String,
    maxPage: Int,
  )

  def searchNews(
    name: String,
    query: String,
    publishedAfter: String,   // 例如 "2025-04-01T00:00:00Z"
    publishedBefore: String,  // 例如 "2025-04-15T00:00:00Z"
    apiKey: String,
    maxPage: Int,
    downstream: ActorRef[KafkaProducerActor.Publish]
  ): Unit = {

    println("[Fetcher] Fetch News received.")

    val url = uri"https://newsapi.org/v2/everything?q=${query}&apiKey=${apiKey}&from=${publishedAfter}&to=${publishedBefore}&pageSize=${maxPage}"

    val request = basicRequest
      .get(url).response(asStringAlways)
      //.header("Authorization", s"Bearer $bearerToken")

    val response = request.send()
    val rawBody = response.body
    //println(s"Raw response body 1: $rawBody")
    val json = parse(rawBody)

    // 4) 提取 items[] 里每个 item 的 id.videoId
    //    只取 kind == "youtube#video" 的
    val items = (json \ "articles").children
    val posts = items.flatMap { item =>
      val title  = (item \ "title").extractOrElse("")
      //println(s"Title: $title")
      val description = (item \ "description").extractOrElse("")
      val res = s"${title} \n ${description}"
      Some(res)
    }

    posts.foreach { c =>
        downstream ! KafkaProducerActor.Publish(s"[NEWS ${name}] ${c}")
    }

  }

  def apply(printer: ActorRef[KafkaProducerActor.Publish], config: Config): Behavior[Command] = {
    Behaviors.withTimers { (timers: TimerScheduler[Command]) =>
      // 启动定时器，每 10 秒触发一次 Fetch 消息
      timers.startTimerWithFixedDelay(Fetch, Fetch, 10.seconds)

      Behaviors.setup { _ =>
        // 使用 Scala 3 的 given 替代 implicit
        //given backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
        //given formats: Formats = DefaultFormats

        Behaviors.receiveMessage {
          case Fetch =>
  
            searchNews(config.name, config.query, config.publishedAfter, config.publishedBefore, config.apiKey, config.maxPage, printer)

            Behaviors.same
        }
      }
    }
  }
}