package edu.neu.coe.csye7200.burningcrusade.train

import org.apache.spark.sql.SparkSession
import org.apache.spark.ml.{Pipeline, PipelineModel}
import org.apache.spark.ml.feature.{RegexTokenizer, StopWordsRemover, HashingTF, IDF, StringIndexer, CountVectorizer}
import org.apache.spark.ml.classification.LogisticRegression
import java.nio.file.Paths

/** 离线批量训练：
  *  ① Tokenizer → StopWords → CountVectorizer → IDF  生成向量化模型（vec）
  *  ② LogisticRegression 做多分类（lr）
  *  ③ 分别保存 vec/ 与 lr/ 目录，供流式推理直接 load&transform
  *
  * 用法：
  *   spark-submit ... OfflineTrainer <trainDataDir> <modelOutDir>
  *   目录结构举例：trainDataDir/politics/001.txt
  */
object OfflineTrainer {

  def main(args: Array[String]): Unit = {
    require(args.length == 2, "OfflineTrainer <inputDir> <modelDir>")
    val Array(inDir, modelDir) = args

    val spark = SparkSession.builder()
      .appName("Domain-Classifier-Trainer")
      .getOrCreate()
    import spark.implicits._

    // 1) 读取文本 + 标签（由父文件夹名推断）
    val pattern = Paths.get(inDir).toAbsolutePath.toString + "/*/*.txt"
    val raw = spark.sparkContext.wholeTextFiles(pattern).map { case (path, txt) =>
      val label = Paths.get(path).getParent.getFileName.toString
      (label, txt)
    }.toDF("label", "text").na.drop()

    // 2) 预处理 & 特征提取流水线（Tokenizer → StopWords → CV → IDF）
    val tokenizer = new RegexTokenizer()
      .setInputCol("text").setOutputCol("tokens").setPattern("\\W+")
    val sw        = new StopWordsRemover()
      .setInputCol("tokens").setOutputCol("filtered")
    val cv        = new CountVectorizer()
      .setInputCol("filtered").setOutputCol("tf").setVocabSize(1 << 18).setMinDF(2)
    val idf       = new IDF().setInputCol("tf").setOutputCol("features")

    val vecModel = new Pipeline().setStages(Array(tokenizer, sw, cv, idf)).fit(raw)

    // 3) 训练分类器
    val indexer   = new StringIndexer().setInputCol("label").setOutputCol("labelIdx").fit(raw)
    val trainDF   = indexer.transform(raw)               // 把 label 字符串映射到 idx
    val vecDF     = vecModel.transform(trainDF)          // 加 features

    val lrModel = new LogisticRegression()
      .setFeaturesCol("features").setLabelCol("labelIdx").setMaxIter(100)
      .fit(vecDF)

    // 4) 保存
    val out = Paths.get(modelDir).toAbsolutePath.toString
    vecModel.write.overwrite().save(s"$out/vec")
    lrModel .write.overwrite().save(s"$out/lr")
    indexer.write.overwrite().save(s"$out/indexer")      // 可选：将来用来自动 idx→label

    println(s"Vectorizer + LR 模型已保存至 $out")
    spark.stop()
  }
}
