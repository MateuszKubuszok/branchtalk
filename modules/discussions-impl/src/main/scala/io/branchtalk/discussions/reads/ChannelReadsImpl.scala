package io.branchtalk.discussions.reads

import cats.effect.Sync
import io.branchtalk.discussions.model.Channel
import io.branchtalk.shared.infrastructure.DoobieSupport._
import io.branchtalk.shared.models

final class ChannelReadsImpl[F[_]: Sync](transactor: Transactor[F]) extends ChannelReads[F] {

  private implicit val logHandler: LogHandler = doobieLogger(getClass)

  private val commonSelect: Fragment =
    fr"""SELECT id,
        |       url_name,
        |       name,
        |       description,
        |       created_at,
        |       last_modified_at
        |FROM channels""".stripMargin

  private def idExists(id: models.ID[Channel]): Fragment = fr"id = ${id} AND deleted = FALSE"

  override def exists(id: models.ID[Channel]): F[Boolean] =
    (fr"SELECT EXISTS(SELECT 1 FROM channels WHERE" ++ idExists(id) ++ fr")").query[Boolean].unique.transact(transactor)

  override def getById(id: models.ID[Channel]): F[Option[Channel]] =
    (commonSelect ++ fr"WHERE" ++ idExists(id)).query[Channel].option.transact(transactor)

  override def requireById(id: models.ID[Channel]): F[Channel] =
    (commonSelect ++ fr"WHERE" ++ idExists(id)).query[Channel].failNotFound("Channel", id).transact(transactor)
}
