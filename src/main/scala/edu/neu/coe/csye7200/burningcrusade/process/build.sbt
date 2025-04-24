// ------------ build.sbt (根目录同 level‑2 项目) -------------
ThisBuild / scalaVersion := "2.12.15"

lazy val sparkVersion = "3.5.1"          // 和集群保持一致

// ============== 依赖 =====================
libraryDependencies ++= Seq(
  // Spark（% "provided" 表示运行时由 spark-submit 提供，不打进 fat‑jar）
  "org.apache.spark" % "spark-core_2.12"                % sparkVersion,
  "org.apache.spark" % "spark-sql_2.12"                 % sparkVersion,
  "org.apache.spark" % "spark-streaming_2.12"           % sparkVersion,
  "org.apache.spark" % "spark-mllib_2.12"               % sparkVersion,
  "org.apache.spark" %  "spark-streaming-kafka-0-10_2.12" % sparkVersion,

  "com.softwaremill.sttp.client3" %% "core" % "3.8.15",

  // JSON parsing
  //"org.json4s" %% "json4s-native" % "4.0.6",

  //"org.json4s" %% "json4s-jackson" % "3.6.7",

  //"org.apache.kafka" % "kafka-clients" % "3.7.0",

  "org.json4s" %% "json4s-core"    % "3.6.7" % Provided,
  "org.json4s" %% "json4s-jackson" % "3.6.7" % Provided,
  

  // PostgreSQL JDBC Driver
  "org.postgresql"  % "postgresql"                 % "42.7.3"
)

assembly / assemblyMergeStrategy := {
  // 丢弃所有 module-info.class 文件（各个库各自的 module-info 都不需要）
  case PathList(ps @ _*) if ps.last == "module-info.class" =>
    MergeStrategy.discard

  // META-INF 下 versions 目录里的所有文件取第一个即可
  case PathList("META-INF", "versions", _ @ _*) =>
    MergeStrategy.first

  // 你之前的那些规则（.proto、protobuf、commons-logging、netty、Log4j2Plugins.dat、reference.conf 等）
  case PathList("google", "protobuf", _ @ _*)            => MergeStrategy.first
  case PathList("org", "apache", "commons", "logging", _ @ _*) => MergeStrategy.first
  case PathList("arrow-git.properties")                  => MergeStrategy.first
  case PathList("META-INF", "io.netty.versions.properties")  => MergeStrategy.first
  case PathList("META-INF", "org", "apache", "logging", "log4j", "core", "config", "plugins", "Log4j2Plugins.dat") =>
    MergeStrategy.first
  case PathList(ps @ _*) if ps.last.endsWith(".proto")   => MergeStrategy.first
  case "reference.conf"                                  => MergeStrategy.concat

  // 默认其它资源
  case x                                                 => MergeStrategy.defaultMergeStrategy(x)
}
