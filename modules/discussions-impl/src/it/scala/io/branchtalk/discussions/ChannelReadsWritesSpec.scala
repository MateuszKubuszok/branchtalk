package io.branchtalk.discussions

import cats.effect.{ IO, Resource }
import io.branchtalk.{ IOTest, ResourcefulTest }
import io.branchtalk.discussions.model.Channel
import io.branchtalk.shared.models.UUIDGenerator
import org.specs2.mutable.Specification

final class ChannelReadsWritesSpec extends Specification with IOTest with ResourcefulTest with DiscussionsFixtures {

  private implicit val uuidGenerator: UUIDGenerator = UUIDGenerator.FastUUIDGenerator

  // populated by resources
  private var discussionsReads:  DiscussionsReads[IO]  = _
  private var discussionsWrites: DiscussionsWrites[IO] = _

  override protected def testResource: Resource[IO, Unit] =
    for {
      domainCfg <- TestDiscussionsConfig.loadDomainConfig[IO]
      reads <- DiscussionsModule.reads[IO](domainCfg)
      writes <- DiscussionsModule.writes[IO](domainCfg)
    } yield {
      discussionsReads  = reads
      discussionsWrites = writes
    }

  "Channel Reads & Writes" should {

    "create a Channel and eventually read it" in {
      discussionsWrites.runProjector.use { projector =>
        for {
          // given
          _ <- projector.handleError(_.printStackTrace()).start
          creationData <- (0 until 3).toList.traverse(_ => channelCreate)
          // when
          toCreate <- creationData.traverse(discussionsWrites.channelWrites.createChannel)
          ids = toCreate.map(_.id)
          channels <- ids.traverse(discussionsReads.channelReads.requireById).eventually()
          channelsOpt <- ids.traverse(discussionsReads.channelReads.getById).eventually()
          channelsExist <- ids.traverse(discussionsReads.channelReads.exists).eventually()
          channelDeleted <- ids.traverse(discussionsReads.channelReads.deleted).eventually()
        } yield {
          // then
          ids.toSet === channels.map(_.id).toSet
          channelsOpt.forall(_.isDefined) must beTrue
          channelsExist.forall(identity) must beTrue
          channelDeleted.exists(identity) must beFalse
        }
      }
    }

    "allow delete and restore of a created Channel" in {
      discussionsWrites.runProjector.use { projector =>
        for {
          // given
          _ <- projector.handleError(_.printStackTrace()).start
          creationData <- (0 until 3).toList.traverse(_ => channelCreate)
          editorID <- editorIDCreate
          // when
          toCreate <- creationData.traverse(discussionsWrites.channelWrites.createChannel)
          ids = toCreate.map(_.id)
          _ <- ids.traverse(discussionsReads.channelReads.requireById).eventually()
          _ <- ids.map(Channel.Delete(_, editorID)).traverse(discussionsWrites.channelWrites.deleteChannel)
          _ <- ids
            .traverse(discussionsReads.channelReads.getById)
            .flatTap(results => IO(assert(results.forall(_.isEmpty), "All Channels should be eventually deleted")))
            .eventually()
          notExist <- ids.traverse(discussionsReads.channelReads.exists)
          areDeleted <- ids.traverse(discussionsReads.channelReads.deleted)
          _ <- ids.map(Channel.Restore(_, editorID)).traverse(discussionsWrites.channelWrites.restoreChannel)
          toRestore <- ids
            .traverse(discussionsReads.channelReads.getById)
            .flatTap { results =>
              IO(assert(results.forall(_.isDefined), "All Channels should be eventually restored"))
            }
            .eventually()
          restoredIds = toRestore.flatten.map(_.id)
          areRestored <- ids.traverse(discussionsReads.channelReads.exists)
          notDeleted <- ids.traverse(discussionsReads.channelReads.deleted)
        } yield {
          // then
          ids.toSet === restoredIds.toSet
          notExist.exists(identity) must beFalse
          areDeleted.forall(identity) must beTrue
          areRestored.forall(identity) must beTrue
          notDeleted.exists(identity) must beFalse
        }
      }
    }

    // TODO: test update
  }
}
