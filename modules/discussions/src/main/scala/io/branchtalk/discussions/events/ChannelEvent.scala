package io.branchtalk.discussions.events

import io.scalaland.catnip.Semi
import io.branchtalk.ADT
import io.branchtalk.discussions.models.{ Channel, User }
import io.branchtalk.shared.models._

@Semi(FastEq, ShowPretty) sealed trait ChannelEvent extends ADT
object ChannelEvent {

  @Semi(FastEq, ShowPretty) final case class Created(
    id:          ID[Channel],
    authorID:    ID[User],
    urlName:     Channel.UrlName,
    name:        Channel.Name,
    description: Option[Channel.Description],
    createdAt:   CreationTime
  ) extends ChannelEvent

  @Semi(FastEq, ShowPretty) final case class Updated(
    id:          ID[Channel],
    editorID:    ID[User],
    urlName:     Updatable[Channel.UrlName],
    name:        Updatable[Channel.Name],
    description: OptionUpdatable[Channel.Description],
    modifiedAt:  ModificationTime
  ) extends ChannelEvent

  @Semi(FastEq, ShowPretty) final case class Deleted(
    id:       ID[Channel],
    editorID: ID[User]
  ) extends ChannelEvent

  @Semi(FastEq, ShowPretty) final case class Restored(
    id:       ID[Channel],
    editorID: ID[User]
  ) extends ChannelEvent
}
