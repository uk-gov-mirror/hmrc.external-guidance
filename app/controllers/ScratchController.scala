/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import java.util.UUID
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, ControllerComponents, AnyContent}
import services.ScratchService
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Try,Success,Failure}

@Singleton()
class ScratchController @Inject() (scratchService: ScratchService, cc: ControllerComponents) extends BackendController(cc) {

  def save(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    val process = request.body.as[JsObject]
    scratchService.save(process).map { id =>
      Created(Json.obj("id" -> id.toString))
    }
  }

  def get(uuidString: String): Action[AnyContent] = Action.async { _ =>
    Try{UUID.fromString(uuidString)} match {
      case Success(uuid) =>
        scratchService.getByUuid(uuid).map {
          case Some(jsObject) => Ok(jsObject)
          case None => NotFound
        }
      case Failure(_) => Future.successful(NotFound)
    }
  }

}
