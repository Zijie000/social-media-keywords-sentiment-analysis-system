package edu.neu.coe.csye7200.burningcrusade.ingest

import java.io.{FileWriter, BufferedWriter}
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object PrinterActor {
  def apply(filePath: String): Behavior[String] = Behaviors.setup { context =>
    // 打开文件 (追加模式)
    val bw = new BufferedWriter(new FileWriter(filePath, true))

    Behaviors.receiveMessage { message =>
      // 写入文件
      bw.write(s"[Tweet]: $message\n")
      bw.flush()
      // 不要关闭 bw，因为 Actor 可能一直要写
      Behaviors.same
    }
  }
}
