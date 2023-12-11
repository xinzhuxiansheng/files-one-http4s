package com.yzhou.files.route

import cats.Applicative
import cats.effect.*
import cats.syntax.all.*
import com.yzhou.files.model.Result
import com.yzhou.files.service.FileService
import com.yzhou.files.util.FileUtils
import fs2.io.file.{Files, Path as Fs2Path}
import io.circe.*
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{`Content-Disposition`, `Content-Type`}
import org.http4s.multipart.{Multipart, Part}
import org.typelevel.ci.CIString

import java.io.File
import scala.util.Try


case class CreateFolderParams(name: String, path: String)
case class DeleteResourceParams(resourceName: String, path: String)

case class FileDesc(name: String, fullName: String, isDic: Boolean, file: File)

object FileRoutes :
  def fileServiceRoutes[F[_] : Concurrent : Async: Applicative: Files](fileService: FileService[F]): HttpRoutes[F] =
    val dsl = new Http4sDsl[F] {}
    import dsl.*
    import org.http4s.circe.CirceEntityEncoder.*
    // 导入 Circe 自动解码器，以便将 JSON 请求解码为 CreateFolderParams 实例
    implicit val createFolderParamsDecoder: EntityDecoder[F, CreateFolderParams] = jsonOf[F, CreateFolderParams]
    implicit val deleteResourceParamsDecoder: EntityDecoder[F, DeleteResourceParams] = jsonOf[F, DeleteResourceParams]

    HttpRoutes.of[F] {
      case req@GET -> Root / "getFiles" =>
        val queryParams = req.uri.query.params
        val path = queryParams.getOrElse("path", "")
        val keyword = queryParams.getOrElse("keyword", "")
        val isHidden = queryParams.get("isHidden").flatMap(s => Try(s.toBoolean).toOption).getOrElse(false)
        for {
          list <- fileService.getFiles(path, keyword, isHidden)
          resp <- Ok(list)
        } yield resp

      case req@POST -> Root / "createFolder" =>
        for {
          params <- req.as[CreateFolderParams]
          result <- fileService.createFolder(params.path, params.name)
          resp <- Ok(result)
        } yield resp

      case req@POST -> Root / "upload" =>
        (for {
          // 解析多部分请求
          multipart <- req.as[Multipart[F]]
          filePartOption = multipart.parts.find(p => p.name.contains("file"))
          pathPartOption = multipart.parts.find(p => p.name.contains("path"))

          response <- (pathPartOption, filePartOption) match {
            case (Some(pathPart), Some(filePart: Part[F])) => // 确保 filePart 是 Part[F] 类型
              // 提取 path 参数
              pathPart.bodyText.compile.string.flatMap { path =>
                // 调用文件服务上传文件
                val saveResult = fileService.uploadFile(path, filePart.filename.getOrElse("unnamed"), filePart.body)
                Ok(saveResult)
              }
            case (None, _) =>
              // 如果没有找到 path 字段，返回错误响应
              BadRequest("请求中未包含 path 字段")

            case (_, None) =>
              // 如果没有文件部分，返回错误响应
              BadRequest("请求中未包含文件部分")
          }
        } yield response).handleErrorWith {
          // 处理发生的异常
          case e: Exception =>
            InternalServerError(s"服务器错误: ${e.getMessage}")
        }

      case req@POST -> Root / "delete" =>
        for {
          params <- req.as[DeleteResourceParams]
          result <- fileService.deleteResource(params.resourceName, params.path)
          resp <- Ok(result)
        } yield resp

      case req@GET -> Root / "download"  =>
        val queryParams = req.uri.query.params
        val fileName = queryParams.getOrElse("name", "")
        val fs2FilePath = Fs2Path(FileUtils.rootPath+"/" + fileName)

        Files[F].exists(fs2FilePath).flatMap {
          case true =>
            val fileStream = Files[F].readAll(fs2FilePath)
            Ok(fileStream)
              .map(_.withContentType(`Content-Type`(MediaType.application.`octet-stream`)))
              .map(_.putHeaders(`Content-Disposition`("attachment", Map(CIString("filename") -> fileName))))
          case false =>
            NotFound(s"File not found: $fileName")
        }

    }


