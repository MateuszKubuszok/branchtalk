package io.branchtalk.discussions.reads

import io.branchtalk.discussions.model.{ Channel, Post }
import io.branchtalk.shared.models.ID

trait PostReads[F[_]] {

  def exists(id: ID[Post]): F[Boolean]

  def getById(id: ID[Post]): F[Option[Post]]

  def requireById(id: ID[Post]): F[Post]
}
