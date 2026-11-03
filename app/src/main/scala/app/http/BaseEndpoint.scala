package app.http

import io.circe.{Decoder, Encoder}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*

final case class ErrorResponse(message: String) derives Encoder.AsObject, Decoder, Schema

object BaseEndpoints:
  val api: Endpoint[Unit, Unit, (StatusCode, ErrorResponse), Unit, Any] =
    endpoint
      .in("api" / "v1")
      .errorOut(statusCode.and(jsonBody[ErrorResponse]))

  def notFound(msg: String): (StatusCode, ErrorResponse) =
    (StatusCode.NotFound, ErrorResponse(msg))
