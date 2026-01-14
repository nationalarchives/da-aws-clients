package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.{Decoder, Encoder}
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.when
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.*
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}
import org.scalatestplus.mockito.MockitoSugar
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{
  ChangeMessageVisibilityRequest,
  ChangeMessageVisibilityResponse,
  DeleteMessageRequest,
  DeleteMessageResponse,
  GetQueueAttributesRequest,
  GetQueueAttributesResponse,
  Message,
  MessageSystemAttributeName,
  QueueAttributeName,
  ReceiveMessageRequest,
  ReceiveMessageResponse,
  SendMessageRequest,
  SendMessageResponse
}
import uk.gov.nationalarchives.DASQSClient.FifoQueueConfiguration

import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters.*
import scala.concurrent.duration.*

class DASQSClientTest extends AnyFlatSpec with MockitoSugar with TableDrivenPropertyChecks {

  case class Test(message: String, value: String)

  given Encoder[Test] = Encoder.forProduct2("message", "value")(t => (t.message, t.value))
  given Decoder[Test] =
    Decoder.forProduct2[Test, String, String]("message", "value")((message, value) => Test(message, value))

  "sendMessage" should "send the correct message to the queue" in {
    val sqsAsyncClient = mock[SqsAsyncClient]
    val sendMessageCaptor: ArgumentCaptor[SendMessageRequest] = ArgumentCaptor.forClass(classOf[SendMessageRequest])
    val mockResponse = CompletableFuture.completedFuture(SendMessageResponse.builder().build())
    when(sqsAsyncClient.sendMessage(sendMessageCaptor.capture())).thenReturn(mockResponse)

    val fifoConfig = FifoQueueConfiguration("MessageGroupId", "MessageDeduplicationId")
    val client = DASQSClient[IO](sqsAsyncClient)
    client.sendMessage("https://test")(Test("testMessage", "testValue"), Option(fifoConfig), 10).unsafeRunSync()

    val sendMessageValue = sendMessageCaptor.getValue

    sendMessageValue.messageBody() should equal("""{"message":"testMessage","value":"testValue"}""")
    sendMessageValue.messageGroupId() should equal("MessageGroupId")
    sendMessageValue.messageDeduplicationId() should equal("MessageDeduplicationId")
    sendMessageValue.queueUrl() should equal("https://test")
    sendMessageValue.delaySeconds() should equal(10)
  }

  "sendMessage" should "not send message group id and deduplication id if the fifo queue configuration is not provided" in {
    val sqsAsyncClient = mock[SqsAsyncClient]
    val sendMessageCaptor: ArgumentCaptor[SendMessageRequest] = ArgumentCaptor.forClass(classOf[SendMessageRequest])
    val mockResponse = CompletableFuture.completedFuture(SendMessageResponse.builder().build())
    when(sqsAsyncClient.sendMessage(sendMessageCaptor.capture())).thenReturn(mockResponse)

    val client = DASQSClient[IO](sqsAsyncClient)
    client.sendMessage("https://test")(Test("testMessage", "testValue")).unsafeRunSync()

    val sendMessageValue = sendMessageCaptor.getValue

    sendMessageValue.messageBody() should equal("""{"message":"testMessage","value":"testValue"}""")
    Option(sendMessageValue.messageGroupId()) should equal(None)
    Option(sendMessageValue.messageDeduplicationId()) should equal(None)
    sendMessageValue.queueUrl() should equal("https://test")
  }

  "sendMessage" should "return an error if there is an error sending to the queue" in {
    val sqsAsyncClient = mock[SqsAsyncClient]
    when(sqsAsyncClient.sendMessage(any[SendMessageRequest])).thenThrow(new RuntimeException("Error sending message"))

    val client = DASQSClient[IO](sqsAsyncClient)

    val ex = intercept[Exception] {
      client.sendMessage("https://test")(Test("testMessage", "testValue")).unsafeRunSync()
    }
    ex.getMessage should equal("Error sending message")
  }

  "sendMessage" should "set the delay seconds value to zero if no value is provided" in {
    val sqsAsyncClient = mock[SqsAsyncClient]
    val sendMessageCaptor: ArgumentCaptor[SendMessageRequest] = ArgumentCaptor.forClass(classOf[SendMessageRequest])
    val mockResponse = CompletableFuture.completedFuture(SendMessageResponse.builder().build())
    when(sqsAsyncClient.sendMessage(sendMessageCaptor.capture())).thenReturn(mockResponse)

    val client = DASQSClient[IO](sqsAsyncClient)
    client.sendMessage("https://test")(Test("testMessage", "testValue")).unsafeRunSync()

    val sendMessageValue = sendMessageCaptor.getValue

    sendMessageValue.delaySeconds() should equal(0)
  }

