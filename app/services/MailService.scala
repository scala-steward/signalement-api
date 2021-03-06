package services

import java.net.URI

import actors.EmailActor
import akka.actor.ActorRef
import akka.pattern.ask
import javax.inject.{Inject, Named}
import models.{EmailValidation, Report}
import play.api.mvc.Request
import play.api.{Configuration, Logger}
import utils.Constants.Tags
import utils.{EmailAddress, EmailSubjects}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class MailService @Inject()(
  @Named("email-actor") emailActor: ActorRef,
  configuration: Configuration,
)(
  private[this] implicit val executionContext: ExecutionContext
) {

  private[this]          val logger                     = Logger(this.getClass)
  private[this]          val mailFrom                   = configuration.get[EmailAddress]("play.mail.from")
  private[this]          val tokenDuration              = configuration.getOptional[String]("play.tokens.duration").map(java.time.Period.parse(_))
  private[this] implicit val websiteUrl                 = configuration.get[URI]("play.website.url")
  private[this] implicit val contactAddress             = configuration.get[EmailAddress]("play.mail.contactAddress")
  private[this] implicit val ccrfEmailSuffix            = configuration.get[String]("play.mail.ccrfEmailSuffix")
  private[this] implicit val timeout: akka.util.Timeout = 5.seconds

  def sendConsumerEmailConfirmation(email: EmailValidation)(implicit request: Request[Any]) = {
    emailActor ? EmailActor.EmailRequest(
      from = mailFrom,
      recipients = Seq(email.email),
      subject = EmailSubjects.VALIDATE_EMAIL,
      bodyHtml = views.html.mails.consumer.confirmEmail(email.email, email.confirmationCode).toString
    )
  }

  def sendDangerousProductEmail(emails: Seq[EmailAddress], report: Report) = {
    emailActor ? EmailActor.EmailRequest(
      from = mailFrom,
      recipients = emails,
      subject = EmailSubjects.REPORT_NOTIF_DGCCRF(1, Some("[Produits dangereux] ")),
      bodyHtml = views.html.mails.dgccrf.reportDangerousProductNotification(report).toString
    )
  }
}
