package app.http

import sttp.tapir.server.ServerEndpoint
import scala.concurrent.Future

trait Controller:
  def endpoints: List[ServerEndpoint[Any, Future]]
