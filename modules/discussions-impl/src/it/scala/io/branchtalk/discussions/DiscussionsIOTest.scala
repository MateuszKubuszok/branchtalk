package io.branchtalk.discussions

import cats.effect.{ IO, Resource }
import io.branchtalk.{ IOTest, ResourcefulTest }
import io.branchtalk.shared.models.UUIDGenerator

trait DiscussionsIOTest extends IOTest with ResourcefulTest {

  protected implicit val uuidGenerator: UUIDGenerator

  // populated by resources
  protected var discussionsReads:  DiscussionsReads[IO]  = _
  protected var discussionsWrites: DiscussionsWrites[IO] = _

  protected val discussionsResource: Resource[IO, Unit] = for {
    discussionsCfg <- TestDiscussionsConfig.loadDomainConfig[IO]
    _ <- DiscussionsModule.reads[IO](discussionsCfg).map(discussionsReads   = _)
    _ <- DiscussionsModule.writes[IO](discussionsCfg).map(discussionsWrites = _)
  } yield ()

  override protected def testResource: Resource[IO, Unit] = super.testResource >> discussionsResource
}
