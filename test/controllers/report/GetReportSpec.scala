package controllers.report

import java.time.OffsetDateTime
import java.util.UUID

import akka.util.Timeout
import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.{Environment, LoginInfo}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.{FakeEnvironment, _}
import controllers.ReportController
import models._
import net.codingwell.scalaguice.ScalaModule
import org.specs2.Spec
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.mailer.{Attachment, AttachmentFile}
import play.api.mvc.Result
import play.api.test.Helpers.contentAsJson
import play.api.test._
import play.mvc.Http.Status
import repositories.{EventFilter, EventRepository, ReportRepository, UserRepository}
import services.MailerService
import tasks.ReminderTaskModule
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.ReportStatus._
import utils.Constants.{ActionEvent, Departments, EventType, ReportStatus}
import utils.silhouette.auth.AuthEnv
import utils.EmailAddress

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContext, Future}


object GetReportByUnauthenticatedUser extends GetReportSpec  {
  override def is =
    s2"""
         Given an unauthenticated user                                ${step(someLoginInfo = None)}
         When retrieving the report                                   ${step(someResult = Some(getReport(neverRequestedReportUUID)))}
         Then user is not authorized                                  ${userMustBeUnauthorized}
    """
}

object GetReportByAdminUser extends GetReportSpec  {
  override def is =
    s2"""
         Given an authenticated admin user                            ${step(someLoginInfo = Some(adminLoginInfo))}
         When retrieving the report                                   ${step(someResult = Some(getReport(neverRequestedReportUUID)))}
         Then the report is rendered to the user as an Admin          ${reportMustBeRenderedForUserRole(neverRequestedReport, UserRoles.Admin)}
    """
}

object GetReportByNotConcernedProUser extends GetReportSpec  {
  override def is =
    s2"""
         Given an authenticated pro user which is not concerned by the report   ${step(someLoginInfo = Some(notConcernedProLoginInfo))}
         When getting the report                                                ${step(someResult = Some(getReport(neverRequestedReportUUID)))}
         Then the report is not found                                           ${reportMustBeNotFound}
    """
}

object GetReportByConcernedProUserFirstTime extends GetReportSpec  {
  override def is = 
    s2"""
         Given an authenticated pro user which is concerned by the report       ${step(someLoginInfo = Some(concernedProLoginInfo))}
         When retrieving the report for the first time                          ${step(someResult = Some(getReport(neverRequestedReportUUID)))}
         Then an event "ENVOI_SIGNALEMENT is created                            ${eventMustHaveBeenCreatedWithAction(ActionEvent.ENVOI_SIGNALEMENT)}
         And the report reportStatusList is updated to "SIGNALEMENT_TRANSMIS"   ${reportMustHaveBeenUpdatedWithStatus(ReportStatus.SIGNALEMENT_TRANSMIS)}
         And a mail is sent to the consumer                                     ${mailMustHaveBeenSent(neverRequestedReport.email,"Votre signalement", views.html.mails.consumer.reportTransmission(neverRequestedReport).toString, Seq(AttachmentFile("logo-signal-conso.png", application.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
         And the report is rendered to the user as a Professional               ${reportMustBeRenderedForUserRole(neverRequestedReport.copy(status = Some(ReportStatus.SIGNALEMENT_TRANSMIS)), UserRoles.Pro)}
      """
}

object GetFinalReportByConcernedProUserFirstTime extends GetReportSpec  {
  override def is =
    s2"""
         Given an authenticated pro user which is concerned by the report       ${step(someLoginInfo = Some(concernedProLoginInfo))}
         When retrieving a final report for the first time                      ${step(someResult = Some(getReport(neverRequestedFinalReportUUID)))}
         Then an event "ENVOI_SIGNALEMENT is created                            ${eventMustHaveBeenCreatedWithAction(ActionEvent.ENVOI_SIGNALEMENT)}
         And the report reportStatusList is not updated                         ${reportMustNotHaveBeenUpdated}
         And no mail is sent                                                    ${mailMustNotHaveBeenSent}
         And the report is rendered to the user as a Professional               ${reportMustBeRenderedForUserRole(neverRequestedFinalReport, UserRoles.Pro)}
    """
}

