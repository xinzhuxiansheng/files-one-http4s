package com.yzhou.files.config

import com.typesafe.config.ConfigFactory

object AppConfig {
  private val env = System.getProperty("env", "dev") // 如果没有指定，默认为 "dev"
  private val config = ConfigFactory.load(s"application-$env.conf")
  // 文件根目录
  val rootPath: String = config.getString("app.rootpath")

}
