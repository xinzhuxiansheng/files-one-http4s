package com.yzhou.files.config

import com.typesafe.config.ConfigFactory

object AppConfig {
  private val config = ConfigFactory.load()
  // 文件根目录
  val rootPath: String = config.getString("app.rootpath")

}
