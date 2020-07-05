package io.subfibers.discussions

import cats.effect.{ ConcurrentEffect, ContextShift, Resource, Timer }
import io.subfibers.discussions.events.{ DiscussionEvent, DiscussionInternalEvent }
import io.subfibers.discussions.infrastructure._
import io.subfibers.shared.infrastructure._
import io.subfibers.shared.models._

final case class DiscussionsModule[F[_]](
  commentRepository: CommentRepository[F],
  postRepository:    PostRepository[F],
  eventConsumer:     EventBusSubscriber[F, UUID, DiscussionEvent]
)
object DiscussionsModule extends DomainModule[DiscussionEvent, DiscussionInternalEvent] {

  def apply[F[_]: ConcurrentEffect: ContextShift: Timer](
    domainConfig: DomainConfig
  ): Resource[F, DiscussionsModule[F]] =
    setupInfrastructure[F](domainConfig).map {
      case Infrastructure(transactor, internalPublisher, _, _, consumer) =>
        val commentRepository: CommentRepository[F] = new CommentRepositoryImpl[F](transactor, internalPublisher)
        val postRepository:    PostRepository[F]    = new PostRepositoryImpl[F](transactor, internalPublisher)
        DiscussionsModule(commentRepository, postRepository, consumer)
    }
}