  "receiveMessage" should "request messages with the correct parameters" in {
    val sqsAsyncClient = mock[SqsAsyncClient]
    val receiveMessageCaptor: ArgumentCaptor[ReceiveMessageRequest] =
      ArgumentCaptor.forClass(classOf[ReceiveMessageRequest])
    val response = CompletableFuture.completedFuture(ReceiveMessageResponse.builder().build())
    when(sqsAsyncClient.receiveMessage(receiveMessageCaptor.capture())).thenReturn(response)

    val client: DASQSClient[IO] = DASQSClient[IO](sqsAsyncClient)

    client.receiveMessages[Test]("https://test", maxNumberOfMessages = 20).unsafeRunSync()

    val receiveRequest = receiveMessageCaptor.getValue
    receiveRequest.maxNumberOfMessages() should equal(20)
    receiveRequest.queueUrl() should equal("https://test")
    receiveRequest.messageSystemAttributeNames().asScala.toList should equal(
      List(MessageSystemAttributeName.MESSAGE_GROUP_ID)
    )
  }

  "receiveMessage" should "pass a default of 10 to max number of messages if no argument is provided" in {
    val sqsAsyncClient = mock[SqsAsyncClient]
    val receiveMessageCaptor: ArgumentCaptor[ReceiveMessageRequest] =
      ArgumentCaptor.forClass(classOf[ReceiveMessageRequest])
    val response = CompletableFuture.completedFuture(ReceiveMessageResponse.builder().build())
    when(sqsAsyncClient.receiveMessage(receiveMessageCaptor.capture())).thenReturn(response)

    val client: DASQSClient[IO] = DASQSClient[IO](sqsAsyncClient)

    client.receiveMessages[Test]("https://test").unsafeRunSync()

    val receiveRequest = receiveMessageCaptor.getValue
    receiveRequest.maxNumberOfMessages() should equal(10)
  }

  "receiveMessage" should "return the decoded message with the receipt handle" in {
    case class Custom(name: String, isCustom: Boolean)
    val sqsAsyncClient = mock[SqsAsyncClient]
    val receiveMessageCaptor: ArgumentCaptor[ReceiveMessageRequest] =
      ArgumentCaptor.forClass(classOf[ReceiveMessageRequest])
    val receiveResponse = ReceiveMessageResponse
      .builder()
      .messages(
        Message
          .builder()
          .body("""{"name": "custom", "isCustom": true}""")
          .receiptHandle("receiptHandle")
          .attributes(Map(MessageSystemAttributeName.MESSAGE_GROUP_ID -> "groupId").asJava)
          .build()
      )
      .build()

    given Decoder[Custom] =
      Decoder.forProduct2[Custom, String, Boolean]("name", "isCustom")((name, isCustom) => Custom(name, isCustom))
    val response = CompletableFuture.completedFuture(receiveResponse)
    when(sqsAsyncClient.receiveMessage(receiveMessageCaptor.capture())).thenReturn(response)

    val client: DASQSClient[IO] = DASQSClient[IO](sqsAsyncClient)

    val messagesResponse = client.receiveMessages[Custom]("https://test", maxNumberOfMessages = 20).unsafeRunSync()

    messagesResponse.size should equal(1)
    val messageResponse = messagesResponse.head
    messageResponse.receiptHandle should equal("receiptHandle")
    messageResponse.messageGroupId.isDefined should equal(true)
    messageResponse.messageGroupId.get should equal("groupId")
    messageResponse.message.name should equal("custom")
    messageResponse.message.isCustom should equal(true)
  }

  "receiveMessage" should "return an error if there is an error receiving messages" in {
    val sqsAsyncClient = mock[SqsAsyncClient]
    when(sqsAsyncClient.receiveMessage(any[ReceiveMessageRequest]))
      .thenThrow(new RuntimeException("Error receiving messages"))

    val client = DASQSClient[IO](sqsAsyncClient)

    val ex = intercept[Exception] {
      client.receiveMessages("https://test").unsafeRunSync()
    }
    ex.getMessage should equal("Error receiving messages")
  }

  "deleteMessage" should "delete the correct message from the queue" in {
    val sqsAsyncClient = mock[SqsAsyncClient]
    val deleteMessageCaptor: ArgumentCaptor[DeleteMessageRequest] =
      ArgumentCaptor.forClass(classOf[DeleteMessageRequest])
    val mockResponse = CompletableFuture.completedFuture(DeleteMessageResponse.builder().build())
    when(sqsAsyncClient.deleteMessage(deleteMessageCaptor.capture())).thenReturn(mockResponse)

    val client = DASQSClient[IO](sqsAsyncClient)
    client.deleteMessage("https://test", "receiptHandle").unsafeRunSync()

    val deleteMessageValue = deleteMessageCaptor.getValue

    deleteMessageValue.queueUrl() should equal("https://test")
    deleteMessageValue.receiptHandle() should equal("receiptHandle")
  }

