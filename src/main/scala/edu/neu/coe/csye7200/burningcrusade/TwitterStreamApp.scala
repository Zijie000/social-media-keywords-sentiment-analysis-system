package edu.neu.coe.csye7200.burningcrusade

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import edu.neu.coe.csye7200.burningcrusade.ingest.TwitterFetcherActor
import edu.neu.coe.csye7200.burningcrusade.visualize.PrinterActor

object TwitterStreamApp {
  def main(args: Array[String]): Unit = {
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val printer = context.spawn(PrinterActor(), "Printer")
      val fetcher = context.spawn(TwitterFetcherActor(printer), "Fetcher")
      Behaviors.empty
    }

    ActorSystem[Nothing](rootBehavior, "TwitterSystem")
  }
}
