package controllers

import java.util.UUID

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject._
import models.{Subscription, SubscriptionCreation, SubscriptionUpdate, UserPermission}
import play.api.Logger
import play.api.libs.json.{JsError, Json}
import repositories.SubscriptionRepository
import utils.Country
import utils.silhouette.auth.{AuthEnv, WithPermission}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class SubscriptionController @Inject()(subscriptionRepository: SubscriptionRepository,
                                       val silhouette: Silhouette[AuthEnv])(implicit ec: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def createSubscription = SecuredAction(WithPermission(UserPermission.subscribeReports)).async(parse.json) { implicit request =>

    request.body.validate[SubscriptionCreation].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      draftSubscription => subscriptionRepository.create(
        Subscription(
          userId = Some(request.identity.id),
          email = None,
          departments = draftSubscription.departments,
          categories = draftSubscription.categories,
          tags = draftSubscription.tags,
          countries = draftSubscription.countries.map(Country.fromCode),
          sirets = draftSubscription.sirets,
          frequency = draftSubscription.frequency
        )
      ).map(subscription => Ok(Json.toJson(subscription)))
    )
  }

  def updateSubscription(uuid: UUID) = SecuredAction(WithPermission(UserPermission.subscribeReports)).async(parse.json) { implicit request =>
    request.body.validate[SubscriptionUpdate].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      draftSubscription =>
        for {
          subscriptions <- subscriptionRepository.list(request.identity.id)
          updatedSubscription <- subscriptions.find(_.id == uuid)
            .map(s => subscriptionRepository.update(
              s.copy(
                departments = draftSubscription.departments.getOrElse(s.departments),
                categories = draftSubscription.categories.getOrElse(s.categories),
                tags = draftSubscription.tags.getOrElse(s.tags),
                countries = draftSubscription.countries.map(_.map(Country.fromCode)).getOrElse(s.countries),
                sirets = draftSubscription.sirets.getOrElse(s.sirets),
                frequency = draftSubscription.frequency.getOrElse(s.frequency),
              )).map(Some(_))).getOrElse(Future(None))
        } yield if (updatedSubscription.isDefined) Ok(Json.toJson(updatedSubscription)) else NotFound
    )
  }

  def getSubscriptions = SecuredAction(WithPermission(UserPermission.subscribeReports)).async { implicit request =>
    subscriptionRepository.list(request.identity.id).map(subscriptions => Ok(Json.toJson(subscriptions)))
  }

  def getSubscription(uuid: UUID) = SecuredAction(WithPermission(UserPermission.subscribeReports)).async { implicit request =>
    subscriptionRepository.get(uuid).map(subscription =>
      subscription.filter(s => s.userId == Some(request.identity.id)).map(s => Ok(Json.toJson(s))).getOrElse(NotFound)
    )
  }

  def removeSubscription(uuid: UUID) = SecuredAction(WithPermission(UserPermission.subscribeReports)).async { implicit request =>
    for {
      subscriptions <- subscriptionRepository.list(request.identity.id)
      deletedCount <- subscriptions.find(_.id == uuid).map(subscription => subscriptionRepository.delete(subscription.id)).getOrElse(Future(0))
    } yield if (deletedCount > 0) Ok else NotFound
  }
}
