package com.yzhou.files.util

import cats.effect.IO
import com.yzhou.files.config.AppConfig
import com.yzhou.files.model.FileData
import com.yzhou.files.route.FileDesc
import com.yzhou.files.util.StringUtils.isNullOrBlank
import org.joda.time.DateTime
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.io.{BufferedInputStream, File, FileInputStream, FileOutputStream}
import java.util.zip.{ZipEntry, ZipOutputStream}

object FileUtils {
  private val logger = Slf4jLogger.getLogger[IO]
  val rootPath = AppConfig.rootPath

  def queryFilesOrFolers(path: String, keyword: String, isHidden: Boolean): Either[String, List[FileData]] = {
    try {
      logger.info(s"queryFilesOrFolers path:${path}, keyword:${keyword}")
      var directory = path
      if (isNullOrBlank(directory) || directory == "/")
        directory = rootPath
      else
        directory = s"$rootPath$path"

      val dir = new File(directory)

      val filterList = dir.listFiles().filter(f => {
        var iskeywordF = true
        var ishiddenF = true

        if (!isNullOrBlank(keyword) && !f.getName.contains(keyword))
          iskeywordF = false

        if (isHidden && f.isHidden)
          ishiddenF = false
        iskeywordF && ishiddenF
      }).toList

      // f.getPath 是绝对路径
      var items = filterList.zipWithIndex
        .map {
          case (f, index) => {
            FileData(
              index,
              f.getName,
              getFileType(f),
              f.length(),
              formatSizeUnits(f.length()),
              f.getAbsolutePath,
              f.isHidden,
              f.lastModified(),
              longTime2String(f.lastModified()))
          }
        }
      Right(items)
    } catch {
      case exception: Exception => Left("查询接口异常")
    }
  }

    def createFolder(currentPath: String, newFolderName: String): Either[String, String] = {
      try {
        logger.info(s"createFolder currentPath:${currentPath}, newFolderName:${newFolderName}")
        var directory = currentPath
        if (StringUtils.isNullOrBlank(directory) || directory == "/")
          directory = rootPath + "/" + newFolderName;
        else
          directory = rootPath + directory + "/" + newFolderName

        val dir = new File(directory);
        if (!dir.exists()) {
          dir.mkdir()
          Right("目录创建成功!")
        } else {
          Left("目录已存在")
        }
      } catch {
        case exception: Exception => Left("查询接口异常")
      }
    }

  //  def upload(filePart: MultipartFormData.FilePart[Files.TemporaryFile], currentPath: String): (Boolean, String) = {
  //    try {
  //      val targetFile = new File(s"$rootPath$currentPath/${filePart.filename}")
  //      if (targetFile.exists()) {
  //        return (false, "当前路径下已存在同名文件，无法上传")
  //      }
  //      filePart.ref.moveTo(new File(s"$rootPath$currentPath/${filePart.filename}"))
  //      (true, "OK")
  //    } catch {
  //      case exception: Exception => (false, "上传文件异常")
  //    }
  //  }

  //  def deleteResource(resourceName: String, currentPath: String): (Boolean, String) = {
  //    val resourcePath = s"$rootPath$currentPath/$resourceName";
  //    val resource = new File(resourcePath)
  //    if (resource.exists()) {
  //      if (resource.isDirectory) {
  //        deleteDirectory(resource);
  //      } else {
  //        if (resource.isFile) {
  //          resource.delete()
  //        }
  //      }
  //      (true, "OK")
  //    } else {
  //      (false, "当前目录或者资源不存在")
  //    }
  //  }

  def downloadResource(currentPath: String, resourceName: String): FileDesc = {
    val resourcePath = s"$rootPath$currentPath/$resourceName"
    val resourceFile = new File(resourcePath)
    //      if(resourceFile.exists()){
    if (resourceFile.isDirectory) {
      val newZipResourcePath = s"$resourcePath.zip"
      val zipFile = new File(newZipResourcePath)
      val zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile))
      zipFiles(resourceFile.listFiles, "", zipOutputStream)
      zipOutputStream.close()
      FileDesc(resourceName, s"$resourceName.zip", true, zipFile)
    } else {
      FileDesc(resourceName, resourceName, false, resourceFile)
    }
    //      }
  }

  private def zipFiles(files: Array[File], path: String, zipOutputStream: ZipOutputStream): Unit = {
    files.foreach { file =>
      if (file.isDirectory) {
        zipFiles(file.listFiles, path + file.getName + "/", zipOutputStream)
      } else {
        val entry = new ZipEntry(path + file.getName)
        zipOutputStream.putNextEntry(entry)
        val in = new BufferedInputStream(new FileInputStream(file))
        var b = in.read()
        while (b > -1) {
          zipOutputStream.write(b)
          b = in.read()
        }
        in.close()
      }
    }
  }

  /**
   * long类型时间转 字符串
   */
  def longTime2String(timestamp: Long): String = {
    val time: DateTime = new DateTime(timestamp)
    return time.toString("yyyy-MM-dd HH:mm:ss")
  }

  def getFileType(file: File): String = {
    if (file.isDirectory) {
      return "folder"
    }
    val fileName = file.getName
    val dotIndex = fileName.lastIndexOf(".")
    if (dotIndex > 0) {
      val extension = fileName.substring(dotIndex + 1)
      extension match {
        case "jpg" => "image" //<FileImageOutlined />
        case "png" => "image" //
        case "pdf" => "pdf" //<FilePdfOutlined />
        case "doc" => "word" //<FileWordOutlined />
        case "xls" => "excel" //<FileExcelOutlined />
        case "xlsx" => "excel" //<FileExcelOutlined />
        case "ppt" => "ppt" //<FilePptOutlined />
        case "md" => "md" //<FileMarkdownOutlined />
        case "zip" => "yasuobao" //<FileZipOutlined />
        case "tgz" => "yasuobao" //<FileZipOutlined />
        case _ => "txt" //<FileTextOutlined />
      }
    } else {
      "txt" //<FileTextOutlined />
    }
  }

  //  def deleteDirectory(directory: File): Unit = {
  //    val files = directory.listFiles()
  //    if (files != null) {
  //      for (file <- files) {
  //        if (file.isDirectory()) {
  //          deleteDirectory(file)
  //        } else {
  //          file.delete()
  //        }
  //      }
  //    }
  //    directory.delete()
  //  }

  def formatSizeUnits(bytes: Long): String = {
    val units = Seq("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble
    var i = 0
    while (size >= 1024 && i < units.length - 1) {
      size /= 1024
      i += 1
    }
//    val sizeFor = "%.1f".format(size)
    s"${"%.1f".format(size)} ${units(i)}"
  }

}
