package controllers

import javax.inject._

import actors.{XLSActor, XLSName}
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import argonaut.Argonaut._
import argonaut._
import common.{ExtraJsonHelpers, Shared, myTypes}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._

import scala.concurrent.duration._

@Singleton
class UploadController @Inject() (system: ActorSystem) extends Controller with myTypes with ExtraJsonHelpers {

  implicit val timeout: Timeout = 5.seconds

  val r = Shared.r

  val xlsActor = system.actorOf(XLSActor.props, "xls-actor")

  private def errorJson(reason: String): Json = Json.obj("error" -> jString(reason))

  private def successJson(reason: String): Json = Json.obj("id" -> jString(reason))

  def uploadFile = Action(parse.multipartFormData) { request =>
    val uploadType = request.body.dataParts("type").head
    request.body.file("fileUpload").map { picture =>
      import java.io.File
      val filename = picture.filename
      val randname = r.nextInt(999999)
      val contentType = picture.contentType
      val ret =
        if (contentType.nonEmpty && contentType.get.endsWith("ms-excel")) {
          val savedName = s"$randname-$filename"
          val fileNode = new File(s"/tmp/$savedName.upload")
          picture.ref.moveTo(fileNode)
          (xlsActor ? XLSName(uploadType, savedName, fileNode)).mapTo[String].map { message: String =>
            Logger.debug(s"got $uploadType")
            Logger.debug(message)
          }
          successJson(savedName)
        } else
          errorJson("bad type")
      Ok(ret)
    }.getOrElse {
      Redirect(routes.QueryController.index()).flashing(
        "error" -> "Missing file")
    }
  }
}