object GetReportByConcernedProUserNotFirstTime extends GetReportSpec  {
  override def is =
    s2"""
         Given an authenticated pro user which is concerned by the report       ${step(someLoginInfo = Some(concernedProLoginInfo))}
         When retrieving the report not for the first time                      ${step(someResult = Some(getReport(alreadyRequestedReportUUID)))}
         Then no event is created                                               ${eventMustNotHaveBeenCreated}
         And the report reportStatusList is not updated                         ${reportMustNotHaveBeenUpdated}
         And no mail is sent                                                    ${mailMustNotHaveBeenSent}
         And the report is rendered to the user as a Professional               ${reportMustBeRenderedForUserRole(alreadyRequestedReport, UserRoles.Pro)}

    """
}

trait GetReportSpec extends Spec with GetReportContext {

  import org.specs2.matcher.MatchersImplicits._

  implicit val ee = ExecutionEnv.fromGlobalExecutionContext
  implicit val timeout: Timeout = 30.seconds

  var someLoginInfo: Option[LoginInfo] = None
  var someResult: Option[Result] = None

  def getReport(reportUUID: UUID) =  {
    Await.result(
      application.injector.instanceOf[ReportController].getReport(reportUUID.toString)
        .apply(someLoginInfo.map(FakeRequest().withAuthenticator[AuthEnv](_)).getOrElse(FakeRequest())),
      Duration.Inf
    )
  }

  def userMustBeUnauthorized() = {
    someResult must beSome and someResult.get.header.status === Status.UNAUTHORIZED
  }

  def reportMustBeNotFound() = {
    someResult must beSome and someResult.get.header.status === Status.NOT_FOUND
  }

  def reportMustBeRenderedForUserRole(report: Report, userRole: UserRole) = {
    implicit val someUserRole = Some(userRole)
    someResult must beSome and contentAsJson(Future(someResult.get)) === Json.toJson(report)
  }

  def mailMustHaveBeenSent(recipient: EmailAddress, subject: String, bodyHtml: String, attachments: Seq[Attachment] = null) = {
    there was one(application.injector.instanceOf[MailerService])
      .sendEmail(
        EmailAddress(application.configuration.get[String]("play.mail.from")),
        recipient
      )(
        subject,
        bodyHtml,
        attachments
      )
  }


  def mailMustNotHaveBeenSent() = {
    there was no(application.injector.instanceOf[MailerService]).sendEmail(EmailAddress(anyString), EmailAddress(anyString))(anyString, anyString, any)
  }

  def reportMustHaveBeenUpdatedWithStatus(status: ReportStatusValue) = {
    there was one(mockReportRepository).update(argThat(reportStatusMatcher(Some(status))))
  }

  def reportStatusMatcher(status: Option[ReportStatusValue]): org.specs2.matcher.Matcher[Report] = { report: Report =>
    (status == report.status, s"reportStatusList doesn't match ${status}")
  }

  def reportMustNotHaveBeenUpdated() = {
    there was no(mockReportRepository).update(any[Report])
  }

  def eventMustHaveBeenCreatedWithAction(action: ActionEventValue) = {
    there was one(mockEventRepository).createEvent(argThat(eventActionMatcher(action)))
  }

  def eventActionMatcher(action: ActionEventValue): org.specs2.matcher.Matcher[Event] = { event: Event =>
    (action == event.action, s"action doesn't match ${action}")
  }

  def eventMustNotHaveBeenCreated() = {
    there was no(mockEventRepository).createEvent(any[Event])
  }

}

trait GetReportContext extends Mockito {

  implicit val ec = ExecutionContext.global

  val siretForConcernedPro = "000000000000000"
  val siretForNotConcernedPro = "11111111111111"

  val neverRequestedReportUUID = UUID.randomUUID();
  val neverRequestedReport = Report(
    Some(neverRequestedReportUUID), "category", List("subcategory"), List(), None, "companyName", "companyAddress", Some(Departments.AUTHORIZED(0)), Some(siretForConcernedPro), Some(OffsetDateTime.now()),
    "firstName", "lastName", EmailAddress("email"), true, List(), None
  )

