package utils

import play.api.libs.json._
import repositories.PostgresProfile.api._

object EmailsHelpers {
  case class Email(value: String)
  implicit def normalizeEmail(email: Email): String = email.value.toLowerCase.trim
  implicit val EmailColumnType = MappedColumnType.base[Email, String](
    normalizeEmail(_),
    Email(_)
  )
  implicit val emailWrites = new Writes[Email] {
    def writes(o: Email): JsValue = {
      JsString(o)
    }
  }
  implicit val emailReads = new Reads[Email] {
    def reads(json: JsValue): JsResult[Email] = json.validate[String].map(Email(_))
  }
}
