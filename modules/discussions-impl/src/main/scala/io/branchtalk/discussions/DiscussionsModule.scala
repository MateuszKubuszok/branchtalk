package io.branchtalk.discussions

import cats.data.NonEmptyList
import cats.effect.{ ConcurrentEffect, ContextShift, Resource, Timer }
import io.branchtalk.discussions.events.{ DiscussionCommandEvent, DiscussionEvent }
import io.branchtalk.discussions.reads._
import io.branchtalk.discussions.writes._
import io.branchtalk.shared.models._
import io.branchtalk.shared.infrastructure._
import _root_.io.branchtalk.discussions.reads.ChannelReads
import com.softwaremill.macwire.wire
import com.typesafe.scalalogging.Logger

final case class DiscussionsReads[F[_]](
  channelReads:      ChannelReads[F],
  postReads:         PostReads[F],
  commentReads:      CommentReads[F],
  subscriptionReads: SubscriptionReads[F]
)

final case class DiscussionsWrites[F[_]](
  commentWrites:      CommentWrites[F],
  postWrites:         PostWrites[F],
  channelWrites:      ChannelWrites[F],
  subscriptionWrites: SubscriptionWrites[F],
  runProjector:       Resource[F, F[Unit]]
)

object DiscussionsModule {

  private val module = DomainModule[DiscussionEvent, DiscussionCommandEvent]
  private val logger = Logger(getClass)

  def reads[F[_]: ConcurrentEffect: ContextShift: Timer](
    domainConfig: DomainConfig
  ): Resource[F, DiscussionsReads[F]] =
    module.setupReads[F](domainConfig).map {
      case ReadsInfrastructure(transactor, _) =>
        val channelReads:      ChannelReads[F]      = wire[ChannelReadsImpl[F]]
        val postReads:         PostReads[F]         = wire[PostReadsImpl[F]]
        val commentReads:      CommentReads[F]      = wire[CommentReadsImpl[F]]
        val subscriptionReads: SubscriptionReads[F] = wire[SubscriptionReadsImpl[F]]

        wire[DiscussionsReads[F]]
    }

  def writes[F[_]: ConcurrentEffect: ContextShift: Timer](
    domainConfig:           DomainConfig
  )(implicit uuidGenerator: UUIDGenerator): Resource[F, DiscussionsWrites[F]] =
    module.setupWrites[F](domainConfig).map {
      case WritesInfrastructure(transactor, internalProducer, internalConsumerStream, producer) =>
        val channelWrites:      ChannelWrites[F]      = wire[ChannelWritesImpl[F]]
        val postWrites:         PostWrites[F]         = wire[PostWritesImpl[F]]
        val commentWrites:      CommentWrites[F]      = wire[CommentWritesImpl[F]]
        val subscriptionWrites: SubscriptionWrites[F] = wire[SubscriptionWritesImpl[F]]

        val projector: Projector[F, DiscussionCommandEvent, (UUID, DiscussionEvent)] = NonEmptyList
          .of(
            new ChannelProjector[F](transactor),
            new CommentProjector[F](transactor),
            new PostProjector[F](transactor),
            new SubscriptionProjector[F](transactor)
          )
          .reduce
        val runProjector: Resource[F, F[Unit]] =
          internalConsumerStream.withPipeToResource(logger)(projector andThen producer)

        wire[DiscussionsWrites[F]]
    }
}
