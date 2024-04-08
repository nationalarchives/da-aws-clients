package uk.gov.nationalarchives

import cats.effect.Async
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Printer}
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
class DAEventBridgeClient[F[_]: Async](asyncClient: EventBridgeAsyncClient):

  /** Sends an event to EventBridge. detail will be serialised to a json string.
    * @param sourceId
    *   The source ID to send to EventBridge
    * @param detailType
    *   The detail type to send to EventBridge
    * @param detail
    *   A Serializable class of type T. This will be serialised to a json string
    * @param enc
    *   The circe encoder to do the serialisation
    * @tparam T
    *   The type of the detail class
    * @return
    */
  def publishEventToEventBridge[T](sourceId: String, detailType: String, detail: T)(using
      enc: Encoder[T]
  ): F[PutEventsResponse] =
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

object DAEventBridgeClient:

  private lazy val httpClient: SdkAsyncHttpClient = NettyNioAsyncHttpClient.builder.build
  private lazy val asyncClient: EventBridgeAsyncClient = EventBridgeAsyncClient.builder
    .httpClient(httpClient)
    .region(Region.EU_WEST_2)
    .credentialsProvider(DefaultCredentialsProvider.create())
    .build()

  def apply[F[_]: Async](eventBridgeAsyncClient: EventBridgeAsyncClient) =
    new DAEventBridgeClient[F](eventBridgeAsyncClient)

  def apply[F[_]: Async]() = new DAEventBridgeClient[F](asyncClient)
