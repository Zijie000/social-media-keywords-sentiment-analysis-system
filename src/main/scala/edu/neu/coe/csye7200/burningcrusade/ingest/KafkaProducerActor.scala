package edu.neu.coe.csye7200.burningcrusade.ingest

import akka.actor.typed.{Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import java.util.Properties

object KafkaProducerActor:

  /** 封装要写入 Kafka 的消息 */
  final case class Publish(text: String)

  def apply(topic: String, keyword: String, bootstrap: String): Behavior[Publish] =
    Behaviors.setup { ctx =>
      // 1) 创建 Java KafkaProducer[String,String]
      val props = new Properties()
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer")
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer")
      val producer = new KafkaProducer[String, String](props)

      ctx.log.info(s"KafkaProducerActor started, writing to topic '$topic'")

      Behaviors
        .receiveMessage[Publish] { msg =>
          val record = new ProducerRecord[String, String](topic, keyword, msg.text)
          producer.send(record)          // 异步；可加回调
          Behaviors.same
        }
        .receiveSignal {
          case (_, PostStop) =>
            ctx.log.info("Closing KafkaProducer")
            producer.close()              // 确保资源释放
            Behaviors.same
        }
    }
