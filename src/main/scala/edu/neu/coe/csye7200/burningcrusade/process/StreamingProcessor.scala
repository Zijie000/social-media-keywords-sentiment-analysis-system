package edu.neu.coe.csye7200.burningcrusade.process

import org.apache.spark.sql.{SparkSession, DataFrame, functions => F}
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.ml.{Pipeline, PipelineModel}
import org.apache.spark.ml.feature.{RegexTokenizer, StopWordsRemover, HashingTF, IDF, StringIndexer}
import org.apache.spark.ml.classification.LogisticRegressionModel
import org.apache.spark.ml.feature.{CountVectorizerModel}
//import edu.neu.coe.csye7200.burningcrusade.ingest.JsonSttpImplicits.*
import java.time.Instant
import java.util.UUID

import sttp.client3._
import org.json4s._
//import org.json4s.{StringInput, Formats, AsJsonInput}
//import org.json4s.native.JsonMethods._
import org.json4s.jackson.JsonMethods._
import org.json4s.jvalue2monadic

object StreamingProcessor {

  /* ====== Hugging-Face 情感分析 UDF ====== */
  private val hfEndpoint = uri"https://oul3scdwy5xss3bs.us-east-1.aws.endpoints.huggingface.cloud"
  private val hfToken    = ""

  implicit val sttpBackend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  implicit val jsonFormats: Formats = DefaultFormats

  private def jsonEscape(str: String): String = {

    str.flatMap {
      case '\\'             => "\\\\"
      case '\"'             => "\\\""
      case '\n'             => "\\n"
      case '\r'             => "\\r"
      case '\t'             => "\\t"
      case '\b'             => "\\b"
      case '\f'             => "\\f"
      // 0x00–0x1F 其余不可见控制字符
      case c if c < 0x20    => f"\\u${c.toInt}%04x"
      case c                => c.toString
    }

  }
    

  private def inferSentiment(text: String): (String, Double, Int) = {
    if (text == null || text.isBlank) return ("neutral", 0.0, 0)

    //val snippet = text.trim.take(512)
                      //.replace("\\", "\\\\")
                      //.replace("\"", "\\\"")
    val snippet = jsonEscape(text.trim.take(512))
    //val payload = Serialization.write(Map("inputs" -> text.take(512)))
    val payload = s"""{"inputs":"$snippet"}"""

    val startNs = System.nanoTime()

    val resp = basicRequest
      .post(hfEndpoint)
      .header("Authorization", s"Bearer $hfToken")
      .contentType("application/json")            // ← 修正点
      .header("Accept", "application/json")
      .body(payload)
      .response(asStringAlways)
      .send()

    val ms = ((System.nanoTime() - startNs) / 1e6).toInt  // 耗时

    if (!resp.code.isSuccess) {
      println(s"[HF] HTTP ${resp.code}: ${resp.body.take(120)} …")
      return ("neutral", 0.0, ms)
    }

    val json = parse(resp.body)

    val maybeObj: Option[JObject] = json match {
      case JArray(JArray(JObject(obj) :: _) :: _) => Some(JObject(obj)) // [[{…}]]
      case JArray(JObject(obj) :: _)              => Some(JObject(obj)) // [{…}]
      case _                                      => None
    }

    /* ---------- ② 根据是否解析到结果决定返回 ------------------------ */
    maybeObj match {
      case Some(obj) =>
        val label = (obj \ "label").extractOrElse("neutral")
        val score = (obj \ "score").extractOrElse(0.0)
        (label, score, ms)

      case None =>
        // 可能是 {"error": "..."} 或空数组 []
        (json \ "error") match {
          case JString(err) =>
            println(s"[HF] logic error: $err")
          case _ =>
            println(s"[HF] unknown payload: ${resp.body.take(120)} …")
        }
        ("neutral", 0.0, ms)
    }
  }

  /* ====== TF-IDF 关键词辅助 UDF ====== */
  private def topTerms(vocab: Array[String], k: Int) = F.udf {
    vec: org.apache.spark.ml.linalg.SparseVector =>
      vec.indices.zip(vec.values).sortBy(-_._2).take(k).map { case (idx, _) => vocab(idx) }
  }

