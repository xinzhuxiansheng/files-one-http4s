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

  def deleteResource(resourceName: String, path: String): F[Result[String]]

  def isDirectory(name: String): F[Result[Boolean]]
  def checkDirectory(path: String): F[Result[String]]

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
      val destinationPath = Path.fromNioPath(java.nio.file.Paths.get(FileUtils.rootPath + "/" + path))
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

    def deleteResource(resourceName: String, path: String): F[Result[String]] = {
      val resourcePath = Path.fromNioPath(java.nio.file.Paths.get(FileUtils.rootPath + "/" + path + "/" + resourceName))
      Files[F].isDirectory(resourcePath).flatMap {
        case true =>
          // 是文件夹，递归删除文件夹及其内容
          deleteDirectoryRecursively(resourcePath)
        case false =>
          // 是文件，直接删除
          Files[F].deleteIfExists(resourcePath).attempt.map {
            case Right(_) => Result(200, Some("File deleted successfully"), "Success")
            case Left(e) => Result(400, None, e.getMessage)
          }
      }
    }

    private def deleteDirectoryRecursively(path: Path)(implicit F: Concurrent[F]): F[Result[String]] = {
      Files[F].walk(path)
        .evalMap(p => Files[F].deleteIfExists(p).attempt)
        .compile
        .drain
        .attempt
        .flatMap {
          case Right(_) =>
            // 删除文件夹内容后，删除文件夹本身
            Files[F].deleteIfExists(path).attempt.map {
              case Right(_) => Result(200, Some("Directory deleted successfully"), "Success")
              case Left(e) => Result(400, None, e.getMessage)
            }
          case Left(e) =>
            Result(400, None, e.getMessage).pure[F]
        }
    }

    def isDirectory(name: String): F[Result[Boolean]] = {
      val fs2Path = Path.fromNioPath(java.nio.file.Paths.get(FileUtils.rootPath + "/" + name))
      Files[F].exists(fs2Path).flatMap {
        case true =>
          Files[F].isDirectory(fs2Path).attempt.map {
            case Right(isDir) => Result(200, Some(isDir), if (isDir) "路径是一个目录" else "路径不是目录")
            case Left(e) => Result(500, None, e.getMessage)
          }
        case false =>
          Result(404, None, "路径不存在").pure[F]
      }
    }

    def checkDirectory(path: String): F[Result[String]] = {
      // 验证路径是否以 rootPath 开始
      if (!path.startsWith(FileUtils.rootPath)) {
        Result(400, None, "路径不合法").pure[F]
      } else {
        val fs2Path = Path.fromNioPath(java.nio.file.Paths.get(path))
        Files[F].exists(fs2Path).flatMap {
          case true =>
            Files[F].isDirectory(fs2Path).flatMap {
              case true =>
                // 是文件夹
                Result(200, Some(path.drop(FileUtils.rootPath.length)), "是文件夹").pure[F]
              case false =>
                // 不是文件夹
                Result(400, None, "不是文件夹").pure[F]
            }
          case false =>
            // 路径不存在
            Result(400, None, "路径不存在").pure[F]
        }
      }
    }

}






