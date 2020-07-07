package io.branchtalk.shared.infrastructure

import cats.effect.{ ConcurrentEffect, ContextShift, Resource, Timer }
import doobie.util.transactor.Transactor
import fs2.kafka.{ Deserializer, Serializer }
import io.branchtalk.shared.models.UUID

final case class ReadsInfrastructure[F[_], Event](
  transactor: Transactor[F],
  consumer:   EventBusConsumer[F, UUID, Event]
)

final case class WritesInfrastructure[F[_], Event, InternalEvent](
  transactor:        Transactor[F],
  internalPublisher: EventBusProducer[F, UUID, InternalEvent],
  internalConsumer:  EventBusConsumer[F, UUID, InternalEvent],
  publisher:         EventBusProducer[F, UUID, Event]
)

trait DomainModule[Event, InternalEvent] {

  // TODO: implement somehow
  implicit def keySerializer[F[_]]:             Serializer[F, UUID]            = ???
  implicit def keyDeserializer[F[_]]:           Deserializer[F, UUID]          = ???
  implicit def internalEventSerializer[F[_]]:   Serializer[F, InternalEvent]   = ???
  implicit def internalEventDeserializer[F[_]]: Deserializer[F, InternalEvent] = ???
  implicit def eventSerializer[F[_]]:           Serializer[F, Event]           = ???
  implicit def eventDeserializer[F[_]]:         Deserializer[F, Event]         = ???

  protected def setupReads[F[_]: ConcurrentEffect: ContextShift: Timer](
    domainConfig: DomainConfig
  ): Resource[F, ReadsInfrastructure[F, Event]] =
    for {
      transactor <- new PostgresDatabase(domainConfig.database).transactor
      consumer = KafkaEventBus.consumer[F, UUID, Event](domainConfig.publishedEventBus)
    } yield ReadsInfrastructure(transactor, consumer)

  protected def setupWrites[F[_]: ConcurrentEffect: ContextShift: Timer](
    domainConfig: DomainConfig
  ): Resource[F, WritesInfrastructure[F, Event, InternalEvent]] =
    for {
      transactor <- new PostgresDatabase(domainConfig.database).transactor
      internalPublisher = KafkaEventBus.producer[F, UUID, InternalEvent](domainConfig.internalEventBus)
      internalConsumer  = KafkaEventBus.consumer[F, UUID, InternalEvent](domainConfig.internalEventBus)
      publisher         = KafkaEventBus.producer[F, UUID, Event](domainConfig.publishedEventBus)
    } yield WritesInfrastructure(transactor, internalPublisher, internalConsumer, publisher)
}
