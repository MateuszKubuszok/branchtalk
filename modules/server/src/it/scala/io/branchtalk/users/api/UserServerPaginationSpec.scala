package io.branchtalk.users.api

import io.branchtalk.api.{ Permission => _, RequiredPermissions => _, _ }
import io.branchtalk.discussions.DiscussionsFixtures
import io.branchtalk.mappings._
import io.branchtalk.shared.model._
import io.branchtalk.users.UsersFixtures
import io.branchtalk.users.api.UserModels._
import io.branchtalk.users.model.{ Permission, RequiredPermissions, User }
import org.specs2.mutable.Specification
import sttp.model.StatusCode

final class UserServerPaginationSpec
    extends Specification
    with ServerIOTest
    with UsersFixtures
    with DiscussionsFixtures {

  // User pagination tests cannot be run in parallel to other User tests (no parent to filter other tests)
  sequential

  implicit protected val uuidGenerator: TestUUIDGenerator = new TestUUIDGenerator

  "UserServer-provided pagination endpoints" should {

    "on GET /users" in {

      "return paginated Users" in {
        for {
          // given
          (CreationScheduled(userID), CreationScheduled(sessionID)) <- userCreate.flatMap(
            usersWrites.userWrites.createUser
          )
          _ <- usersReads.userReads.requireById(userID).eventually()
          _ <- usersWrites.userWrites.updateUser(
            User.Update(
              id = userID,
              moderatorID = None,
              newUsername = Updatable.Keep,
              newDescription = OptionUpdatable.Keep,
              newPassword = Updatable.Keep,
              updatePermissions = List(Permission.Update.Add(Permission.ModerateUsers))
            )
          )
          _ <- usersReads.userReads
            .requireById(userID)
            .assert("User should eventually have Moderator status")(
              _.data.permissions.allow(RequiredPermissions.one(Permission.ModerateUsers))
            )
            .eventually()
          userIDs <- (0 until 9).toList
            .traverse(_ => userCreate.flatMap(usersWrites.userWrites.createUser).map(_._1.id))
            .map(_ :+ userID)
          users <- userIDs.traverse(usersReads.userReads.requireById(_)).eventually()
          // when
          response1 <- UserAPIs.paginate.toTestCall.untupled(
            Authentication.Session(sessionID = sessionIDApi2Users.reverseGet(sessionID)),
            None,
            PaginationLimit(5).some
          )
          response2 <- UserAPIs.paginate.toTestCall.untupled(
            Authentication.Session(sessionID = sessionIDApi2Users.reverseGet(sessionID)),
            PaginationOffset(5L).some,
            PaginationLimit(5).some
          )
        } yield {
          // then
          response1.code must_=== StatusCode.Ok
          response1.body must beValid(beRight(anInstanceOf[Pagination[APIUser]]))
          response2.code must_=== StatusCode.Ok
          response2.body must beValid(beRight(anInstanceOf[Pagination[APIUser]]))
          (response1.body.toValidOpt.flatMap(_.toOption), response2.body.toValidOpt.flatMap(_.toOption))
            .mapN { (pagination1, pagination2) =>
              (pagination1.entities.toSet ++ pagination2.entities.toSet) must_=== users.map(APIUser.fromDomain).toSet
            }
            .getOrElse(pass)
        }
      }
    }

    "on GET /users/newest" in {

      "return newest Users" in {
        for {
          // given
          _ <- usersReads.userReads.paginate(User.Sorting.NameAlphabetically, 0L, 1000).flatMap {
            case Paginated(entities, _) =>
              entities.traverse_(user => usersWrites.userWrites.deleteUser(User.Delete(user.id, None)))
          }
          (CreationScheduled(userID), CreationScheduled(sessionID)) <- userCreate.flatMap(
            usersWrites.userWrites.createUser
          )
          _ <- usersReads.userReads.requireById(userID).eventually()
          _ <- usersWrites.userWrites.updateUser(
            User.Update(
              id = userID,
              moderatorID = None,
              newUsername = Updatable.Keep,
              newDescription = OptionUpdatable.Keep,
              newPassword = Updatable.Keep,
              updatePermissions = List(Permission.Update.Add(Permission.ModerateUsers))
            )
          )
          _ <- usersReads.userReads
            .requireById(userID)
            .assert("User should eventually have Moderator status")(
              _.data.permissions.allow(RequiredPermissions.one(Permission.ModerateUsers))
            )
            .eventually()
          userIDs <- (0 until 9).toList
            .traverse(_ => userCreate.flatMap(usersWrites.userWrites.createUser).map(_._1.id))
            .map(_ :+ userID)
          users <- userIDs.traverse(usersReads.userReads.requireById(_)).eventually()
          // when
          response1 <- UserAPIs.newest.toTestCall.untupled(
            Authentication.Session(sessionID = sessionIDApi2Users.reverseGet(sessionID)),
            None,
            PaginationLimit(5).some
          )
          response2 <- UserAPIs.newest.toTestCall.untupled(
            Authentication.Session(sessionID = sessionIDApi2Users.reverseGet(sessionID)),
            PaginationOffset(5L).some,
            PaginationLimit(5).some
          )
        } yield {
          // then
          response1.code must_=== StatusCode.Ok
          response1.body must beValid(beRight(anInstanceOf[Pagination[APIUser]]))
          response2.code must_=== StatusCode.Ok
          response2.body must beValid(beRight(anInstanceOf[Pagination[APIUser]]))
          (response1.body.toValidOpt.flatMap(_.toOption), response2.body.toValidOpt.flatMap(_.toOption))
            .mapN { (pagination1, pagination2) =>
              (pagination1.entities.toSet ++ pagination2.entities.toSet) must_=== users.map(APIUser.fromDomain).toSet
            }
            .getOrElse(pass)
        }
      }
    }
  }
}
