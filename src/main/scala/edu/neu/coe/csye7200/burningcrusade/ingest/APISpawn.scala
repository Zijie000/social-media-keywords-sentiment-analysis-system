package edu.neu.coe.csye7200.burningcrusade.ingest

import java.time.{LocalDate, LocalDateTime}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import edu.neu.coe.csye7200.burningcrusade.ingest.YoutubeFetcherActor.{Config => YoutubeConfig}
import edu.neu.coe.csye7200.burningcrusade.ingest.NewsFetcherActor.{Config => NewsConfig}
import edu.neu.coe.csye7200.burningcrusade.ingest.PrinterActor

object APISpawn {
  def main(args: Array[String]): Unit = {
    val rootBehavior = Behaviors.setup[Nothing] { context =>

      val keywords = "Trump"

      //val printer = context.spawn(PrinterActor("data.txt"), "Printer")

      val kafkaBootstrap = "localhost:19092"
      val kafkaTopic     = "social-text"

      val kafka = context.spawn(
        KafkaProducerActor(kafkaTopic, keywords, kafkaBootstrap),
        "KafkaProducer"
      )

      val zijieYoutubeKey = ""
      val zijieNewsKey = ""

      val weifanYoutubeKey = ""
      val weifanNewsKey = ""

      val xuanliYoutubeKey = ""
      val xuanliNewsKey = ""
    
      // 配置1：Youtube comment抓取
      val ytbCfg1 = YoutubeConfig(
        name = "Zijie",
        query = keywords,
        publishedAfter = LocalDate.now().minusDays(10).toString + "T00:00:00Z",
        publishedBefore = LocalDate.now().minusDays(1).toString + "T00:00:00Z",
        apiKey = zijieYoutubeKey,
        maxResults = 2,
      )

      val ytbCfg2 = YoutubeConfig(
        name = "Weifan",
        query = keywords,
        publishedAfter = LocalDate.now().minusDays(20).toString + "T00:00:00Z",
        publishedBefore = LocalDate.now().minusDays(10).toString + "T00:00:00Z",
        apiKey = weifanYoutubeKey,
        maxResults = 2,
      )

      val ytbCfg3 = YoutubeConfig(
        name = "Xuanli",
        query = keywords,
        publishedAfter = LocalDate.now().minusDays(30).toString + "T00:00:00Z",
        publishedBefore = LocalDate.now().minusDays(20).toString + "T00:00:00Z",
        apiKey = xuanliYoutubeKey,
        maxResults = 2,
      )

      val newsCfg1 = NewsConfig(
        name = "Zijie",
        query = keywords,
        publishedAfter = LocalDate.now().minusDays(40).toString + "T00:00:00Z",
        publishedBefore = LocalDate.now().minusDays(30).toString + "T00:00:00Z",
        apiKey = zijieNewsKey,
        maxPage = 3,
      )

      val newsCfg2 = NewsConfig(
        name = "Weifan",
        query = keywords,
        publishedAfter = LocalDate.now().minusDays(20).toString + "T00:00:00Z",
        publishedBefore = LocalDate.now().minusDays(10).toString + "T00:00:00Z",
        apiKey = weifanNewsKey,
        maxPage = 3,
      )

      val newsCfg3 = NewsConfig(
        name = "Xuanli",
        query = keywords,
        publishedAfter = LocalDate.now().minusDays(30).toString + "T00:00:00Z",
        publishedBefore = LocalDate.now().minusDays(20).toString + "T00:00:00Z",
        apiKey = xuanliNewsKey,
        maxPage = 3,
      )

      val youtubeFetcher1 = context.spawn(YoutubeFetcherActor(kafka, ytbCfg1), "YoutubeFetcher_zijie")
      val youtubeFetcher2 = context.spawn(YoutubeFetcherActor(kafka, ytbCfg2), "YoutubeFetcher_weifan")
      val youtubeFetcher3 = context.spawn(YoutubeFetcherActor(kafka, ytbCfg3), "YoutubeFetcher_xuanli")

      val newsFetcher1 = context.spawn(NewsFetcherActor(kafka, newsCfg1), "NewsFetcher_zijie")
      val newsFetcher2 = context.spawn(NewsFetcherActor(kafka, newsCfg2), "NewsFetcher_weifan")
      val newsFetcher3 = context.spawn(NewsFetcherActor(kafka, newsCfg3), "NewsFetcher_xuanli")

      
      Behaviors.empty
    }


    val system = ActorSystem[Nothing](rootBehavior, "System")

    println("Press ENTER to exit.")
    scala.io.StdIn.readLine() // 阻塞主线程，防止系统立即退出
    system.terminate()
  }
}
