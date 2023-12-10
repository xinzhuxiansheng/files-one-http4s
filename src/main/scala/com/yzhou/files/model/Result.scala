package com.yzhou.files.model

case class Result[T](code: Int, data: Option[T], msg: String)