package com.yzhou.files.model

case class FileData(id: Int, name: String, fType: String, fSize: Long, fSizeDesc: String
                    , internalPath: String,
                    isHidden: Boolean,
                    modificationTime: Long,
                    modificationTimeDesc: String)
