package io.branchtalk.discussions

import cats.effect.{ IO, Resource }
import io.branchtalk.{ IOTest, ResourcefulTest }
import io.branchtalk.discussions.model.{ Comment, Post }
import io.branchtalk.shared.models.{ ID, UUIDGenerator, Updatable }
import org.specs2.mutable.Specification

final class CommentReadsWritesSpec extends Specification with IOTest with ResourcefulTest with DiscussionsFixtures {

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

  "Comment Reads & Writes" should {

    "don't create a Comment if there is no Post for it" in {
      discussionsWrites.runProjector.use { projector =>
        for {
          // given
          _ <- projector.logError("Error reported by projector").start
          postID <- ID.create[IO, Post]
          creationData <- (0 until 3).toList.traverse(_ => commentCreate(postID))
          // when
          toCreate <- creationData.traverse(discussionsWrites.commentWrites.createComment(_).attempt)
        } yield {
          // then
          toCreate.forall(_.isLeft) must beTrue
        }
      }
    }

    "create a Comment and eventually read it" in {
      discussionsWrites.runProjector.use { projector =>
        for {
          // given
          _ <- projector.logError("Error reported by projector").start
          channelID <- channelCreate.flatMap(discussionsWrites.channelWrites.createChannel).map(_.id)
          _ <- discussionsReads.channelReads.requireById(channelID).eventually()
          postID <- postCreate(channelID).flatMap(discussionsWrites.postWrites.createPost).map(_.id)
          _ <- discussionsReads.postReads.requireById(postID).eventually()
          creationData <- (0 until 3).toList.traverse(_ => commentCreate(postID))
          // when
          toCreate <- creationData.traverse(discussionsWrites.commentWrites.createComment)
          ids = toCreate.map(_.id)
          comments <- ids.traverse(discussionsReads.commentReads.requireById).eventually()
          commentsOpt <- ids.traverse(discussionsReads.commentReads.getById).eventually()
          commentsExist <- ids.traverse(discussionsReads.commentReads.exists).eventually()
          commentDeleted <- ids.traverse(discussionsReads.commentReads.deleted).eventually()
        } yield {
          // then
          ids.toSet === comments.map(_.id).toSet
          commentsOpt.forall(_.isDefined) must beTrue
          commentsExist.forall(identity) must beTrue
          commentDeleted.exists(identity) must beFalse
        }
      }
    }

    "don't update a Comment that doesn't exists" in {
      discussionsWrites.runProjector.use { projector =>
        for {
          // given
          _ <- projector.logError("Error reported by projector").start
          channelID <- channelCreate.flatMap(discussionsWrites.channelWrites.createChannel).map(_.id)
          _ <- discussionsReads.channelReads.requireById(channelID).eventually()
          postID <- postCreate(channelID).flatMap(discussionsWrites.postWrites.createPost).map(_.id)
          editorID <- editorIDCreate
          creationData <- (0 until 3).toList.traverse(_ => commentCreate(postID))
          fakeUpdateData <- creationData.traverse { data =>
            ID.create[IO, Comment].map { id =>
              Comment.Update(
                id         = id,
                editorID   = editorID,
                newContent = Updatable.Set(data.content)
              )
            }
          }
          // when
          toUpdate <- fakeUpdateData.traverse(discussionsWrites.commentWrites.updateComment(_).attempt)
        } yield {
          // then
          toUpdate.forall(_.isLeft) must beTrue
        }
      }
    }

    "update an existing Comment" in {
      discussionsWrites.runProjector.use { projector =>
        for {
          // given
          _ <- projector.logError("Error reported by projector").start
          channelID <- channelCreate.flatMap(discussionsWrites.channelWrites.createChannel).map(_.id)
          _ <- discussionsReads.channelReads.requireById(channelID).eventually()
          postID <- postCreate(channelID).flatMap(discussionsWrites.postWrites.createPost).map(_.id)
          _ <- discussionsReads.postReads.requireById(postID).eventually()
          editorID <- editorIDCreate
          creationData <- (0 until 2).toList.traverse(_ => commentCreate(postID))
          toCreate <- creationData.traverse(discussionsWrites.commentWrites.createComment)
          ids = toCreate.map(_.id)
          created <- ids.traverse(discussionsReads.commentReads.requireById).eventually()
          updateData = created.zipWithIndex.collect {
            case (Comment(id, data), 0) =>
              Comment.Update(
                id         = id,
                editorID   = editorID,
                newContent = Updatable.Set(data.content)
              )
            case (Comment(id, _), 1) =>
              Comment.Update(
                id         = id,
                editorID   = editorID,
                newContent = Updatable.Keep
              )
          }
          // when
          _ <- updateData.traverse(discussionsWrites.commentWrites.updateComment)
          updated <- ids
            .traverse(discussionsReads.commentReads.requireById)
            .flatTap { current =>
              IO(assert(current.head.data.lastModifiedAt.isDefined, "Updated entity should have lastModifiedAt set"))
            }
            .eventually()
        } yield {
          // then
          created
            .zip(updated)
            .zipWithIndex
            .collect {
              case ((Comment(_, older), Comment(_, newer)), 0) =>
                // set case
                older === newer.copy(lastModifiedAt = None)
              case ((Comment(_, older), Comment(_, newer)), 1) =>
                // keep case
                older === newer
            }
            .forall(identity) must beTrue
        }
      }
    }

    "allow delete and restore of a created Comment" in {
      discussionsWrites.runProjector.use { projector =>
        for {
          // given
          _ <- projector.logError("Error reported by projector").start
          channelID <- channelCreate.flatMap(discussionsWrites.channelWrites.createChannel).map(_.id)
          _ <- discussionsReads.channelReads.requireById(channelID).eventually()
          postID <- postCreate(channelID).flatMap(discussionsWrites.postWrites.createPost).map(_.id)
          _ <- discussionsReads.postReads.requireById(postID).eventually()
          creationData <- (0 until 3).toList.traverse(_ => commentCreate(postID))
          editorID <- editorIDCreate
          // when
          toCreate <- creationData.traverse(discussionsWrites.commentWrites.createComment)
          ids = toCreate.map(_.id)
          _ <- ids.traverse(discussionsReads.commentReads.requireById).eventually()
          _ <- ids.map(Comment.Delete(_, editorID)).traverse(discussionsWrites.commentWrites.deleteComment)
          _ <- ids
            .traverse(discussionsReads.commentReads.getById)
            .flatTap(results => IO(assert(results.forall(_.isEmpty), "All Comments should be eventually deleted")))
            .eventually()
          notExist <- ids.traverse(discussionsReads.commentReads.exists)
          areDeleted <- ids.traverse(discussionsReads.commentReads.deleted)
          _ <- ids.map(Comment.Restore(_, editorID)).traverse(discussionsWrites.commentWrites.restoreComment)
          toRestore <- ids
            .traverse(discussionsReads.commentReads.getById)
            .flatTap(results => IO(assert(results.forall(_.isDefined), "All Comments should be eventually restored")))
            .eventually()
          restoredIds = toRestore.flatten.map(_.id)
          areRestored <- ids.traverse(discussionsReads.commentReads.exists)
          notDeleted <- ids.traverse(discussionsReads.commentReads.deleted)
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
  }
}
