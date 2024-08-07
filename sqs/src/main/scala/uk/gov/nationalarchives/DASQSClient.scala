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
import software.amazon.awssdk.services.sqs.model.{
  DeleteMessageRequest,
  DeleteMessageResponse,
  ReceiveMessageRequest,
  SendMessageRequest,
  SendMessageResponse
}
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
class DASQSClient[F[_]: Async](sqsAsyncClient: SqsAsyncClient):

  /** Serialises the provided value to JSON and sends to the provided SQS queue.
    *
    * @param queueUrl
    *   The url for the SQS queue
    * @param message
    *   A case class which will be serialised to JSON and sent to the queue or a json string
    * @param enc
    *   A circe encoder which will encode the case class to JSON
    * @tparam T
    *   The case class with the message
    * @return
    *   The response from the send message call wrapped with F[_]
    */
  def sendMessage[T <: Product](queueUrl: String)(message: T)(using enc: Encoder[T]): F[SendMessageResponse] =
    val messageRequest = SendMessageRequest.builder
      .queueUrl(queueUrl)
      .messageBody(message.asJson.printWith(Printer.noSpaces))
      .build()
    Async[F].fromCompletableFuture(Async[F].pure(sqsAsyncClient.sendMessage(messageRequest)))

  /** Receives messages from the specified queue. The messages are deserialised using the implicit decoder
    * @param queueUrl
    *   The queue to receive messages from
    * @param maxNumberOfMessages
    *   The maximum number of messages to receive. Defaults to 10
    * @param dec
    *   The circe decoder used to decode the messages to type T
    * @tparam T
    *   The type of the class the messages will be decoded to
    * @return
    *   A list of MessageResponse case classes wrapped in the F effect
    */
  def receiveMessages[T](queueUrl: String, maxNumberOfMessages: Int = 10)(using
      dec: Decoder[T]
  ): F[List[MessageResponse[T]]] =
    val receiveRequest = ReceiveMessageRequest.builder
      .queueUrl(queueUrl)
      .maxNumberOfMessages(maxNumberOfMessages)
      .build

    Async[F]
      .fromCompletableFuture(Async[F].pure(sqsAsyncClient.receiveMessage(receiveRequest)))
      .flatMap { response =>
        response.messages.asScala.toList
          .map(message =>
            for messageAsT <- Async[F].fromEither(decode[T](message.body()))
            yield MessageResponse(message.receiptHandle, messageAsT)
          )
          .sequence
      }

  /** Deletes a message using the provided receipt handle
    * @param queueUrl
    *   The queue to delete the message from
    * @param receiptHandle
    *   The receipt handle from the message to delete
    * @return
    *   A DeleteMessageResponse class wrapped in the F effect.
    */
  def deleteMessage(queueUrl: String, receiptHandle: String): F[DeleteMessageResponse] =
    val deleteMessageRequest = DeleteMessageRequest.builder.queueUrl(queueUrl).receiptHandle(receiptHandle).build
    Async[F].fromCompletableFuture(Async[F].pure(sqsAsyncClient.deleteMessage(deleteMessageRequest)))

object DASQSClient:
  private lazy val httpClient: SdkAsyncHttpClient = NettyNioAsyncHttpClient.builder().build()
  case class MessageResponse[T](receiptHandle: String, message: T)
  private lazy val sqsClient: SqsAsyncClient = SqsAsyncClient.builder
    .region(Region.EU_WEST_2)
    .httpClient(httpClient)
    .build()
  def apply[F[_]: Async]() = new DASQSClient[F](sqsClient)

  def apply[F[_]: Async](httpsProxy: URI): DASQSClient[F] =
    val proxy = ProxyConfiguration
      .builder()
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
