package uk.gov.nationalarchives

import cats.effect.Async
import io.circe.syntax._
import io.circe.{Encoder, Printer}
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.{PublishRequest, PublishResponse}

/** An SNS client. It is written generically so can be used for any effect which has an Async instance. Requires an
 * implicit instance of cats Async which is used to convert CompletableFuture to F
 *
 * @param snsAsyncClient
 * An AWS SNS Async client
 * @tparam F
 * Type of the effect
 */
class DASNSClient[F[_] : Async](snsAsyncClient: SnsAsyncClient) {

  /** Deserialises the provided value to JSON and sends to the provided SNS topic.
   *
   * @param queueUrl
   * The url for the SNS queue
   * @param message
   * A case class which will be deserialised to JSON and sent to the topic or a json string
   * @param enc
   * A circe encoder which will encode the case class to JSON
   * @tparam T
   * The case class with the message
   * @return
   * The response from the send message call wrapped with F[_]
   */
  def publish[T <: Product](topicArn: String)(message: T)(implicit enc: Encoder[T]): F[PublishResponse] = {
    val messageRequest = PublishRequest.builder
      .topicArn(topicArn)
      .message(message.asJson.printWith(Printer.noSpaces))
      .build()
    Async[F].fromCompletableFuture(Async[F].pure(snsAsyncClient.publish(messageRequest)))
  }
}

object DASNSClient {
  private val httpClient: SdkAsyncHttpClient = NettyNioAsyncHttpClient.builder().build()

  private val snsClient: SnsAsyncClient = SnsAsyncClient.builder
    .region(Region.EU_WEST_2)
    .httpClient(httpClient)
    .build()

  def apply[F[_] : Async]() = new DASNSClient[F](snsClient)
}
