package com.yzhou.files

import cats.effect.{Async, Concurrent}
import cats.syntax.all.*
import com.comcast.ip4s.*
import com.yzhou.files.route.FileRoutes
import com.yzhou.files.service.FileService
import fs2.io.file.Files
import fs2.io.net.Network
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.server.middleware.Logger

object QuickstartServer:

  def run[F[_]: Async: Network: Concurrent: Files]: F[Nothing] = {
    for {
      client <- EmberClientBuilder.default[F].build
      helloWorldAlg = HelloWorld.impl[F]
      jokeAlg = Jokes.impl[F](client)
      fileAlg = FileService.impl[F]

      // Combine Service Routes into an HttpApp.
      // Can also be done via a Router if you
      // want to extract segments not checked
      // in the underlying routes.
      routes = QuickstartRoutes.helloWorldRoutes[F](helloWorldAlg) <+>
        QuickstartRoutes.jokeRoutes[F](jokeAlg) <+>
        FileRoutes.fileServiceRoutes[F](fileAlg)

      httpApp = Router("/api" -> routes).orNotFound

      // With Middlewares in place
      finalHttpApp = Logger.httpApp(true, true)(httpApp)

      _ <-
        EmberServerBuilder.default[F]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"8082")
          .withHttpApp(finalHttpApp)
          .build
    } yield ()
  }.useForever
