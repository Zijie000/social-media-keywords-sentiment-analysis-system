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

object YoutubeFetcherActor {
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
    query: String,
    publishedAfter: String,   // 例如 "2025-04-01T00:00:00Z"
    publishedBefore: String,  // 例如 "2025-04-15T00:00:00Z"
    apiKey: String,
    maxResults: Int,
  )

  def searchVideos(
    query: String,
    publishedAfter: String,   // 例如 "2025-04-01T00:00:00Z"
    publishedBefore: String,  // 例如 "2025-04-15T00:00:00Z"
    apiKey: String,
    maxResults: Int
  ): List[String] = {

    println("[Fetcher] Fetch video received.")

    val url = uri"https://www.googleapis.com/youtube/v3/search?part=snippet&q=${query}&maxResults=${maxResults}&publishedBefore=${publishedBefore}&publishedAfter=${publishedAfter}&key=${apiKey}"

    val request = basicRequest
      .get(url).response(asStringAlways)
      //.header("Authorization", s"Bearer $bearerToken")

    val response = request.send()
    val rawBody = response.body
    //println(s"Raw response body 1: $rawBody")
    val json = parse(rawBody)

    // 4) 提取 items[] 里每个 item 的 id.videoId
    //    只取 kind == "youtube#video" 的
    val items = (json \ "items").children
    val videoIds = items.flatMap { item =>
      val kind  = (item \ "id" \ "kind").extractOrElse("")
      if (kind == "youtube#video") {
        Some((item \ "id" \ "videoId").extractOrElse(""))
      } else None
    }

    videoIds.filter(_.nonEmpty)

  }

  private def fetchComments(
    apiKey: String,
    videoId: String,
    maxResults: Int
  ): List[String] = {

    println("[Fetcher] Fetch command received.")

    val url = uri"https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId=${videoId}&maxResults=${maxResults}&&key=${apiKey}"


    val request = basicRequest
      .get(url).response(asStringAlways)
      //.header("Authorization", s"Bearer $bearerToken")

    val response = request.send()

    val rawBody = response.body
    //println(s"Raw response body 2: $rawBody")
    val json = parse(rawBody)

    // 4) 提取每条评论 textOriginal
    //    路径: items[].snippet.topLevelComment.snippet.textOriginal
    val items = (json \ "items").children

    val comments = items.flatMap { item =>
      val snippet = item \ "snippet"
      val topLevel = snippet \ "topLevelComment"
      val commentText = (topLevel \ "snippet" \ "textOriginal").extractOrElse("")
      if (commentText.nonEmpty) Some(commentText) else None
    }

    //println(s"[fetchComments] For video=$videoId got ${comments.size} comment(s).")
    comments
    
  }

  private def fetchDaily(
    downstream: ActorRef[String],
    config: Config
  ): Unit = {
    //val today = LocalDate.now()
    //val yesterday = today.minusDays(1)

    // 伪代码: searchVideos => comments => 下游
    val videoIds = searchVideos(
      query = config.query,
      publishedAfter = config.publishedAfter,
      publishedBefore = config.publishedBefore,  
      apiKey = config.apiKey,
      maxResults = config.maxResults
    )
    videoIds.foreach { vid =>
      val comments = fetchComments(
        maxResults = config.maxResults,
        apiKey  = config.apiKey,
        videoId = vid
      )
      comments.foreach { c =>
        downstream ! c
      }
    }
  }

  def apply(printer: ActorRef[String], config: Config): Behavior[Command] = {
    Behaviors.withTimers { (timers: TimerScheduler[Command]) =>
      // 启动定时器，每 10 秒触发一次 Fetch 消息
      timers.startTimerWithFixedDelay(Fetch, Fetch, 10.seconds)

      Behaviors.setup { _ =>
        // 使用 Scala 3 的 given 替代 implicit
        //given backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
        //given formats: Formats = DefaultFormats

        Behaviors.receiveMessage {
          case Fetch =>
            
            fetchDaily(printer, config)

            Behaviors.same
        }
      }
    }
  }
}