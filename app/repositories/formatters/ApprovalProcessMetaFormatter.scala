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

package repositories.formatters

import java.time.LocalDate

import models.{ApprovalProcessMeta, MongoDateTimeFormats}
import play.api.libs.json._

object ApprovalProcessMetaFormatter {

  implicit val dateFormat: Format[LocalDate] = MongoDateTimeFormats.localDateFormats

  val read: JsValue => JsResult[ApprovalProcessMeta] = json =>
    for {
      id <- (json \ "id").validate[String]
      status <- (json \ "status").validate[String]
      title <- (json \ "title").validate[String]
      dateSubmitted <- (json \ "dateSubmitted").validate[LocalDate]
    } yield ApprovalProcessMeta(id, title, status, dateSubmitted)

  val write: ApprovalProcessMeta => JsObject = meta =>
    Json.obj(
      "id" -> meta.id,
      "status" -> meta.status,
      "title" -> meta.title,
      "dateSubmitted" -> meta.dateSubmitted
    )

  val mongoFormat: OFormat[ApprovalProcessMeta] = OFormat(read, write)
}