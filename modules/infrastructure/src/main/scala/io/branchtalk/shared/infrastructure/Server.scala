package io.branchtalk.shared.infrastructure

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.types.string.NonEmptyString
import eu.timepit.refined.pureconfig._
import pureconfig._
import pureconfig.generic.semiauto._

final case class Server(
  host: NonEmptyString,
  port: Int Refined Positive
)
object Server {

  implicit val configReader: ConfigReader[Server] = deriveReader[Server]
}
