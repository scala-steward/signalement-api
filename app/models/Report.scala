package models

import java.time.LocalDateTime
import java.util.UUID

import com.github.tminglei.slickpg.composite.Struct
import play.api.libs.json.{Json, OFormat, Writes}
import utils.Constants.StatusConso.StatusConsoValue
import utils.Constants.StatusPro.{PROMESSE_ACTION, PROMESSE_ACTION_REFUSEE, StatusProValue}

case class Report(
                   id: Option[UUID],
                   category: String,
                   subcategories: List[String],
                   details: List[DetailInputValue],
                   companyName: String,
                   companyAddress: String,
                   companyPostalCode: Option[String],
                   companySiret: Option[String],
                   creationDate: Option[LocalDateTime],
                   firstName: String,
                   lastName: String,
                   email: String,
                   contactAgreement: Boolean,
                   files: List[ReportFile],
                   statusPro: Option[StatusProValue],
                   statusConso: Option[StatusConsoValue]
                 )

object Report {

  implicit val reportWriter = Json.writes[Report]
  implicit val reportReader = Json.reads[Report]

  private def getStatusProFiltered(statusPro: Option[StatusProValue]): String = {
    statusPro match {
      case Some(PROMESSE_ACTION) | Some(PROMESSE_ACTION_REFUSEE) => statusPro.get.value
      case _ => ""
    }
  }

  val reportProWriter = new Writes[Report] {
    def writes(report: Report) =
      Json.obj(
      "id" -> report.id,
      "category" -> report.category,
      "subcategories" -> report.subcategories,
      "details" -> report.details,
      "creationDate" -> report.creationDate,
      "companyName" -> report.companyName,
      "companyAddress" -> report.companyAddress,
      "companyPostalCode" -> report.companyPostalCode,
      "companySiret" -> report.companySiret,
      "files" -> report.files,
      "contactAgreement" -> report.contactAgreement,
      "statusPro" -> getStatusProFiltered(report.statusPro)
    ) ++ (report.contactAgreement match {
        case true => Json.obj(
          "firstName" -> report.firstName,
          "lastName" -> report.lastName,
          "email" -> report.email
        )
        case _ => Json.obj()
      })
  }
}


case class  DetailInputValue (
                           label: String,
                           value: String
                 ) extends Struct

object DetailInputValue {
  implicit val detailInputValueFormat: OFormat[DetailInputValue] = Json.format[DetailInputValue]

  implicit def string2detailInputValue(input: String): DetailInputValue = {
    input match {
      case input if input.contains(':') => DetailInputValue(input.substring(0, input.indexOf(':') + 1), input.substring(input.indexOf(':') + 1).trim)
      case input => DetailInputValue("Précision :", input)
    }
  }
}