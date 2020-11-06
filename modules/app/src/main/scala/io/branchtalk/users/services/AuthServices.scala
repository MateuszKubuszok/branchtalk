package io.branchtalk.users.services

import cats.effect.Sync
import io.branchtalk.api.{ Authentication, Password, Permission, SessionID, Username }
import io.branchtalk.mappings._
import io.branchtalk.shared.models.{ CodePosition, CommonError }
import io.branchtalk.users
import io.branchtalk.users.reads.{ SessionReads, UserReads }

trait AuthServices[F[_]] {

  def authenticateUser(auth:               Authentication): F[users.model.User]
  def authenticateUserWithSessionOpt(auth: Authentication): F[(users.model.User, Option[users.model.Session])]

  def authorizeUser(auth: Authentication, requirePermissions: Permission*): F[users.model.User]
}

final class AuthServicesImpl[F[_]: Sync](userReads: UserReads[F], sessionReads: SessionReads[F])
    extends AuthServices[F] {

  private def authSessionID(sessionID: SessionID) =
    for {
      session <- sessionReads.requireSession(sessionIDApi2Users.get(sessionID))
      user <- userReads.requireById(session.data.userID)
    } yield (user, session)

  private def authCredentials(username: Username, password: Password) =
    userReads.authenticate(usernameApi2Users.get(username), passwordApi2Users.get(password))

  override def authenticateUser(auth: Authentication): F[users.model.User] =
    auth.fold(authSessionID(_).map(_._1), authCredentials)

  override def authenticateUserWithSessionOpt(
    auth: Authentication
  ): F[(users.model.User, Option[users.model.Session])] = auth.fold(
    sessionID => authSessionID(sessionID).map { case (user, session) => user -> session.some },
    (username, password) => authCredentials(username, password).map(user => user -> none[users.model.Session])
  )

  override def authorizeUser(auth: Authentication, requirePermissions: Permission*): F[users.model.User] =
    for {
      (user, sessionOpt) <- authenticateUserWithSessionOpt(auth)
      allOwnedPermissions = user.data.permissions.append(users.model.Permission.IsUser(user.id))
      available = sessionOpt.map(_.data.usage).collect { case users.model.SessionProperties.Usage.OAuth(permissions) =>
        permissions
      } match {
        case Some(constrained) => allOwnedPermissions intersect constrained
        case None              => allOwnedPermissions
      }
      required = requirePermissions.map(permissionApi2Users.get).toList
      _ <-
        if (available.allow(required.toSeq: _*)) Sync[F].unit
        else {
          (CommonError.InsufficientPermissions(
            s"User has insufficient permissions: available ${available.show}, required: ${required.show}",
            CodePosition.providePosition
          ): Throwable).raiseError[F, Unit]
        }
    } yield user
}
