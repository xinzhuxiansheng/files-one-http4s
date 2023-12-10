package com.yzhou.files.service

import cats.effect.Concurrent
import cats.syntax.all.*
import com.yzhou.files.model.{FileData, Result}
import com.yzhou.files.util.FileUtils
import fs2.Stream
import fs2.io.file.{Files, Flags, Path}

trait FileService[F[_]]:
  def getFiles(path: String, keyword: String, isHidden: Boolean): F[Result[List[FileData]]]

  def createFolder(path: String, folderName: String): F[Result[String]]

  def uploadFile(path: String, filename: String, fileBody: Stream[F, Byte]): F[Result[String]]


object FileService {

  def impl[F[_] : Concurrent : Files]: FileService[F] = new FileService[F]:
    def getFiles(path: String, keyword: String, isHidden: Boolean): F[Result[List[FileData]]] = {
      val result = FileUtils.queryFilesOrFolers(path, keyword, isHidden)
      result.fold(
        errorMessage => Result(400, None, errorMessage).pure[F],
        data => Result(200, Some(data), "Success").pure[F]
      )
    }

    def createFolder(path: String, folderName: String): F[Result[String]] = {
      val result = FileUtils.createFolder(path, folderName)
      result match {
        case Left(error) =>
          Result(400, None, error).pure[F]
        case Right(value) =>
          // 处理成功的情况
          Result(200, Some(value), value).pure[F]
      }
    }

    def uploadFile(path: String, filename: String, fileBody: Stream[F, Byte]): F[Result[String]] = {
      val destinationPath = Path.fromNioPath(java.nio.file.Paths.get(FileUtils.rootPath+"/"+path))
      val fullPath = destinationPath / filename
      // 创建 Flags
      val flags = Flags.Write
      fileBody
        .through(Files[F].writeAll(fullPath, flags))
        .compile
        .drain
        .attempt
        .map {
          case Right(_) => Result(200, None, "File uploaded successfully")
          case Left(e) => Result(400, None, e.getMessage)
        }
    }

}






