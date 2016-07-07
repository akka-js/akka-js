package eu.unicredit

import scala.scalajs.js

import akka.actor._

import org.scalajs.dom.document.{getElementById => getElem}

import scalatags.JsDom._
import scalatags.JsDom.all._

import org.scalajs.dom.ext.Ajax

import java.util.UUID.randomUUID

object Page extends js.JSApp {
  def main() = {
    val system = ActorSystem("akkasite", AkkaConfig.config)

    val panels = List(
      {s: String => Props(PingPongPanel(s))},
      {s: String => Props(ToDoPanel(s))},
      {s: String => Props(StreamPanel(s))},
      {s: String => Props(ThisPagePanel(s))}
    )

    import system.dispatcher
    import scala.concurrent.duration._
    system.scheduler.scheduleOnce(0 millis){
      system.actorOf(Props(BodyActor(panels)), "body")
    }
  }
}

case class BodyActor(panels: List[(String) => Props]) extends DomActor {
  override val domElement = Some(getElem("body"))

  def template() =
    body(cls := "container", style := "overflow-x:hidden")(
      div(id := "overlay")
    )

  override def operative = {
    val evens = panels.zipWithIndex.filter{case (_, i) => i % 2 == 0}
    val odds = panels.zipWithIndex.filter{case (_, i) => i % 2 != 0}

    context.actorOf(Props(LogoActor()))

    context.actorOf(Props(Note()))

    evens.map{case (o, _) => Some(o)}.zipAll(
      odds.map{case (o, _) => Some(o)}, None, None
    ).foreach(x =>
      context.actorOf(Props(RowActor(x._1, x._2)))
    )

    context.actorOf(Props(Footer()))

    super.operative
  }
}

case class Note() extends DomActor {
  def template() =
    div(cls := "row")(
      div(cls := "col-md-12")(
        p(cls := "alert alert-success")(
          "The complete source code of this page is available ",
          a(href := "https://github.com/andreaTP/akka.js-site")(" here"),
          "."
        )
      )
    )
}

case class Footer() extends DomActor {
  def template() =
    footer(cls := "footer")(
      div(cls := "container")(
        p(cls := "text-muted")(
          "Inspiration for this page and much of the styles comes from ",
          a(href := "http://andreaferretti.github.io/paths-js-react-demo/")("Paths demo"),
          " many thanks to ",
          a(href := "https://github.com/andreaferretti")("@andreaferretti"),
          "."
        )
      )
    )
}

case class LogoActor() extends DomActor {
  def template() =
    div(cls := "row")(
      div(cls := "col-md-3")(),
      div(cls := "col-md-6")(
        a(href := "https://github.com/unicredit/akka.js")(
          img(cls := "img-responsive", src := "https://raw.githubusercontent.com/unicredit/akka.js/merge-js/logo/akkajs.png")
        ),
        h3(cls := "text-center")("Enjoy Akka in Scala.Js!")
      ),
      div(cls := "col-md-3")()
    )
}

case class RowActor(left: Option[(String) => Props], right: Option[(String) => Props]) extends DomActor {

  def template = div(cls := "row")()

  override def operative = {
    (left, right) match {
      case (Some(l), Some(r)) =>
        context.actorOf(l("col-md-6"))
        context.actorOf(r("col-md-6"))
      case (Some(l), _) =>
        context.actorOf(l("col-md-12"))
      case (_, Some(r)) =>
        context.actorOf(r("col-md-12"))
      case _ =>
    }

    super.operative
  }
}

abstract class Panel(title: String, source_url: String, col_style: String) extends DomActor {

  def content: TypedTag[_ <: org.scalajs.dom.raw.Element]

  val modalId = randomUUID.toString

  val prefix_url = "https://api.github.com/repos/andreaTP/akka.js-site/contents/src/main/scala/eu/unicredit/"

  val modal =
    div(id := modalId, cls := "modal fade large", "role".attr := "dialog")(
      div(cls := "modal-dialog")(
        div(cls := "modal-content")(
          div(cls := "modal-header")(
            h4(cls := "modal-title")(s"$title source code:")
          ),
          div(cls := "modal-body")(
            pre(style := "background-color: black")(
              code(id := s"code$modalId", cls := "scala hljs")()
            )
          ),
          div(cls := "modal-footer")(
            button(`type` := "button", cls := "close", "aria-hidden".attr :="true", "data-dismiss".attr := "modal")("Close")
          )
        )
      )
    )

  override def template =
    div(cls := col_style)(
      div(cls := "panel panel-default")(
        div(cls := "panel-heading")(
          h2(cls := "panel-title")(title),
          span(cls := "links")(
            a("data-toggle".attr :="modal", "data-target".attr :=s"#$modalId")("Source")
          )
        ),
        modal,
        div(cls := "panel-body")(content)
      )
    )

