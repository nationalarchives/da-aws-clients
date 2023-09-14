package uk.gov.nationalarchives

import cats.effect.Async
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Json, Printer}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient
import software.amazon.awssdk.services.eventbridge.model.{PutEventsRequest, PutEventsRequestEntry, PutEventsResponse}

/** An EventBridgeAsyncClient client. It is written generically so can be used for any effect which has an Async
  * instance. Requires an implicit instance of cats Async which is used to convert CompletableFuture to F
  *
  * @param asyncClient
  *   An AWS EventBridge Async client
  * @tparam F
  *   Type of the effect
  */
class DAEventBridgeClient[F[_]: Async](asyncClient: EventBridgeAsyncClient) {

  /** Sends an event to EventBridge. detail will be deserialised to a json string.
    * @param sourceId
    *   The source ID to send to EventBridge
    * @param detailType
    *   The detail type to send to EventBridge
    * @param detail
    *   A case class of type T. This will be deserialised to a json string
    * @param enc
    *   The circe encoder to do the deserialisation
    * @tparam T
    *   The type of the detail class
    * @return
    */
  def publishEventToEventBridge[T](sourceId: String, detailType: String, detail: T)(implicit
      enc: Encoder[T]
  ): F[PutEventsResponse] = {
    val detailAsString = detail.asJson.printWith(Printer.noSpaces)
    val requestEntry: PutEventsRequestEntry = PutEventsRequestEntry.builder
      .detail(detailAsString)
      .source(sourceId)
      .detailType(detailType)
      .build()
    val putEventsRequest = PutEventsRequest.builder
      .entries(requestEntry)
      .build()

    Async[F].fromCompletableFuture(Async[F].pure(asyncClient.putEvents(putEventsRequest)))
  }
}

object DAEventBridgeClient {

  private val httpClient: SdkAsyncHttpClient = NettyNioAsyncHttpClient.builder.build
  private val asyncClient: EventBridgeAsyncClient = EventBridgeAsyncClient.builder
    .httpClient(httpClient)
    .region(Region.EU_WEST_2)
    .credentialsProvider(DefaultCredentialsProvider.create())
    .build()

  def apply[F[_]: Async](eventBridgeAsyncClient: EventBridgeAsyncClient) =
    new DAEventBridgeClient[F](eventBridgeAsyncClient)

  def apply[F[_]: Async]() = new DAEventBridgeClient[F](asyncClient)

  /** Converts a string into a DetailType.
    * @param detailType
    *   The string value which will be passed to EventBridge
    */
  implicit class DetailTypeStringExtension(detailType: String) {
    def toDetailType: DetailType = DetailType(detailType)
  }

  case class DetailType(detailTypeValue: String)
  case class Detail(slackMessage: String)

  /** An implicit class which adds a sendToSlack extension message to the log4cats logger
    * @param logger
    *   The log4cats logger the extension method is added to
    * @param detailType
    *   The detail type. This determines which rule picks up the event.
    * @param client
    *   The DAEventBridgeClient used to send the event
    * @tparam F
    *   The effect to wrap the responses
    */
  implicit class EventBridgeSlf4jLoggerExtensions[F[_]: Async](logger: SelfAwareStructuredLogger[F])(implicit
      val detailType: DetailType,
      client: DAEventBridgeClient[F]
  ) {
    import cats.implicits._

    implicit val encoder: Encoder[Detail] = (detail: Detail) =>
      Json.obj(("message", Json.fromString(detail.slackMessage)))

    /** This method publishes an event with the detailType set to the implicit parameter on the class. You will need to
      * set up an eventbridge rule to send these events to Slack. There is an example at
      * https://github.com/nationalarchives/dr2-terraform-environments/blob/main/common.tf#L214
      *
      * @param messageString
      *   The message string to send to event bus.
      * @return
      *   F[Unit]
      */
    def sendToSlack(messageString: String): F[Unit] = {
      client
        .publishEventToEventBridge(
          "uk.gov.nationalarchives.da-eventbridge-client",
          detailType.detailTypeValue,
          Detail(messageString)
        )
        .map(_ => Async[F].unit)
    }
  }
}
