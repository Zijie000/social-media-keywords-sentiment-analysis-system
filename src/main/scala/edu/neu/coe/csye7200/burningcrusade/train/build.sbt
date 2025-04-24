// ------------ build.sbt (根目录同 level‑2 项目) -------------
ThisBuild / scalaVersion := "2.12.15"

lazy val sparkVersion = "3.5.1"          // 和集群保持一致

// 在 build.sbt 顶部或适当位置加上：
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.MergeStrategy
import sbtassembly.PathList

// ============== 依赖 =====================
libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core"                % sparkVersion,
  "org.apache.spark" %% "spark-sql"                 % sparkVersion,
  "org.apache.spark" %% "spark-mllib"               % sparkVersion
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
