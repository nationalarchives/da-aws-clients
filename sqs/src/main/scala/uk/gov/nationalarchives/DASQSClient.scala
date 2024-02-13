package uk.gov.nationalarchives

import cats.effect.Async
import cats.implicits._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Printer}
import io.circe.parser.decode
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.{NettyNioAsyncHttpClient, ProxyConfiguration}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{DeleteMessageRequest, ReceiveMessageRequest, SendMessageRequest, SendMessageResponse}
import uk.gov.nationalarchives.DASQSClient.MessageResponse

import java.net.URI
import scala.jdk.CollectionConverters._

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

  def receiveMessages[T](queueUrl: String, maxNumberOfMessages: Int = 10)(implicit dec: Decoder[T]): F[List[MessageResponse[T]]] = {
    val receiveRequest = ReceiveMessageRequest.builder
      .queueUrl(queueUrl)
      .maxNumberOfMessages(maxNumberOfMessages)
      .build

    Async[F].fromCompletableFuture(Async[F].pure(sqsAsyncClient.receiveMessage(receiveRequest)))
      .map(response => {
        response.messages.asScala.toList.map(message => for {
          messageAsT <- Async[F].fromEither(decode[T](message.body()))
        } yield MessageResponse(message.receiptHandle, messageAsT)).sequence

      }).flatten
  }

  def deleteMessage(queueUrl: String, receiptHandle: String) = {
    val deleteMessageRequest = DeleteMessageRequest.builder.queueUrl(queueUrl).receiptHandle(receiptHandle).build
    Async[F].fromCompletableFuture(Async[F].pure(sqsAsyncClient.deleteMessage(deleteMessageRequest)))
  }
}

object DASQSClient {
  private val httpClient: SdkAsyncHttpClient = NettyNioAsyncHttpClient.builder().build()
  case class MessageResponse[T](receiptHandle: String, message: T)
  private val sqsClient: SqsAsyncClient = SqsAsyncClient.builder
    .region(Region.EU_WEST_2)
    .httpClient(httpClient)
    .build()
  def apply[F[_]: Async]() = new DASQSClient[F](sqsClient)

  def apply[F[_]: Async](httpsProxy: URI): DASQSClient[F] = {
    val proxy = ProxyConfiguration.builder()
      .scheme(httpsProxy.getScheme)
      .host(httpsProxy.getHost)
      .port(httpsProxy.getPort)
      .build
    val httpClient = NettyNioAsyncHttpClient.builder().proxyConfiguration(proxy).build
    val sqsClient: SqsAsyncClient = SqsAsyncClient.builder
      .region(Region.EU_WEST_2)
      .httpClient(httpClient)
      .build()
    new DASQSClient[F](sqsClient)
  }


}