  "deleteMessage" should "return an error if there is an error sending to the queue" in {
    val sqsAsyncClient = mock[SqsAsyncClient]
    when(sqsAsyncClient.deleteMessage(any[DeleteMessageRequest]))
      .thenThrow(new RuntimeException("Error deleting message"))

    val client = DASQSClient[IO](sqsAsyncClient)

    val ex = intercept[Exception] {
      client.deleteMessage("https://test", "receiptHandle").unsafeRunSync()
    }
    ex.getMessage should equal("Error deleting message")
  }

  "getQueueAttributes" should "request for 'ALL' attributes of the queue when no specific attribute is requested" in {
    val sqsAsyncClient = mock[SqsAsyncClient]
    val getQueueAttributesCaptor: ArgumentCaptor[GetQueueAttributesRequest] =
      ArgumentCaptor.forClass(classOf[GetQueueAttributesRequest])
    val mockResponse = CompletableFuture.completedFuture(GetQueueAttributesResponse.builder().build())
    when(sqsAsyncClient.getQueueAttributes(getQueueAttributesCaptor.capture())).thenReturn(mockResponse)

    val client = DASQSClient[IO](sqsAsyncClient)
    client.getQueueAttributes("https://test").unsafeRunSync()

    val getQueueAttributesValue = getQueueAttributesCaptor.getValue

    getQueueAttributesValue.queueUrl() should equal("https://test")
    getQueueAttributesValue.attributeNames().size() should equal(1)
    getQueueAttributesValue.attributeNames().asScala.toList.head should equal(QueueAttributeName.ALL)
  }

  "getQueueAttributes" should "return an error if there is an error getting the queue attributes" in {
    val sqsAsyncClient = mock[SqsAsyncClient]
    when(sqsAsyncClient.getQueueAttributes(any[GetQueueAttributesRequest]))
      .thenThrow(new RuntimeException("Error getting queue attributes"))

    val client = DASQSClient[IO](sqsAsyncClient)

    val ex = intercept[Exception] {
      client.getQueueAttributes("https://test").unsafeRunSync()
    }
    ex.getMessage should equal("Error getting queue attributes")
  }

  "getQueueAttributes" should "request specific attributes of the queue" in {
    val sqsAsyncClient = mock[SqsAsyncClient]
    val getQueueAttributesCaptor: ArgumentCaptor[GetQueueAttributesRequest] =
      ArgumentCaptor.forClass(classOf[GetQueueAttributesRequest])
    val mockResponse = CompletableFuture.completedFuture(GetQueueAttributesResponse.builder().build())
    when(sqsAsyncClient.getQueueAttributes(getQueueAttributesCaptor.capture())).thenReturn(mockResponse)

    val client = DASQSClient[IO](sqsAsyncClient)
    client
      .getQueueAttributes(
        "https://test",
        List(
          QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
          QueueAttributeName.MESSAGE_RETENTION_PERIOD
        )
      )
      .unsafeRunSync()

    val getQueueAttributesValue = getQueueAttributesCaptor.getValue

    getQueueAttributesValue.queueUrl() should equal("https://test")
    getQueueAttributesValue.attributeNames().size() should equal(2)
    getQueueAttributesValue.attributeNames().asScala.toList should contain(
      QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES
    )
    getQueueAttributesValue.attributeNames().asScala.toList should contain(
      QueueAttributeName.MESSAGE_RETENTION_PERIOD
    )
  }

  val times: TableFor2[FiniteDuration, Int] = Table(
    ("duration", "timeoutSeconds"),
    (1.second, 1),
    (1.hour, 3600),
    (30.minutes, 1800)
  )

  forAll(times) { (duration, timeoutSeconds) =>
    "changeVisibilityTimeout" should s"change the visibility timeout of the requested message to $duration" in {
      val sqsAsyncClient = mock[SqsAsyncClient]
      val changeVisibilityCaptor: ArgumentCaptor[ChangeMessageVisibilityRequest] =
        ArgumentCaptor.forClass(classOf[ChangeMessageVisibilityRequest])
      when(sqsAsyncClient.changeMessageVisibility(changeVisibilityCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(ChangeMessageVisibilityResponse.builder.build))

      val client = DASQSClient[IO](sqsAsyncClient)
      client.changeVisibilityTimeout("https://test")("receiptHandle", duration).unsafeRunSync()

      val request = changeVisibilityCaptor.getValue

      request.queueUrl() should equal("https://test")
      request.receiptHandle() should equal("receiptHandle")
      request.visibilityTimeout() should equal(timeoutSeconds)
    }
  }

  "changeVisibilityTimeout" should "return an error if there is an error setting the timeout" in {
    val sqsAsyncClient = mock[SqsAsyncClient]
    when(sqsAsyncClient.changeMessageVisibility(any[ChangeMessageVisibilityRequest]))
      .thenThrow(new RuntimeException("Error setting visibility timeout"))

    val client = DASQSClient[IO](sqsAsyncClient)

    val ex = intercept[Exception] {
      client.changeVisibilityTimeout("https://test")("receiptHandle", 1.seconds).unsafeRunSync()
    }
    ex.getMessage should equal("Error setting visibility timeout")
  }

}
