package io.branchtalk.shared.models

import io.scalaland.catnip.Semi

@Semi(FastEq, ShowPretty) final case class CodePosition(
  file:    String,
  line:    Int,
  context: String
)
object CodePosition {

  implicit def providePosition(
    implicit file: sourcecode.File,
    line:          sourcecode.Line,
    enclosing:     sourcecode.Enclosing.Machine
  ): CodePosition =
    CodePosition(
      file    = file.value,
      line    = line.value,
      context = enclosing.value
    )
}
