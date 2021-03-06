package models

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json.{Json, OFormat}


case class Rating(
                  id: Option[UUID],
                  creationDate: Option[OffsetDateTime],
                  category: String,
                  subcategories: List[String],
                  positive: Boolean
                )

object Rating {

  implicit val ratingFormat: OFormat[Rating] = Json.format[Rating]

}
