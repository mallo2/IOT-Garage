package app.http

import app.http.controllers.HealthController
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

object HttpServer:
  def start(controllers: List[Controller], host: String, port: Int)(using system: ActorSystem[Nothing], ec: ExecutionContext): Future[Http.ServerBinding] =
    val api  = controllers.flatMap(_.endpoints)
    val docs = SwaggerInterpreter().fromServerEndpoints[Future](api, "IoT API", "1.0")
    val route = PekkoHttpServerInterpreter().toRoute(api ++ docs)

    Http().newServerAt(host, port).bind(route)