  val neverRequestedFinalReportUUID = UUID.randomUUID();
  val neverRequestedFinalReport = Report(
    Some(neverRequestedFinalReportUUID), "category", List("subcategory"), List(), None, "companyName", "companyAddress", Some(Departments.AUTHORIZED(0)), Some(siretForConcernedPro), Some(OffsetDateTime.now()),
    "firstName", "lastName", EmailAddress("email"), true, List(), Some(SIGNALEMENT_CONSULTE_IGNORE)
  )

  val alreadyRequestedReportUUID = UUID.randomUUID();
  val alreadyRequestedReport = Report(
    Some(alreadyRequestedReportUUID), "category", List("subcategory"), List(), None, "companyName", "companyAddress", Some(Departments.AUTHORIZED(0)), Some(siretForConcernedPro), Some(OffsetDateTime.now()),
    "firstName", "lastName", EmailAddress("email"), true, List(), None
  )

  val adminUser = User(UUID.randomUUID(), "admin@signalconso.beta.gouv.fr", "password", None, Some(EmailAddress("admin@signalconso.beta.gouv.fr")), Some("Prénom"), Some("Nom"), UserRoles.Admin)
  val adminLoginInfo = LoginInfo(CredentialsProvider.ID, adminUser.login)

  val concernedProUser = User(UUID.randomUUID(), siretForConcernedPro, "password", None, Some(EmailAddress("pro@signalconso.beta.gouv.fr")), Some("Prénom"), Some("Nom"), UserRoles.Pro)
  val concernedProLoginInfo = LoginInfo(CredentialsProvider.ID, concernedProUser.login)

  val notConcernedProUser = User(UUID.randomUUID(), siretForNotConcernedPro, "password", None, Some(EmailAddress("pro@signalconso.beta.gouv.fr")), Some("Prénom"), Some("Nom"), UserRoles.Pro)
  val notConcernedProLoginInfo = LoginInfo(CredentialsProvider.ID, notConcernedProUser.login)

  implicit val env: Environment[AuthEnv] = new FakeEnvironment[AuthEnv](Seq(adminLoginInfo -> adminUser, concernedProLoginInfo -> concernedProUser, notConcernedProLoginInfo -> notConcernedProUser))

  val mockReportRepository = mock[ReportRepository]
  val mockEventRepository = mock[EventRepository]
  val mockMailerService = mock[MailerService]

  mockReportRepository.getReport(neverRequestedReportUUID) returns Future(Some(neverRequestedReport))
  mockReportRepository.getReport(neverRequestedFinalReportUUID) returns Future(Some(neverRequestedFinalReport))
  mockReportRepository.getReport(alreadyRequestedReportUUID) returns Future(Some(alreadyRequestedReport))
  mockReportRepository.update(any[Report]) answers { report => Future(report.asInstanceOf[Report])}

  mockEventRepository.createEvent(any[Event]) answers { event => Future(event.asInstanceOf[Event]) }
  mockEventRepository.getEvents(neverRequestedReportUUID, EventFilter(None)) returns Future(List.empty)
  mockEventRepository.getEvents(neverRequestedFinalReportUUID, EventFilter(None)) returns Future(List.empty)
  mockEventRepository.getEvents(alreadyRequestedReportUUID, EventFilter(None)) returns Future(
    List(Event(Some(UUID.randomUUID()), Some(alreadyRequestedReportUUID), Some(concernedProUser.id), Some(OffsetDateTime.now()), EventType.PRO, ActionEvent.ENVOI_SIGNALEMENT))
  )

  class FakeModule extends AbstractModule with ScalaModule {
    override def configure() = {
      bind[Environment[AuthEnv]].toInstance(env)
      bind[ReportRepository].toInstance(mockReportRepository)
      bind[EventRepository].toInstance(mockEventRepository)
      bind[MailerService].toInstance(mockMailerService)
    }
  }

  lazy val application = new GuiceApplicationBuilder()
    .configure(
      Configuration(
        "play.evolutions.enabled" -> false,
        "slick.dbs.default.db.connectionPool" -> "disabled",
        "play.mailer.mock" -> true
      )
    )
    .overrides(new FakeModule())
    .build()

}