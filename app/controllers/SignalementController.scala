package controllers

import java.io.FileInputStream
import java.time.LocalDate
import java.util.UUID

import javax.inject.Inject
import models.Signalement
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.Files
import play.api.libs.json.Json
import play.api.libs.mailer.AttachmentFile
import play.api.mvc.MultipartFormData
import play.api.{Configuration, Logger}
import repositories.{FileRepository, SignalementRepository}
import services.MailerService

import scala.concurrent.{ExecutionContext, Future}

class SignalementController @Inject()(signalementRepository: SignalementRepository,
                                      fileRepository: FileRepository,
                                      mailerService: MailerService,
                                      configuration: Configuration)
                                     (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def createSignalement = Action.async(parse.multipartFormData) { implicit request =>

    logger.debug("createSignalement")

    SignalementForms.createSignalementForm.bindFromRequest(request.body.asFormUrlEncoded).fold(
      formWithErrors => treatFormErrors(formWithErrors),
      form => {
        for {
          signalement <- signalementRepository.create(
            Signalement(
              UUID.randomUUID(),
              form.typeEtablissement,
              form.categorieAnomalie,
              form.precisionAnomalie,
              form.nomEtablissement,
              form.adresseEtablissement,
              form.siretEtablissement,
              form.dateConstat,
              form.heureConstat,
              form.description,
              form.prenom,
              form.nom,
              form.email,
              form.accordContact,
              None,
              None)
            )
          ticketFileId <- addFile(request.body.file("ticketFile"))
          anomalieFileId <- addFile(request.body.file("anomalieFile"))
          signalement <- signalementRepository.update(signalement.copy(ticketFileId = ticketFileId, anomalieFileId = anomalieFileId))
          mail <- sendSignalementByMail(signalement, request.body.file("ticketFile"), request.body.file("anomalieFile"))
        } yield {
          Ok(Json.toJson(signalement))
        }
      }
    )
  }

  def treatFormErrors(formWithErrors: Form[SignalementForms.CreateSignalementForm]) = {
    logger.error(s"Error createSignalement ${formWithErrors.errors}")
    Future.successful(BadRequest(
      Json.obj("errors" ->
        Json.toJson(formWithErrors.errors.map(error => (error.key, error.message)))
      )
    ))
  }

  def addFile(fileToAdd: Option[MultipartFormData.FilePart[Files.TemporaryFile]]) = {
    logger.debug(s"file ${fileToAdd.map(_.filename)}")
    fileToAdd match {
      case Some(file) => fileRepository.uploadFile(new FileInputStream(file.ref))
      case None => Future(None)
    }
  }

  def sendSignalementByMail(signalement: Signalement, files: Option[MultipartFormData.FilePart[Files.TemporaryFile]]*) = {
    Future(mailerService.sendEmail(
      from = configuration.get[String]("play.mail.from"),
      recipients = configuration.get[String]("play.mail.contactRecipient"))(
      subject = "Nouveau signalement",
      bodyHtml = views.html.mails.signalementNotification(signalement).toString,
      attachments = files.filter(fileOption => fileOption.isDefined).map(file => AttachmentFile(file.get.filename, file.get.ref))
    ))
  }
}


object SignalementForms {

  case class CreateSignalementForm(
                              typeEtablissement: String,
                              categorieAnomalie: String,
                              precisionAnomalie: Option[String],
                              nomEtablissement: String,
                              adresseEtablissement: String,
                              siretEtablissement: Option[String],
                              dateConstat: LocalDate,
                              heureConstat: Option[Int],
                              description: Option[String],
                              prenom: String,
                              nom: String,
                              email: String,
                              accordContact: Boolean
                            )

  val createSignalementForm = Form(mapping(
    "typeEtablissement" -> nonEmptyText,
    "categorieAnomalie" -> nonEmptyText,
    "precisionAnomalie" -> optional(text),
    "nomEtablissement" -> nonEmptyText,
    "adresseEtablissement" -> nonEmptyText,
    "siretEtablissement" -> optional(text),
    "dateConstat" -> localDate("yyyy-MM-dd"),
    "heureConstat" -> optional(number),
    "description" -> optional(text),
    "prenom" -> nonEmptyText,
    "nom" -> nonEmptyText,
    "email" -> email,
    "accordContact" -> boolean
  )(CreateSignalementForm.apply)(CreateSignalementForm.unapply))

}
