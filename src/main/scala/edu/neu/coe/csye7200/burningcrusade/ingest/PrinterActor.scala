package edu.neu.coe.csye7200.burningcrusade.ingest

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object PrinterActor {
  def apply(): Behavior[String] = Behaviors.receive { (_, message) =>
    println(s"[Tweet]: $message")
    Behaviors.same
  }
}
