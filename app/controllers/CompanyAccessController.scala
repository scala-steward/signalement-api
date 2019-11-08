package controllers

import javax.inject.{Inject, Singleton}
import repositories._
import models._
import orchestrators.CompanyAccessOrchestrator
import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Future}
import com.mohiva.play.silhouette.api.Silhouette
import utils.silhouette.auth.AuthEnv

@Singleton
class CompanyAccessController @Inject()(
                                val companyRepository: CompanyRepository,
                                val companyAccessRepository: CompanyAccessRepository,
                                val companyAccessOrchestrator: CompanyAccessOrchestrator,
                                val silhouette: Silhouette[AuthEnv]
                              )(implicit ec: ExecutionContext)
 extends BaseCompanyController {

  def listAccesses(siret: String) = withCompany(siret, List(AccessLevel.ADMIN)).async { implicit request =>
    for {
      userAccesses <- companyAccessRepository.fetchUsersWithLevel(request.company)
    } yield Ok(Json.toJson(userAccesses.map{
      case (user, level) => Map(
          "firstName" -> user.firstName.getOrElse("—"),
          "lastName"  -> user.lastName.getOrElse("—"),
          "email"     -> user.email.getOrElse("—"),
          "level"     -> level.value
      )
    }))
  }

  case class AccessInvitation(email: String, level: AccessLevel)

  def sendInvitation(siret: String) = withCompany(siret, List(AccessLevel.ADMIN)).async(parse.json) { implicit request =>
    implicit val reads = Json.reads[AccessInvitation]
    request.body.validate[AccessInvitation].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      invitation => companyAccessOrchestrator
                    .sendInvitation(request.company, invitation.email, invitation.level, request.identity)
                    .map(_ => Ok)
    )
  }

  def fetchTokenInfo(siret: String, token: String) = UnsecuredAction.async { implicit request =>
    for {
      company <- companyRepository.findBySiret(siret)
      token   <- company.map(companyAccessRepository.findToken(_, token))
                        .getOrElse(Future(None))
    } yield token.flatMap(t => company.map(c => 
      Ok(Json.toJson(TokenInfo(t.token, c.siret)))
    )).getOrElse(NotFound)
  }
}
