package io.branchtalk.discussions.events

import io.scalaland.catnip.Semi
import io.branchtalk.ADT
import io.branchtalk.discussions.dao.{ Comment, Post, User }
import io.branchtalk.shared.models.{ CreationTime, FastEq, ID, ModificationTime, ShowPretty, Updatable }

@Semi(FastEq, ShowPretty) sealed trait CommentCommandEvent extends ADT
object CommentCommandEvent {

  @Semi(FastEq, ShowPretty) final case class Create(
    id:        ID[Comment],
    authorID:  ID[User],
    postID:    ID[Post],
    content:   Comment.Content,
    replyTo:   Option[ID[Comment]],
    createdAt: CreationTime
  ) extends CommentCommandEvent

  @Semi(FastEq, ShowPretty) final case class Update(
    id:         ID[Comment],
    editorID:   ID[User],
    newContent: Updatable[Comment.Content],
    modifiedAt: ModificationTime
  ) extends CommentCommandEvent

  @Semi(FastEq, ShowPretty) final case class Delete(
    id:       ID[Comment],
    editorID: ID[User]
  ) extends CommentCommandEvent

  @Semi(FastEq, ShowPretty) final case class Restore(
    id:       ID[Comment],
    editorID: ID[User]
  ) extends CommentCommandEvent
}
