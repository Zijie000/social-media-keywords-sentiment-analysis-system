package edu.neu.coe.csye7200.burningcrusade.ingest

import java.time.{LocalDate, LocalDateTime}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import edu.neu.coe.csye7200.burningcrusade.ingest.YoutubeFetcherActor.{Config => YoutubeConfig}
//import edu.neu.coe.csye7200.burningcrusade.ingest.NewsFetcherActor.{Config => NewsConfig}
import edu.neu.coe.csye7200.burningcrusade.ingest.PrinterActor

object APISpawn {
  def main(args: Array[String]): Unit = {
    val rootBehavior = Behaviors.setup[Nothing] { context =>

      val printer = context.spawn(PrinterActor("data.txt"), "Printer")
    
      // 配置1：Twitter抓取
      val ytbCfg = YoutubeConfig(
        query = "trump",
        publishedAfter = LocalDate.now().minusDays(30).toString + "T00:00:00Z",
        publishedBefore = LocalDate.now().minusDays(1).toString + "T00:00:00Z",
        apiKey = "AIzaSyCGPEvV6aHJEZygEhiLuFBDs3oMAkOlAwI",
        maxResults = 3,
      )
      val twitterFetcher = context.spawn(YoutubeFetcherActor(printer, ytbCfg), "YoutubeFetcher")

      
      Behaviors.empty
    }

    //ActorSystem[Nothing](rootBehavior, "TwitterSystem")

    val system = ActorSystem[Nothing](rootBehavior, "TwitterSystem")

    println("Press ENTER to exit.")
    scala.io.StdIn.readLine() // 阻塞主线程，防止系统立即退出
    system.terminate()
  }
}
