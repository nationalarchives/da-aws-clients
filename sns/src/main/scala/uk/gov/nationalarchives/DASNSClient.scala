package uk.gov.nationalarchives

import cats.effect.Async
import cats.implicits._
import io.circe.syntax._
import io.circe.{Encoder, Printer}
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.{PublishBatchRequest, PublishBatchRequestEntry, PublishBatchResponse}

import java.util
import java.util.UUID
import scala.jdk.CollectionConverters.SeqHasAsJava

/** An SNS client. It is written generically so can be used for any effect which has an Async instance. Requires an
  * implicit instance of cats Async which is used to convert CompletableFuture to F
  * @tparam F
  *   Type of the effect
  */
trait DASNSClient[F[_]: Async]:

  /** Deserialises the provided value to JSON and sends to the provided SNS topic.
    *
    * @param topicArn
    *   The arn for the SNS topic
    * @param messages
    *   A List of case classes which will be deserialised to JSON and sent to the topic or a json string
    * @param enc
    *   A circe encoder which will encode the case class to JSON
    * @tparam T
    *   The case class with the message
    * @return
    *   The response from the send message call wrapped with F[_]
    */

  def publish[T <: Product](
      topicArn: String
  )(messages: List[T])(using enc: Encoder[T]): F[List[PublishBatchResponse]]

object DASNSClient:
  lazy val snsClient: SnsAsyncClient = SnsAsyncClient.builder
    .region(Region.EU_WEST_2)
    .httpClient(NettyNioAsyncHttpClient.builder().build())
    .build()

  def apply[F[_]: Async](snsAsyncClient: SnsAsyncClient = snsClient): DASNSClient[F] = new DASNSClient[F]() {
    override def publish[T <: Product](topicArn: String)(messages: List[T])(using
        enc: Encoder[T]
    ): F[List[PublishBatchResponse]] =
      val batchesOfTenMessages: List[List[T]] = messages.grouped(10).toList

      batchesOfTenMessages.map { batchOfTenMessages =>
        val batchOfTenJsonMessages: util.List[PublishBatchRequestEntry] = batchOfTenMessages.map { message =>
          val messageAsJson: String = message.asJson.printWith(Printer.noSpaces)
          PublishBatchRequestEntry
            .builder()
            .id(UUID.randomUUID().toString)
            .message(messageAsJson)
            .build()
        }.asJava

        val batchMessageRequest: PublishBatchRequest =
          PublishBatchRequest.builder
            .topicArn(topicArn)
            .publishBatchRequestEntries(batchOfTenJsonMessages)
            .build()
        Async[F].fromCompletableFuture(Async[F].pure(snsAsyncClient.publishBatch(batchMessageRequest)))
      }.sequence
  }
