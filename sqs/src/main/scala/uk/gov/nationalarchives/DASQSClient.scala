package uk.gov.nationalarchives

import cats.effect.Async
import io.circe.syntax._
import io.circe.{Encoder, Printer}
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{SendMessageRequest, SendMessageResponse}

/** An SQS client. It is written generically so can be used for any effect which has an Async instance. Requires an
  * implicit instance of cats Async which is used to convert CompletableFuture to F
  *
  * @param sqsAsyncClient
  *   An AWS SQS Async client
  * @tparam F
  *   Type of the effect
  */
class DASQSClient[F[_]: Async](sqsAsyncClient: SqsAsyncClient) {

  /** Deserialises the provided value to JSON and sends to the provided SQS queue.
    *
    * @param queueUrl
    *   The url for the SQS queue
    * @param message
    *   A case class which will be deserialised to JSON and sent to the queue or a json string
    * @param enc
    *   A circe encoder which will encode the case class to JSON
    * @tparam T
    *   The case class with the message
    * @return
    *   The response from the send message call wrapped with F[_]
    */
  def sendMessage[T <: Product](queueUrl: String)(message: T)(implicit enc: Encoder[T]): F[SendMessageResponse] = {
    val messageRequest = SendMessageRequest.builder
      .queueUrl(queueUrl)
      .messageBody(message.asJson.printWith(Printer.noSpaces))
      .build()
    Async[F].fromCompletableFuture(Async[F].pure(sqsAsyncClient.sendMessage(messageRequest)))
  }
}

object DASQSClient {
  private val httpClient: SdkAsyncHttpClient = NettyNioAsyncHttpClient.builder().build()

  private val sqsClient: SqsAsyncClient = SqsAsyncClient.builder
    .region(Region.EU_WEST_2)
    .httpClient(httpClient)
    .build()
  def apply[F[_]: Async]() = new DASQSClient[F](sqsClient)
}
