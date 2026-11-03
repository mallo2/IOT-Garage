package app.http.controllers

import app.http.Controller
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future

class HealthController extends Controller:
  private val health =
    endpoint
      .get
      .in("health")
      .out(stringBody)
      .serverLogicSuccess[Future](_ => Future.successful("OK"))

  val endpoints: List[ServerEndpoint[Any, Future]] = List(health)
