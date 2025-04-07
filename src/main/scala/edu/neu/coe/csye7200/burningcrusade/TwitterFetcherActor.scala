package edu.neu.coe.csye7200.burningcrusade.ingest

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import sttp.client3._
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.concurrent.duration._

object TwitterFetcherActor {
  sealed trait Command

  case object Fetch extends Command

  def apply(printer: ActorRef[String]): Behavior[Command] = {
    Behaviors.withTimers { (timers: TimerScheduler[Command]) =>
      // 启动定时器，每 10 秒触发一次 Fetch 消息
      timers.startTimerWithFixedDelay(Fetch, Fetch, 1.minutes)

      Behaviors.setup { _ =>
        // 使用 Scala 3 的 given 替代 implicit
        given backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
        given formats: Formats = DefaultFormats

        Behaviors.receiveMessage {
          case Fetch =>
            println("[Fetcher] Fetch command received.")

            val bearerToken = "AAAAAAAAAAAAAAAAAAAAAFKu0QEAAAAA5YEJc9tYLMHK3VUAQC%2BbIDalSCM%3DTQMuCtCzT9oERr0J2BHPpnHJQ5fFZVWQQ0FOMvJyS9IJRAxqyu" // 替换为你的 token
            val url = uri"https://api.twitter.com/2/tweets/search/recent?query=scala&max_results=10"


            val request = basicRequest
              .get(url)
              .header("Authorization", s"Bearer $bearerToken")

            val response = request.send()

            // 🔍 打印原始 API 响应（JSON 字符串）
            println(s"[Fetcher] Raw response body: ${response.body}")

            val json = parse(response.body.getOrElse("{}"))

            // 🔍 打印解析后的 data 数组长度
            val tweets = (json \ "data").children
            println(s"[Fetcher] Parsed ${tweets.length} tweets.")

            // 🔍 打印每条推文内容
            tweets.foreach { tweet =>
              val text = (tweet \ "text").extractOrElse("No text")
              println(s"[Tweet]: $text")       // 打印到控制台
              printer ! text                   // 发给下游 actor
            }

            Behaviors.same
        }
      }
    }
  }
}
