package com.yzhou.files

import cats.effect.unsafe.IORuntimeConfig
import cats.effect.{IO, IOApp}
import scala.concurrent.duration._

object Main extends IOApp.Simple:

  override protected def runtimeConfig: IORuntimeConfig =
    super.runtimeConfig.copy(cpuStarvationCheckInterval = 300.seconds)

  val run = QuickstartServer.run[IO]
