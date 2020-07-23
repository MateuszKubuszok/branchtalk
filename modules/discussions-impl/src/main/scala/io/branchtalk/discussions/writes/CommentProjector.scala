package io.branchtalk.discussions.writes

import cats.data.NonEmptyList
import cats.effect.Sync
import doobie.Transactor
import fs2.Stream
import io.scalaland.chimney.dsl._
import io.branchtalk.discussions.events.{ CommentCommandEvent, CommentEvent, DiscussionCommandEvent, DiscussionEvent }
import io.branchtalk.shared.infrastructure.DoobieSupport._
import io.branchtalk.shared.infrastructure.Projector
import io.branchtalk.shared.models.UUID

final class CommentProjector[F[_]: Sync](transactor: Transactor[F])
    extends Projector[F, DiscussionCommandEvent, (UUID, DiscussionEvent)] {

  override def apply(in: Stream[F, DiscussionCommandEvent]): Stream[F, (UUID, DiscussionEvent)] =
    in.collect {
        case DiscussionCommandEvent.ForComment(event) => event
      }
      .evalMap[F, (UUID, CommentEvent)] {
        case event: CommentCommandEvent.Create  => toCreate(event).widen
        case event: CommentCommandEvent.Update  => toUpdate(event).widen
        case event: CommentCommandEvent.Delete  => toDelete(event).widen
        case event: CommentCommandEvent.Restore => toRestore(event).widen
      }
      .map {
        case (key, value) => key -> DiscussionEvent.ForComment(value)
      }

  def toCreate(event: CommentCommandEvent.Create): F[(UUID, CommentEvent.Created)] =
    event.replyTo
      .fold(0.pure[ConnectionIO]) { replyId =>
        sql"""
        SELECT nesting_level + 1
        FROM comments
        WHERE id = ${replyId}
      """.query[Int].option.map(_.getOrElse(0))
      }
      .flatMap { nestingLevel =>
        sql"""
          INSERT INTO comments (
            id,
            author_id,
            post_id,
            content,
            reply_to,
            nesting_level,
            created_at
          )
          VALUE (
            ${event.id},
            ${event.authorID},
            ${event.postID},
            ${event.content},
            ${event.replyTo},
            ${nestingLevel},
            ${event.createdAt}
          )
          ON CONFLICT DO NOTHING
        """.update.run
      }
      .transact(transactor) >>
      (event.id.value -> event.transformInto[CommentEvent.Created]).pure[F]

  def toUpdate(event: CommentCommandEvent.Update): F[(UUID, CommentEvent.Updated)] = {
    (NonEmptyList
      .of(
        event.newContent.toUpdateFragment(fr"content")
      )
      .sequence match {
      case Some(updates) =>
        (fr"UPDATE comments SET" ++
          updates.intercalate(fr",") ++
          fr", last_updated_at = ${event.modifiedAt} WHERE id = ${event.id}").update.run.transact(transactor).void
      case None =>
        ().pure[F]
    })
    (event.id.value -> event.transformInto[CommentEvent.Updated]).pure[F]
  }

  def toDelete(event: CommentCommandEvent.Delete): F[(UUID, CommentEvent.Deleted)] =
    sql"UPDATE comments SET deleted = TRUE WHERE id = ${event.id}".update.run.transact(transactor) >>
      (event.id.value -> event.transformInto[CommentEvent.Deleted]).pure[F]

  def toRestore(event: CommentCommandEvent.Restore): F[(UUID, CommentEvent.Restored)] =
    sql"UPDATE comments SET deleted = FALSE WHERE id = ${event.id}".update.run.transact(transactor) >>
      (event.id.value -> event.transformInto[CommentEvent.Restored]).pure[F]
}
