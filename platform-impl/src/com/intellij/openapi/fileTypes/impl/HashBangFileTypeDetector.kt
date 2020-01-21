// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * @author yole
 */
open class HashBangFileTypeDetector @JvmOverloads constructor(
  val fileType: FileType,
  val marker: String,
  val _version: Int = 1
) : FileTypeRegistry.FileTypeDetector {
  override fun detect(file: VirtualFile, firstBytes: ByteSequence, firstCharsIfText: CharSequence?): FileType? {
    return if (FileUtil.isHashBangLine(firstCharsIfText, marker)) fileType else null
  }

  override fun getDesiredContentPrefixLength(): Int {
    // Maximum length of shebang varies for different OSes (https://www.in-ulm.de/~mascheck/various/shebang/#results).
    // On macOS, its 512.
    // On vast majority of Linux systems, a restriction of 127 bytes of shebang length is compiled into kernel.
    // See "#define BINPRM_BUF_SIZE 128" in /usr/include/linux/binfmts.h (127 + '0' as the string terminator).

    // Let's limit its maximum length to 256 which allows file type detection for most cases.
    // In future, it can be reduced for performance sake.
    return 256
  }

  override fun getVersion() = _version
}
