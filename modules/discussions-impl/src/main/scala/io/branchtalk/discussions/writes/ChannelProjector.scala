package io.branchtalk.discussions.writes

import cats.data.NonEmptyList
import cats.effect.Sync
import fs2.Stream
import io.scalaland.chimney.dsl._
import io.branchtalk.discussions.events.{ ChannelCommandEvent, ChannelEvent, DiscussionCommandEvent, DiscussionEvent }
import io.branchtalk.shared.infrastructure.DoobieSupport._
import io.branchtalk.shared.infrastructure.Projector
import io.branchtalk.shared.models.UUID

final class ChannelProjector[F[_]: Sync](transactor: Transactor[F])
    extends Projector[F, DiscussionCommandEvent, (UUID, DiscussionEvent)] {

  private implicit val logHandler: LogHandler = doobieLogger(getClass)

  override def apply(in: Stream[F, DiscussionCommandEvent]): Stream[F, (UUID, DiscussionEvent)] =
    in.collect {
        case DiscussionCommandEvent.ForChannel(event) => event
      }
      .evalMap[F, (UUID, ChannelEvent)] {
        case event: ChannelCommandEvent.Create  => toCreate(event).widen
        case event: ChannelCommandEvent.Update  => toUpdate(event).widen
        case event: ChannelCommandEvent.Delete  => toDelete(event).widen
        case event: ChannelCommandEvent.Restore => toRestore(event).widen
      }
      .map {
        case (key, value) => key -> DiscussionEvent.ForChannel(value)
      }

  def toCreate(event: ChannelCommandEvent.Create): F[(UUID, ChannelEvent.Created)] =
    sql"""
      INSERT INTO channels (
        id,
        author_id,
        url_name,
        name,
        description,
        created_at
      )
      VALUE (
        ${event.id},
        ${event.authorID},
        ${event.urlName},
        ${event.name},
        ${event.description},
        ${event.createdAt}
      )
      ON CONFLICT DO NOTHING
    """.update.run.transact(transactor) >>
      (event.id.value -> event.transformInto[ChannelEvent.Created]).pure[F]

  def toUpdate(event: ChannelCommandEvent.Update): F[(UUID, ChannelEvent.Updated)] =
    (NonEmptyList
      .of(
        event.urlName.toUpdateFragment(fr"url_name"),
        event.name.toUpdateFragment(fr"name"),
        event.description.toUpdateFragment(fr"description")
      )
      .sequence match {
      case Some(updates) =>
        (fr"UPDATE channels SET" ++
          updates.intercalate(fr",") ++
          fr", last_updated_at = ${event.modifiedAt} WHERE id = ${event.id}").update.run.transact(transactor).void
      case None =>
        ().pure[F]
    }) >>
      (event.id.value -> event.transformInto[ChannelEvent.Updated]).pure[F]

  def toDelete(event: ChannelCommandEvent.Delete): F[(UUID, ChannelEvent.Deleted)] =
    sql"UPDATE channels SET deleted = TRUE WHERE id = ${event.id}".update.run.transact(transactor) >>
      (event.id.value -> event.transformInto[ChannelEvent.Deleted]).pure[F]

  def toRestore(event: ChannelCommandEvent.Restore): F[(UUID, ChannelEvent.Restored)] =
    sql"UPDATE channels SET deleted = FALSE WHERE id = ${event.id}".update.run.transact(transactor) >>
      (event.id.value -> event.transformInto[ChannelEvent.Restored]).pure[F]
}
