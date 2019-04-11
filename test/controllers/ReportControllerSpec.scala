package controllers

import com.mohiva.play.silhouette.api.Silhouette
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.{Configuration, Environment}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers, WithApplication}
import repositories.{FileRepository, ReportRepository}
import services.{MailerService, S3Service}
import utils.silhouette.AuthEnv

class ReportControllerSpec(implicit ee: ExecutionEnv) extends Specification with Results with Mockito {

  "ReportController" should {

    "return a BadRequest with errors if report is invalid" in new Context {
      new WithApplication(application) {

        val jsonBody = Json.toJson("category" -> "")

        val request = FakeRequest("POST", "/api/reports").withJsonBody(jsonBody)

        val controller = new ReportController(mock[ReportRepository], mock[FileRepository], mock[MailerService], mock[S3Service], mock[Silhouette[AuthEnv]], mock[Configuration], mock[Environment]){
          override def controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
        }

        val result = route(application, request).get

        status(result) must beEqualTo(BAD_REQUEST)
        //contentAsJson(result) must beEqualTo(Json.obj("errors" -> ""))
      }
    }

  }

  trait Context extends Scope {

    lazy val application = new GuiceApplicationBuilder()
      .configure(Configuration("play.evolutions.enabled" -> false))
      .build()

  }

}
