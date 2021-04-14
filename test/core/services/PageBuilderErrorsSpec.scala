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

package core.services

import base.BaseSpec
import core.models.errors._
import core.models.ocelot.errors._
import core.models.ocelot.stanzas._
import core.models.ocelot._
import play.api.libs.json._

class PageBuilderErrorsSpec extends BaseSpec with ProcessJson {
  // Define instance of class used in testing
  val pageBuilder: PageBuilder = new PageBuilder(new Placeholders(new DefaultTodayProvider))

  val meta: Meta = Json.parse(prototypeMetaSection).as[Meta]

  case object DummyStanza extends Stanza {
    override val next: Seq[String] = Seq("1")
  }

  "PageBuilder error handling" must {

    val flow = Map(
      Process.StartStanzaId -> ValueStanza(List(Value(ScalarType, "PageUrl", "/blah")), Seq("1"), false),
      "1" -> InstructionStanza(0, Seq("2"), None, false),
      "2" -> DummyStanza
    )

    "detect IncompleteDateInputPage error" in {
      val flow = Map(
        Process.StartStanzaId -> PageStanza("/url", Seq("1"), true),
        "1" -> InstructionStanza(0, Seq("2"), None, false),
        "2" -> InputStanza(Currency, Seq("4"), 0, Some(0), "Label", None, false),
        "4" -> Choice(ChoiceStanza(Seq("5","end"), Seq(ChoiceStanzaTest("[label:label]", LessThanOrEquals, "8")), false)),
        "5" -> PageStanza("/url2", Seq("1"), true),
        "6" -> InstructionStanza(0, Seq("2"), None, false),
        "2" -> InputStanza(Date, Seq("4"), 0, Some(0), "Label", None, false),
        "end" -> EndStanza
      )
      val process = Process(
        metaSection,
        flow,
        Vector[Phrase](
          Phrase(Vector("Some Text", "Welsh, Some Text")),
          Phrase(Vector("Some Text1", "Welsh, Some Text1")),
          Phrase(Vector("Some Text2", "Welsh, Some Text2")),
          Phrase(Vector("Some Text3", "Welsh, Some Text3"))
        ),
        Vector[Link]()
      )
      pageBuilder.pagesWithValidation(process, Process.StartStanzaId) match {
        case Left(Seq(IncompleteDateInputPage("5"), IncompleteDateInputPage("start"))) => succeed
        case err => fail(s"IncompleteDateInputPage not detected $err")
      }
    }

    "detect StanzaNotFound error" in {
      val process = Process(metaSection, flow, Vector[Phrase](), Vector[Link]())

      pageBuilder.buildPage("4", process) match {
        case Left(StanzaNotFound("4")) => succeed
        case _ => fail("Unknown stanza not detected")
      }
    }

    "detect PageStanzaMissing error when stanza routes to page not starting with PageStanza" in {
      val flow = Map(
        Process.StartStanzaId -> InstructionStanza(0, Seq("2"), None, false),
        "2" -> QuestionStanza(1, Seq(2, 3), Seq("4", "5"), None, false),
        "4" -> InstructionStanza(0, Seq("end"), None, false),
        "5" -> InstructionStanza(0, Seq("end"), None, false),
        "end" -> EndStanza
      )
      val process = Process(
        metaSection,
        flow,
        Vector[Phrase](
          Phrase(Vector("Some Text", "Welsh, Some Text")),
          Phrase(Vector("Some Text1", "Welsh, Some Text1")),
          Phrase(Vector("Some Text2", "Welsh, Some Text2")),
          Phrase(Vector("Some Text3", "Welsh, Some Text3"))
        ),
        Vector[Link]()
      )

      pageBuilder.pagesWithValidation(process) match {
        case Left(List(PageStanzaMissing("start"))) => succeed
        case Left(err) => fail(s"Missing PageStanza, failed with $err")
        case x => fail(s"Missing PageStanza with $x")
      }
    }

    "detect VisualStanzasAfterQuestion error when Question stanzas followed by UI stanzas" in {
      val flow = Map(
        Process.StartStanzaId -> PageStanza("/url", Seq("1"), true),
        "1" -> InstructionStanza(0, Seq("2"), None, false),
        "2" -> QuestionStanza(1, Seq(2, 3), Seq("4", "5"), None, false),
        "4" -> InstructionStanza(0, Seq("end"), None, false),
        "5" -> InstructionStanza(0, Seq("end"), None, false),
        "end" -> EndStanza
      )
      val process = Process(
        metaSection,
        flow,
        Vector[Phrase](
          Phrase(Vector("Some Text", "Welsh, Some Text")),
          Phrase(Vector("Some Text1", "Welsh, Some Text1")),
          Phrase(Vector("Some Text2", "Welsh, Some Text2")),
          Phrase(Vector("Some Text3", "Welsh, Some Text3"))
        ),
        Vector[Link]()
      )

      pageBuilder.pagesWithValidation(process) match {
        case Left(List(VisualStanzasAfterQuestion("4"))) => succeed
        case Left(err) => fail(s"Should generate VisualStanzasAfterQuestion, failed with $err")
        case x => fail(s"Should generate VisualStanzasAfterQuestion, returned $x")
      }
    }

    "detect VisualStanzasAfterQuestion with possible loop in post Question stanzas" in {
      val flow = Map(
        Process.StartStanzaId -> PageStanza("/url", Seq("1"), true),
        "1" -> InstructionStanza(0, Seq("2"), None, false),
        "2" -> QuestionStanza(1, Seq(2, 3, 4, 5), Seq("4", "5", "6", "7"), None, false),
        "4" -> Choice(ChoiceStanza(Seq("5","6"), Seq(ChoiceStanzaTest("[label:X]", LessThanOrEquals, "8")), false)),
        "5" -> ValueStanza(List(Value(ScalarType, "PageUrl", "/blah")), Seq("4"), false),
        "6" -> InstructionStanza(0, Seq("end"), None, false),
        "7" -> InstructionStanza(0, Seq("end"), None, false),
        "end" -> EndStanza
      )
      val process = Process(
        metaSection,
        flow,
        Vector[Phrase](
          Phrase(Vector("Some Text", "Welsh, Some Text")),
          Phrase(Vector("Some Text1", "Welsh, Some Text1")),
          Phrase(Vector("Some Text2", "Welsh, Some Text2")),
          Phrase(Vector("Some Text3", "Welsh, Some Text3")),
          Phrase(Vector("Some Text4", "Welsh, Some Text4")),
          Phrase(Vector("Some Text5", "Welsh, Some Text5"))
        ),
        Vector[Link]()
      )

      pageBuilder.pagesWithValidation(process) match {
        case Left(List(VisualStanzasAfterQuestion("6"))) => succeed
        case Left(err) => fail(s"Should generate VisualStanzasAfterQuestion, failed with $err")
        case x => fail(s"Should generate VisualStanzasAfterQuestion, returned $x")
      }
    }

    "detect PageUrlEmptyOrInvalid error when PageValue is present but url is blank" in {
      val flow = Map(
        Process.StartStanzaId -> PageStanza("", Seq("1"), false),
        "1" -> InstructionStanza(0, Seq("2"), None, false),
        "2" -> QuestionStanza(1, Seq(2, 3), Seq("4", "5"), None, false),
        "4" -> InstructionStanza(0, Seq("end"), None, false),
        "5" -> InstructionStanza(0, Seq("end"), None, false),
        "end" -> EndStanza
      )
      val process = Process(
        metaSection,
        flow,
        Vector[Phrase](
          Phrase(Vector("Some Text", "Welsh, Some Text")),
          Phrase(Vector("Some Text1", "Welsh, Some Text1")),
          Phrase(Vector("Some Text2", "Welsh, Some Text2")),
          Phrase(Vector("Some Text3", "Welsh, Some Text3"))
        ),
        Vector[Link]()
      )

      pageBuilder.pagesWithValidation(process) match {
        case Left(List(PageUrlEmptyOrInvalid(Process.StartStanzaId))) => succeed
        case Left(err) => fail(s"Missing ValueStanza containing PageUrl value not detected, failed with $err")
        case _ => fail(s"Missing ValueStanza containing PageUrl value not detected")
      }
    }

    "detect PageUrlEmptyOrInvalid error when PageStanza url is /" in {
      val invalidProcess = invalidOnePageJson.as[Process]

      pageBuilder.pagesWithValidation(invalidProcess) match {
        case Left(List(PageUrlEmptyOrInvalid("4"))) => succeed
        case Left(err) => fail(s"PageStanza url equal to / not detected, failed with $err")
        case _ => fail(s"PageStanza url equal to / not detected")
      }
    }

    "detect PhraseNotFound in QuestionStanza text" in {
      val flow = Map(
        Process.StartStanzaId -> PageStanza("Blah", Seq("1"), false),
        "1" -> InstructionStanza(0, Seq("2"), None, false),
        "2" -> QuestionStanza(four, Seq(two, three), Seq("4", "5"), None, false),
        "4" -> InstructionStanza(0, Seq("end"), None, false),
        "5" -> InstructionStanza(0, Seq("end"), None, false),
        "end" -> EndStanza
      )
      val process = Process(
        metaSection,
        flow,
        Vector[Phrase](
          Phrase(Vector("Some Text", "Welsh, Some Text")),
          Phrase(Vector("Some Text1", "Welsh, Some Text1")),
          Phrase(Vector("Some Text2", "Welsh, Some Text2")),
          Phrase(Vector("Some Text3", "Welsh, Some Text3"))
        ),
        Vector[Link]()
      )

      pageBuilder.pagesWithValidation(process) match {
        case Left(List(PhraseNotFound(id, four))) => succeed
        case Left(err) => fail(s"Missing PhraseNotFound(4) with error $err")
        case Right(_) => fail(s"Missing PhraseNotFound(4)")
      }
    }

    "detect PhraseNotFound in QuestionStanza answers" in {
      val flow = Map(
        Process.StartStanzaId -> PageStanza("Blah", Seq("1"), false),
        "1" -> InstructionStanza(0, Seq("2"), None, false),
        "2" -> QuestionStanza(1, Seq(four, three), Seq("4", "5"), None, false),
        "4" -> InstructionStanza(0, Seq("end"), None, false),
        "5" -> InstructionStanza(0, Seq("end"), None, false),
        "end" -> EndStanza
      )
      val process = Process(
        metaSection,
        flow,
        Vector[Phrase](
          Phrase(Vector("Some Text", "Welsh, Some Text")),
          Phrase(Vector("Some Text1", "Welsh, Some Text1")),
          Phrase(Vector("Some Text2", "Welsh, Some Text2")),
          Phrase(Vector("Some Text3", "Welsh, Some Text3"))
        ),
        Vector[Link]()
      )

      pageBuilder.pagesWithValidation(process) match {
        case Left(List(PhraseNotFound(id, four))) => succeed
        case Left(err) => fail(s"Missing PhraseNotFound(4) with error $err")
        case Right(_) => fail(s"Missing PhraseNotFound(4)")
      }
    }

    "detect PhraseNotFound in InstructionStanza" in {
      val flow = Map(
        Process.StartStanzaId -> PageStanza("Blah", Seq("1"), false),
        "1" -> InstructionStanza(2, Seq("end"), None, false),
        "end" -> EndStanza
      )
      val process = Process(metaSection, flow, Vector[Phrase](Phrase(Vector("Some Text", "Welsh, Some Text"))), Vector[Link]())

      pageBuilder.pagesWithValidation(process) match {
        case Left(List(PhraseNotFound(id, 2))) => succeed
        case Left(err) => fail(s"Missing PhraseNotFound(2) with error $err")
        case Right(_) => fail(s"Missing PhraseNotFound(2)")
      }
    }

    "detect PhraseNotFound in InputStanza name" in {
      val flow = Map(
        Process.StartStanzaId -> PageStanza("Blah", Seq("1"), false),
        "1" -> InputStanza(Currency, Seq("end"), 2, Some(3), "Label", None, false),
        "end" -> EndStanza
      )
      val process = Process(metaSection, flow, Vector[Phrase](Phrase(Vector("Some Text", "Welsh, Some Text"))), Vector[Link]())

      pageBuilder.pagesWithValidation(process) match {
        case Left(List(PhraseNotFound(id, 2))) => succeed
        case Left(err) => fail(s"Missing PhraseNotFound(2) with error $err")
        case Right(_) => fail(s"Missing PhraseNotFound(2)")
      }
    }

    "detect PhraseNotFound in InputStanza help" in {
      val flow = Map(
        Process.StartStanzaId -> PageStanza("Blah", Seq("1"), false),
        "1" -> InputStanza(Currency, Seq("end"), 0, Some(3), "Label", None, false),
        "end" -> EndStanza
      )
      val process = Process(metaSection, flow, Vector[Phrase](Phrase(Vector("Some Text", "Welsh, Some Text"))), Vector[Link]())

      pageBuilder.pagesWithValidation(process) match {
        case Left(List(PhraseNotFound(id, 3))) => succeed
        case Left(err) => fail(s"Missing PhraseNotFound(3) with error $err")
        case Right(_) => fail(s"Missing PhraseNotFound(3)")
      }
    }

    "detect PhraseNotFound in InputStanza placeholder" in {
      val flow = Map(
        Process.StartStanzaId -> PageStanza("Blah", Seq("1"), false),
        "1" -> InputStanza(Currency, Seq("end"), 0, Some(0), "Label", Some(3), false),
        "end" -> EndStanza
      )
      val process = Process(metaSection, flow, Vector[Phrase](Phrase(Vector("Some Text", "Welsh, Some Text"))), Vector[Link]())

      pageBuilder.pagesWithValidation(process) match {
        case Left(List(PhraseNotFound(id, 3))) => succeed
        case Left(err) => fail(s"Missing PhraseNotFound(3) with error $err")
        case Right(_) => fail(s"Missing PhraseNotFound(3)")
      }
    }

    "detect PhraseNotFound in CalloutStanza" in {
      val flow = Map(
        Process.StartStanzaId -> PageStanza("Blah", Seq("1"), false),
        "1" -> CalloutStanza(Title, 2, Seq("end"), false),
        "end" -> EndStanza
      )
      val process = Process(metaSection, flow, Vector[Phrase](Phrase(Vector("Some Text", "Welsh, Some Text"))), Vector[Link]())

      pageBuilder.pagesWithValidation(process) match {
        case Left(List(PhraseNotFound(id, 2))) => succeed
        case Left(err) => fail(s"Missing PhraseNotFound(2) with error $err")
        case Right(_) => fail(s"Missing PhraseNotFound(2)")
      }
    }

    "detect LinkNotFound(1) in InstructionStanza" in {

      val flow: Map[String, Stanza] = Map(
        Process.StartStanzaId -> PageStanza("Blah", Seq("1"), false),
        "1" -> InstructionStanza(0, Seq("2"), None, false),
        "2" -> InstructionStanza(1, Seq("end"), Some(1), false),
        "end" -> EndStanza
      )

      // Create process with single link
      val process: Process = Process(
        metaSection,
        flow,
        Vector[Phrase](Phrase(Vector("First English phrase", "First Welsh Phrase")), Phrase(Vector("Second English Phrase", "Second Welsh Phrase"))),
        Vector[Link](Link(0, "http://my.com/search", "MyCOM Search Engine", false))
      )

      pageBuilder.pagesWithValidation(process) match {
        case Left(List(LinkNotFound(id, 1))) => succeed
        case Left(err) => fail(s"Missing LinkNotFound error. Actual error raised is $err")
        case Right(_) => fail("Page building terminated successfully when LinkNotFound error expected")
      }
    }

    "detect DuplicatePageUrl" in {
      val flow = Map(
        Process.StartStanzaId -> PageStanza("/this", Seq("1"), false),
        "1" -> InstructionStanza(0, Seq("2"), None, false),
        "2" -> QuestionStanza(1, Seq(2, 3), Seq("4", "5"), None, false),
        "4" -> PageStanza("/this", Seq("5"), false),
        "5" -> PageStanza("/that", Seq("end"), false),
        "end" -> EndStanza
      )
      val process = Process(
        metaSection,
        flow,
        Vector[Phrase](
          Phrase(Vector("Some Text", "Welsh, Some Text")),
          Phrase(Vector("Some Text1", "Welsh, Some Text1")),
          Phrase(Vector("Some Text2", "Welsh, Some Text2")),
          Phrase(Vector("Some Text3", "Welsh, Some Text3"))
        ),
        Vector[Link]()
      )

      pageBuilder.pagesWithValidation(process) match {
        case Left(List(DuplicatePageUrl("4", "/this"))) => succeed
        case Left(err) => fail(s"DuplicatePageUrl error not detected, failed with $err")
        case res => fail(s"DuplicatePageUrl not detected $res")
      }
    }

    "detect UseOfReservedUrl" in {
      val flow = Map(
        Process.StartStanzaId -> PageStanza("/this", Seq("1"), false),
        "1" -> InstructionStanza(0, Seq("2"), None, false),
        "2" -> QuestionStanza(1, Seq(2, 3), Seq("4", "5"), None, false),
        "4" -> PageStanza("/session-restart", Seq("5"), false),
        "5" -> PageStanza("/session-timeout", Seq("end"), false),
        "end" -> EndStanza
      )
      val process = Process(
        metaSection,
        flow,
        Vector[Phrase](
          Phrase(Vector("Some Text", "Welsh, Some Text")),
          Phrase(Vector("Some Text1", "Welsh, Some Text1")),
          Phrase(Vector("Some Text2", "Welsh, Some Text2")),
          Phrase(Vector("Some Text3", "Welsh, Some Text3"))
        ),
        Vector[Link]()
      )

      pageBuilder.pagesWithValidation(process) match {
        case Left(List(UseOfReservedUrl("4"), UseOfReservedUrl("5"))) => succeed
        case Left(err) => fail(s"UseOfReservedUrl error not detected, failed with $err")
        case Right(res) => fail(s"UseOfReservedUrl not detected $res")
      }
    }

    "detect multiple DuplicatePageUrl" in {
      duplicateUrlsJson.validate[Process] match {
        case JsSuccess(process, _) =>
          pageBuilder.pagesWithValidation(process) match {
            case Left(List(DuplicatePageUrl("6","/feeling-bad"), DuplicatePageUrl("8","/feeling-good"))) => succeed
            case Left(err) => fail(s"DuplicatePageUrl error not detected, failed with $err")
            case res => fail(s"DuplicatePageUrl not detected $res")
          }

        case JsError(errs) => fail(s"Errors reported $errs")
      }

    }

    "detect MissingWelshText" in {
      val flow = Map(
        Process.StartStanzaId -> PageStanza("/this", Seq("1"), false),
        "1" -> InstructionStanza(0, Seq("2"), None, false),
        "2" -> QuestionStanza(1, Seq(2, 3), Seq("4", "5"), None, false),
        "4" -> PageStanza("/that", Seq("5"), false),
        "5" -> InstructionStanza(0, Seq("end"), None, false),
        "end" -> EndStanza
      )
      val process = Process(
        metaSection,
        flow,
        Vector[Phrase](
          Phrase(Vector("Some Text", "Welsh, Some Text")),
          Phrase(Vector("Some Text1", "")),
          Phrase(Vector("Some Text2", "Welsh, Some Text2")),
          Phrase(Vector("Some Text3", "Welsh, Some Text3"))
        ),
        Vector[Link]()
      )
      pageBuilder.pagesWithValidation(process) match {
        case Left(List(MissingWelshText("2", _, "Some Text1"))) => succeed
        case Left(err) => fail(s"MissingWelshText error not detected, failed with $err")
        case _ => fail(s"MissingWelshText not detected")
      }
    }

    "detect InconsistentQuestionError" in {
      val flow = Map(
        Process.StartStanzaId -> PageStanza("/this", Seq("1"), false),
        "1" -> InstructionStanza(0, Seq("2"), None, false),
        "2" -> QuestionStanza(1, Seq(2, 3), Seq("4"), None, false),
        "4" -> PageStanza("/that", Seq("5"), false),
        "5" -> InstructionStanza(0, Seq("end"), None, false),
        "end" -> EndStanza
      )
      val process = Process(
        metaSection,
        flow,
        Vector[Phrase](
          Phrase(Vector("Some Text", "Welsh, Some Text")),
          Phrase(Vector("Some Text1", "Welsh, Some Text1")),
          Phrase(Vector("Some Text2", "Welsh, Some Text2")),
          Phrase(Vector("Some Text3", "Welsh, Some Text3"))
        ),
        Vector[Link]()
      )
      pageBuilder.pagesWithValidation(process) match {
        case Left(List(InconsistentQuestionError("2"))) => succeed
        case Left(err) => fail(s"InconsistentQuestionError error not detected, failed with $err")
        case _ => fail(s"InconsistentQuestionError not detected")
      }
    }

    "detect UnknownCalloutType" in {
      val processErrors: List[ProcessError] =
        List(
          ProcessError("Unsupported InputStanza type UnknownInputType found at stanza id 34","34"),
          ProcessError("Process Links section parse error, reason: error.path.missing, index: 0",""),
          ProcessError("Process Phrases section parse error, reason: error.minLength, index: 5",""),
          ProcessError("Unsupported stanza type UnknownStanza found at stanza id 2","2"),
          ProcessError("""Process Flow section parse error, reason: 'type' is undefined on object:"""+
           """ {"next":["end"],"noteType":"Error","stack":false,"text":59}, stanzaId: 5, target: /flow/5""","5"),
          ProcessError("Unsupported CalloutStanza type UnknownType found at stanza id 4","4"),
          ProcessError("Unsupported ValueStanza Value type AnUnknownType found at stanza id 33","33"),
          ProcessError("Process Meta section parse error, reason: error.path.missing, target: ocelot",""))

      val jsObject = assortedParseErrorsJson
      val result = jsObject.as[JsObject].validate[Process].fold(
        errs => Left(core.models.errors.Error(GuidanceError.fromJsonValidationErrors(errs))),
        process => {
          pageBuilder.pagesWithValidation(process, process.startPageId).fold(errs => Left(core.models.errors.Error(errs)),
            pages => Right((process, pages, jsObject))
        )}
      )

      result.fold(
        {
          case core.models.errors.Error(core.models.errors.Error.UnprocessableEntity, None, Some(errors)) if errors == processErrors => succeed
          case errs => fail(s"Failed with errors: $errs")
        }, _ => fail)
    }

    "pass as valid exclusive sequence with a single exclusive option" in {

      val flow: Map[String, Stanza] = Map(
        Process.StartStanzaId -> PageStanza("/start", Seq("10"), stack = false),
        "10" -> CalloutStanza(TypeError, 2, Seq("1"), false),
        "1" -> InstructionStanza(0, Seq("2"), None, stack = false),
        "2" -> SequenceStanza(1, Seq("3", "5", "7"), Seq(2, 3), None, stack = false),
        "3" -> PageStanza("/page-1", Seq("4"), stack = false),
        "4" -> InstructionStanza(0, Seq("end"), None, stack = false),
        "5" -> PageStanza("/page-2", Seq("6"), stack = false),
        "6" -> InstructionStanza(0, Seq("end"), None, stack = false),
        "7" -> PageStanza("/page-3", Seq("8"), stack = false),
        "8" -> InstructionStanza(0, Seq("end"), None, stack = false),
        "end" -> EndStanza
      )

      val process = Process(
        metaSection,
        flow,
        Vector[Phrase](
          Phrase(Vector("Some Text", "Welsh, Some Text")),
          Phrase(Vector("Exclusive sequence stanza", "Welsh, Exclusive sequence stanza")),
          Phrase(Vector("Some Text2", "Welsh, Some Text2")),
          Phrase(Vector("Some Text3 [exclusive]", "Welsh, Some Text3"))
        ),
        Vector[Link]()
      )

      pageBuilder.pagesWithValidation(process) match {
        case Right(_) => succeed
        case Left(err) => fail(s"Valid sequence stanza definition should pass validation, but failed with error $err")
      }

    }

    "detect multiple exclusive options in a sequence stanza" in {

      val flow: Map[String, Stanza] = Map(
        Process.StartStanzaId -> PageStanza("/start", Seq("10"), stack = false),
        "10" -> CalloutStanza(TypeError, 2, Seq("1"), false),
        "1" -> InstructionStanza(0, Seq("2"), None, stack = false),
        "2" -> SequenceStanza(1, Seq("3", "5", "7"), Seq(2, 3), None, stack = false),
        "3" -> PageStanza("/page-1", Seq("4"), stack = false),
        "4" -> InstructionStanza(0, Seq("end"), None, stack = false),
        "5" -> PageStanza("/page-2", Seq("6"), stack = false),
        "6" -> InstructionStanza(0, Seq("end"), None, stack = false),
        "7" -> PageStanza("/page-3", Seq("8"), stack = false),
        "8" -> InstructionStanza(0, Seq("end"), None, stack = false),
        "end" -> EndStanza
      )

      val process = Process(
        metaSection,
        flow,
        Vector[Phrase](
          Phrase(Vector("Some Text", "Welsh, Some Text")),
          Phrase(Vector("Exclusive sequence stanza", "Welsh, Exclusive sequence stanza")),
          Phrase(Vector("Some Text2 [exclusive]", "Welsh, Some Text2")),
          Phrase(Vector("Some Text3 [exclusive]", "Welsh, Some Text3"))
        ),
        Vector[Link]()
      )

      pageBuilder.pagesWithValidation(process) match {
        case Left(List(MultipleExclusiveOptionsError("2"))) => succeed
        case Left(err) => fail(s"Failed to detect multiple exclusive options. Instead failed with error $err")
        case _ => fail("Failed to detect multiple exclusive options")
      }
    }

    "detect missing TypeError callout in an exclusive sequence stanza" in {

      val flow: Map[String, Stanza] = Map(
        Process.StartStanzaId -> PageStanza("/start", Seq("1"), stack = false),
        "1" -> InstructionStanza(0, Seq("2"), None, stack = false),
        "2" -> SequenceStanza(1, Seq("3", "5", "7"), Seq(2, 3), None, stack = false),
        "3" -> PageStanza("/page-1", Seq("4"), stack = false),
        "4" -> InstructionStanza(0, Seq("end"), None, stack = false),
        "5" -> PageStanza("/page-2", Seq("6"), stack = false),
        "6" -> InstructionStanza(0, Seq("end"), None, stack = false),
        "7" -> PageStanza("/page-3", Seq("8"), stack = false),
        "8" -> InstructionStanza(0, Seq("end"), None, stack = false),
        "end" -> EndStanza
      )

      val process = Process(
        metaSection,
        flow,
        Vector[Phrase](
          Phrase(Vector("Some Text", "Welsh, Some Text")),
          Phrase(Vector("Exclusive sequence stanza", "Welsh, Exclusive sequence stanza")),
          Phrase(Vector("Some Text2", "Welsh, Some Text2")),
          Phrase(Vector("Some Text3 [exclusive]", "Welsh, Some Text3"))
        ),
        Vector[Link]()
      )

      pageBuilder.pagesWithValidation(process) match {
        case Left(List(IncompleteExclusiveSequencePage(Process.StartStanzaId))) => succeed
        case Left(err) => fail(s"Failed to detect missing TypeError callout. Instead failed with error $err")
        case _ => fail("Failed to detect missing TypeError callout")
      }
    }

  }

}