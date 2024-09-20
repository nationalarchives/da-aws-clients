package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import com.amazon.sqs.javamessaging.message.{SQSMessage, SQSTextMessage}
import org.scalatest.flatspec.AnyFlatSpec
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{ChangeMessageVisibilityBatchRequest, ChangeMessageVisibilityBatchResponse, GetQueueUrlRequest, GetQueueUrlResponse, Message, MessageSystemAttributeName, ReceiveMessageRequest, ReceiveMessageResponse}

import scala.jdk.CollectionConverters.*
import io.circe.generic.auto.*


class DASQSStreamClientTest extends AnyFlatSpec:
  case class TestMessage(id: String)
  val client = new SqsClient() {
    override def receiveMessage(receiveMessageRequest: ReceiveMessageRequest): ReceiveMessageResponse =
      val sqsMessage = Message.builder
        .body("""{"id": "asdad"}""")
        .attributes(Map(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT -> "1").asJava)
        .build
      ReceiveMessageResponse.builder.messages(List(sqsMessage, sqsMessage).asJava).build

    override def changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest: ChangeMessageVisibilityBatchRequest): ChangeMessageVisibilityBatchResponse =
      ChangeMessageVisibilityBatchResponse.builder.build

    override def getQueueUrl(getQueueUrlRequest: GetQueueUrlRequest): GetQueueUrlResponse = GetQueueUrlResponse.builder.queueUrl("http://test").build

    override def serviceName(): String = "Sqs"

    override def close(): Unit = ()
  }

  def getQueueItems[T](queue: Queue[IO, T], elems: List[T] = Nil): IO[List[T]] =
    queue.tryTake.flatMap {
      case Some(value) =>
        getQueueItems(queue, value :: elems)
      case None =>
        IO.pure(elems)
    }

  "it" should "something" in {
    val d = DASQSStreamClient[IO](client).receiveMessageStream[TestMessage]("test").use(queue => {
      val z = queue.tryTakeN(None).unsafeRunSync()
      val y = queue.take.unsafeRunSync()
      val x = queue.tryTakeN(None).unsafeRunSync()
      getQueueItems(queue)
    }).unsafeRunSync()
    println(d)
  }
