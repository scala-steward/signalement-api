package controllers

import akka.actor._
import java.io.File
import java.time.{LocalDateTime, OffsetDateTime}
import java.time.format.DateTimeFormatter
import java.util.UUID

import com.mohiva.play.silhouette.api.Silhouette
import com.norbitltd.spoiwo.model._
import com.norbitltd.spoiwo.model.enums.{CellFill, CellHorizontalAlignment, CellVerticalAlignment}
import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._
import javax.inject.{Inject, Singleton, Named}
import actors.ReportsExtractActor
import models._
import models.Event._
import orchestrators.ReportOrchestrator
import play.api.libs.json.{JsError, JsObject, Json}
import play.api.{Configuration, Logger}
import repositories._
import services.{MailerService, S3Service}
import utils.Constants.{ActionEvent, EventType, ReportStatus}
import utils.Constants.ReportStatus._
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.{AuthEnv, WithPermission}
import utils.{Constants, DateUtils, SIRET}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success, Try}

@Singleton
class ReportListController @Inject()(reportOrchestrator: ReportOrchestrator,
                                     reportRepository: ReportRepository,
                                     companyRepository: CompanyRepository,
                                     eventRepository: EventRepository,
                                     userRepository: UserRepository,
                                     mailerService: MailerService,
                                     s3Service: S3Service,
                                     @Named("reports-extract-actor") reportsExtractActor: ActorRef,
                                     val silhouette: Silhouette[AuthEnv],
                                     val silhouetteAPIKey: Silhouette[APIKeyEnv],
                                     configuration: Configuration)
                                    (implicit val executionContext: ExecutionContext) extends BaseController {

  implicit val timeout: akka.util.Timeout = 5.seconds
  val logger: Logger = Logger(this.getClass)

  def fetchCompany(user: User, siret: Option[String]): Future[Company] = {
    for {
      accesses <- companyRepository.fetchCompaniesWithLevel(user)
    } yield {
      siret.map(s => accesses.filter(_._1.siret == SIRET(s))).getOrElse(accesses).map(_._1).head
    }
  }

  def getReports(
                  offset: Option[Long],
                  limit: Option[Int],
                  departments: Option[String],
                  email: Option[String],
                  siret: Option[String],
                  companyName: Option[String],
                  start: Option[String],
                  end: Option[String],
                  category: Option[String],
                  status: Option[String],
                  details: Option[String]

  ) = SecuredAction.async { implicit request =>

    // valeurs par défaut
    val LIMIT_DEFAULT = 25
    val LIMIT_MAX = 250

    // normalisation des entrées
    val offsetNormalized: Long = offset.map(Math.max(_, 0)).getOrElse(0)
    val limitNormalized = limit.map(Math.max(_, 0)).map(Math.min(_, LIMIT_MAX)).getOrElse(LIMIT_DEFAULT)

    val startDate = DateUtils.parseDate(start)
    val endDate = DateUtils.parseEndDate(end)

    val filter = ReportFilter(
      departments.map(d => d.split(",").toSeq).getOrElse(Seq()),
      email,
      siret,
      companyName,
      startDate,
      endDate,
      category,
      getStatusListForValueWithUserRole(status, request.identity.userRole),
      details,
      request.identity.userRole match {
        case UserRoles.Pro => Some(false)
        case _ => None
      }
    )

    for {
      company <- Some(request.identity)
                  .filter(_.userRole == UserRoles.Pro)
                  .map(u => fetchCompany(u, siret).map(Some(_)))
                  .getOrElse(Future(None))
      paginatedReports <- reportRepository.getReports(
                            offsetNormalized,
                            limitNormalized,
                            company.map(c => filter.copy(siret=Some(c.siret.value)))
                                   .getOrElse(filter))
      reportFilesMap <- reportRepository.prefetchReportsFiles(paginatedReports.entities.map(_.id))
    } yield {
      Ok(Json.toJson(paginatedReports.copy(entities = paginatedReports.entities.map(r => ReportWithFiles(r, reportFilesMap.getOrElse(r.id, Nil))))))
    }
  }

  def extractReports(departments: Option[String],
                     siret: Option[String],
                     start: Option[String],
                     end: Option[String],
                     category: Option[String],
                     status: Option[String],
                     details: Option[String]) = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>
    for {
      restrictToCompany <- if (request.identity.userRole == UserRoles.Pro)
                              fetchCompany(request.identity, siret).map(Some(_))
                           else
                              Future(None)
    } yield {
      logger.debug(s"Requesting report for user ${request.identity.email}")
      reportsExtractActor ! ReportsExtractActor.ExtractRequest(
        request.identity,
        restrictToCompany,
        ReportsExtractActor.RawFilters(
          departments, siret, start, end,
          category, status, details
        )
      )
      Ok
    }
  }

  def confirmContactByPostOnReportList() = SecuredAction(WithPermission(UserPermission.createEvent)).async(parse.json) { implicit request =>

    import ReportListObjects.ReportList

    request.body.validate[ReportList](Json.reads[ReportList]).fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      reportList => {
        logger.debug(s"confirmContactByPostOnReportList ${reportList.reportIds}")
        reportOrchestrator.markBatchPosted(request.identity, reportList.reportIds).map(events => Ok)
      }
    )
  }

}

object ReportListObjects {
  case class ReportList(reportIds: List[UUID])
}
