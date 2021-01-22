/*
 * Copyright 2021 HM Revenue & Customs
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

import services.shared._
import models.ocelot._
import models.errors.Error
import models.ocelot.errors._
import models.RequestOutcome
import models.ocelot.Process
import play.api.libs.json._
import config.AppConfig

package object services {
  def guidancePages(pageBuilder: PageBuilder, securedProcessBuilder: SecuredProcessBuilder, jsObject: JsObject)
                   (implicit c: AppConfig): RequestOutcome[(Process, Seq[Page], JsObject)] =
    jsObject.validate[Process].fold(
      errs => Left(Error(GuidanceError.fromJsonValidationErrors(errs))),
      p => {
        val process = fakeWelshText(p.passPhrase.fold(p)(_ => securedProcessBuilder.secure(p)))
        pageBuilder.pagesWithValidation(process, process.startPageId).fold(
          errs => Left(Error(errs)),
          pages => Right((process, pages, Json.toJsObject(process)))
        )
      }
    )

  def fakeWelshText(process: Process)(implicit c: AppConfig): Process =
    if (!process.passPhrase.isEmpty || c.fakeWelshInUnauthenticatedGuidance)
      process.copy(phrases = process.phrases.map(p => if (p.welsh.trim.isEmpty) Phrase(p.english, s"Welsh, ${p.english}") else p))
    else process
}