  /* ============ MAIN ============ */
  def main(args: Array[String]): Unit = {

    /* 0) Spark 会话 */
    val spark = SparkSession.builder()
      .appName("Sentiment+Category Streaming")
      .config("spark.sql.shuffle.partitions", "4")
      .getOrCreate()
    import spark.implicits._

    /* 1) 载入离线模型 */
    val modelRoot = "/Users/zijiezhou/Documents/csye7200/social-media-keywords-sentiment-analysis-system/src/main/scala/edu/neu/coe/csye7200/burningcrusade/train/model"          // <<< 改成你的模型根目录
    val vecModel  = PipelineModel.load(s"$modelRoot/vec")
    val lrModel   = LogisticRegressionModel.load(s"$modelRoot/lr")

    // 取 CountVectorizerModel 拿 vocabulary
    val cvModel = vecModel.stages.collectFirst {
      case m: CountVectorizerModel => m
    }.getOrElse(throw new IllegalStateException("CountVectorizerModel not found"))
    val vocabArr = cvModel.vocabulary

    // 若保存了 indexer，也可自动映射 idx→label
    //val idxToLabelBr = spark.sparkContext.broadcast(Map(
      //0.0 -> "economy", 1.0 -> "entertainment",
      //2.0 -> "politics",3.0 -> "sports",4.0 -> "technology"
    //))


    /* 2) Kafka Source */
    val kafka = spark.readStream.format("kafka")
      .option("kafka.bootstrap.servers","localhost:19092")
      .option("subscribe","social-text")
      .option("startingOffsets","latest")
      .load()

    val raw = kafka.selectExpr("CAST(value AS STRING) AS text", "timestamp")

    val sentimentUDF = F.udf(inferSentiment _)

    /* 3) 情感分析 */
    val withSentiment = raw.withColumn("sentiment", sentimentUDF(F.col("text")))
      .withColumn("sentiment_label", F.col("sentiment").getField("_1"))
      .withColumn("sentiment_score", F.col("sentiment").getField("_2"))
      .drop("sentiment")

    /* 4) 特征提取 + 分类 */
    val featurized = vecModel.transform(withSentiment)


    val predicted  = lrModel.transform(featurized)
      .withColumnRenamed("prediction","category_idx")



    //val toLabel = F.udf((d: Double) => labelsArr.value.getOrElse(d,"unknown"))
    //val labeled = predicted.withColumn("category", toLabel(F.col("category_idx")))

    import org.apache.spark.ml.feature.StringIndexerModel
    val idxModel = StringIndexerModel.load(s"$modelRoot/indexer") // modelRoot 与 vec/lr 同级
    val labels   = idxModel.labels                                // Array[String]

    val toLabel  = F.udf((d: Double) =>
      if (d >= 0 && d < labels.length) labels(d.toInt) else "unknown"
    )

    val labeled = predicted.withColumn("category", toLabel(F.col("category_idx")))

    /* 5) 关键词抽取 */
    val withKw = labeled.withColumn(
      "keywords",
      topTerms(vocabArr, 3)(F.col("features"))
    )

    /* 6) Sink → PostgreSQL */
    val finalDF = withKw.select(
      F.lit(UUID.randomUUID().toString).alias("id"),
      F.col("timestamp").alias("event_time"),
      F.col("text"), F.col("sentiment_label"), F.col("sentiment_score"),
      F.col("category"), F.col("keywords")
    )

    

    val jdbcUrl = "jdbc:postgresql://localhost:5432/streaming_db"

    def writeToPG(batch: DataFrame, id: Long): Unit = {
      val n = batch.count      // ← 仅调试用，正式跑删掉
      println(s"+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++[foreachBatch] batchId=$id, rows=$n")
      batch.write.mode("append").format("jdbc")
        .option("url", jdbcUrl)
        .option("dbtable","public.sentiment_stream")
        .option("user","spark").option("password","sparkpw")
        .option("driver",    "org.postgresql.Driver")
        .save()
    }

    finalDF.writeStream
      .foreachBatch { (batch: DataFrame, id: Long) =>    // ★ 类型标注
        val n = batch.count()
        println(s"### [batch=$id] rows=$n")
        batch.show(10, truncate = false)

        if (n > 0) {
          batch.write
            .mode("append")
            .format("jdbc")
            .option("url",      "jdbc:postgresql://localhost:5432/streaming_db")
            .option("dbtable",  "public.sentiment_stream")
            .option("user",     "spark")
            .option("password", "sparkpw")
            .option("driver",   "org.postgresql.Driver")
            .save()
          println(s"### [batch=$id] JDBC OK ✓")
        }
      }
      .option("checkpointLocation", s"/tmp/spark/ckpt_${System.currentTimeMillis}") 
      .trigger(Trigger.ProcessingTime("10 seconds"))
      .start()
      .awaitTermination()


  
  }
}