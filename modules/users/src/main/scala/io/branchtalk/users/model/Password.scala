package io.branchtalk.users.model

import java.security.SecureRandom

import cats.{ Eq, Show }
import enumeratum.{ Enum, EnumEntry }
import enumeratum.EnumEntry.Hyphencase
import io.branchtalk.shared.models.{ FastEq, ShowPretty }
import io.estatico.newtype.macros.newtype
import io.scalaland.catnip.Semi

@Semi(FastEq, ShowPretty) final case class Password(
  algorithm: Password.Algorithm,
  hash:      Password.Hash,
  salt:      Password.Salt
) {

  def update(raw: Password.Raw): Password = copy(hash = algorithm.hashRaw(raw, salt))
  def verify(raw: Password.Raw): Boolean  = algorithm.verify(raw, salt, hash)
}
object Password {

  @Semi(FastEq, ShowPretty) sealed trait Algorithm extends EnumEntry with Hyphencase {

    def createSalt: Password.Salt
    def hashRaw(raw: Password.Raw, salt: Password.Salt): Password.Hash
    def verify(raw:  Password.Raw, salt: Password.Salt, hash: Password.Hash): Boolean
  }
  object Algorithm extends Enum[Algorithm] {
    private lazy val sr = new SecureRandom()

    case object BCrypt extends Algorithm {
      private val cost = 10 // must be between 4 and 31 TODO: make it configurable or sth?

      private val hasher   = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults()
      private val verifier = at.favre.lib.crypto.bcrypt.BCrypt.verifyer()

      override def createSalt: Password.Salt = {
        val bytes = new Array[Byte](16) // required by BCrypt to have 16 bytes
        sr.nextBytes(bytes)
        Password.Salt(bytes)
      }

      override def hashRaw(raw: Password.Raw, salt: Password.Salt): Password.Hash =
        Password.Hash(hasher.hashRaw(cost, salt.bytes, raw.bytes).rawHash)

      override def verify(raw: Password.Raw, salt: Password.Salt, hash: Password.Hash): Boolean =
        verifier.verify(raw.bytes, cost, salt.bytes, hash.bytes).verified
    }

    def default: Algorithm = BCrypt // TODO: use config to change this when more than one option is available

    val values: IndexedSeq[Algorithm] = findValues
  }

  @newtype final case class Hash(bytes: Array[Byte])
  object Hash {

    implicit val show: Show[Hash] = (_: Hash) => s"Password.Hash(EDITED OUT)"
    implicit val eq:   Eq[Hash]   = (x: Hash, y: Hash) => x.bytes sameElements y.bytes
  }

  @newtype final case class Salt(bytes: Array[Byte])
  object Salt {

    implicit val show: Show[Salt] = (_: Salt) => s"Password.Salt(EDITED OUT)"
    implicit val eq:   Eq[Salt]   = (x: Salt, y: Salt) => x.bytes sameElements y.bytes
  }

  @newtype final case class Raw(bytes: Array[Byte])
  object Raw {

    implicit val show: Show[Raw] = (_: Raw) => s"Password.Raw(EDITED OUT)"
    implicit val eq:   Eq[Raw]   = (x: Raw, y: Raw) => x.bytes sameElements y.bytes
  }

  def create(raw: Password.Raw): Password = {
    val algorithm = Password.Algorithm.default
    val salt      = algorithm.createSalt
    val hash      = algorithm.hashRaw(raw, salt)
    Password(algorithm, hash, salt)
  }
}