  def loadSource() = {
    import context.dispatcher
    Ajax.get(s"$prefix_url$source_url",
      timeout = 3000
    ).map(req => {
      val json = js.JSON.parse(req.responseText)

      val source = js.Dynamic.global.atob(json.content)

      getElem(s"code$modalId").innerHTML =
        js.Dynamic.global.hljs.highlight("scala", source).value.toString
    }).recover{
      case _ => getElem(s"code$modalId").innerHTML = "source not found"
    }
  }

}

case class PingPongPanel(col_style: String) extends
    Panel(
      "Ping Pong",
      "PingPong.scala",
      col_style
    ) {

  val loggerId = randomUUID.toString

  def content =
    div(
      p(cls := "alert alert-info")(
        "Here is an Akka.js kind of hello world, press run to create the environment and let two actors send ping and pong each other."
      ),
      button(`type` :="button", cls :="btn btn-success alert col-md-2", onclick := {() => self ! Start})("Start"),
      div(cls :="col-md-1"),
      button(`type` :="button", cls :="btn btn-danger alert col-md-2", onclick := {() => self ! Stop})("Stop"),
      div(id := loggerId)
    )

  case object Start
  case object Stop

  override def operative = {
    loadSource()
    running(new PingPong, context.actorOf(Props(LogActor(loggerId, 10, 100, (for (_ <- 0 until 100) yield "").toList))))
  }

  def running(pp: PingPong, logger: ActorRef): Receive = domManagement orElse {
    case Start =>
      pp.stop()
      pp.start(logger)
      logger ! ResetLog
    case Stop =>
      pp.stop()
  }
}

case class LogMsg(txt: String)
case object ResetLog

case class LogActor(hook: String, showLines: Int, maxLines: Int, init: List[String]) extends DomActorWithParams[List[String]] {
  override val domElement = Some(getElem(hook))

  val initValue: List[String] = init

  def template(txt: List[String]) =
    textarea(cls := "form-control", "rows".attr := s"$showLines", "readonly".attr := "readonly", style := "style=font-family: monospace;font-size: 70%")(
      txt.mkString("\n")
    )

  override def operative = withText(initValue)

  def withText(last: List[String]): Receive = domManagement orElse {
    case ResetLog =>
      self ! UpdateValue(initValue)
      context.become(withText(initValue))
    case LogMsg(txt) =>
      val str = new String(txt.toCharArray.filterNot(_.toInt == 0))
      val newTxt = (last :+ str).takeRight(maxLines)
      self ! UpdateValue(newTxt)
      context.become(withText(newTxt))
  }

}

case class ToDoPanel(col_style: String) extends
    Panel(
      "To Do",
      "ToDo.scala",
      col_style
    ) {

  val todoId = randomUUID.toString

  def content =
    div(
      p(cls := "alert alert-info")(
        "Here we see a possible integration of the Actor model with the Dom. Actors life cycle is mapped on the rendering of a related template."
      ),
      div(id := todoId, cls := "container")
    )

  override def operative = {
    loadSource()

    context.actorOf(Props(ToDo(todoId)))

    super.operative
  }
}

case class StreamPanel(col_style: String) extends
    Panel(
      "Stream",
      "Stream.scala",
      col_style
    ) {

  val loggerId = randomUUID.toString

  def content =
    div(
      p(cls := "alert alert-info")(
        "Here is an Akka-Stream.js basic example, where a factorial flow will run."
      ),
      button(`type` :="button", cls :="btn btn-success alert col-md-2", onclick := {() => self ! Start})("Start"),
      div(cls :="col-md-1"),
      button(`type` :="button", cls :="btn btn-danger alert col-md-2", onclick := {() => self ! Stop})("Stop"),
      div(id := loggerId)
    )

  case object Start
  case object Stop

  override def operative = {
    loadSource()
    running(new Stream, context.actorOf(Props(LogActor(loggerId, 20, 20, List()))))
  }

  def running(stream: Stream, logger: ActorRef): Receive = domManagement orElse {
    case Start =>
      stream.stop()
      stream.start(logger)
      logger ! ResetLog
    case Stop =>
      stream.stop()
  }
}

case class ThisPagePanel(col_style: String) extends
    Panel(
      "This Page",
      "Page.scala",
      col_style
    ) {

  def content =
    div(
      p(cls := "alert alert-info")(
        "This page itself is an example of how Akka can help you developing truly reactive frontends!"
      )
    )

  override def operative = {
    loadSource()
    super.operative
  }
}
