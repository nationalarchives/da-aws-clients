package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Encoder
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.*
import org.scalatestplus.mockito.MockitoSugar
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.{PublishBatchRequest, PublishBatchRequestEntry, PublishBatchResponse}

import java.util
import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters.ListHasAsScala

class DASNSClientTest extends AnyFlatSpec with MockitoSugar {

  case class Test(message: String, value: String)

  given Encoder[Test] = Encoder.forProduct2("message", "value")(t => (t.message, t.value))

  "publishBatch" should "send the correct messages to the topic" in {
    val snsAsyncClient = mock[SnsAsyncClient]
    val publishCaptor: ArgumentCaptor[PublishBatchRequest] = ArgumentCaptor.forClass(classOf[PublishBatchRequest])
    val mockResponse = CompletableFuture.completedFuture(PublishBatchResponse.builder().build())
    when(snsAsyncClient.publishBatch(publishCaptor.capture())).thenReturn(mockResponse)

    val client = DASNSClient[IO](snsAsyncClient)
    client
      .publish("mockTopicArn")(List(Test("testMessage1", "testValue1"), Test("testMessage2", "testValue2")))
      .unsafeRunSync()

    val publishValue = publishCaptor.getValue

    val messagesBatch: List[String] = getMessagesFromBatchRequestEntries(publishValue.publishBatchRequestEntries())

    messagesBatch should be(
      List("""{"message":"testMessage1","value":"testValue1"}""", """{"message":"testMessage2","value":"testValue2"}""")
    )

    publishValue.topicArn() should equal("mockTopicArn")
  }

  "publishBatch" should "send up to 10 messages per batch to the topic" in {
    val snsAsyncClient = mock[SnsAsyncClient]
    val publishCaptor: ArgumentCaptor[PublishBatchRequest] = ArgumentCaptor.forClass(classOf[PublishBatchRequest])
    val mockResponse = CompletableFuture.completedFuture(PublishBatchResponse.builder().build())
    when(snsAsyncClient.publishBatch(publishCaptor.capture())).thenReturn(mockResponse)

    val messages = (1 to 23).toList.map(number => Test(s"testMessage$number", s"testValue$number"))
    val convertNumberToJsonMessage = (number: Int) => s"""{"message":"testMessage$number","value":"testValue$number"}"""

    val client = DASNSClient[IO](snsAsyncClient)
    client.publish("mockTopicArn")(messages).unsafeRunSync()

    val publishedBatchRequests: List[PublishBatchRequest] = publishCaptor.getAllValues.asScala.toList
    val batchRequestEntries1 = publishedBatchRequests.head.publishBatchRequestEntries()
    val batchRequestEntries2 = publishedBatchRequests(1).publishBatchRequestEntries()
    val batchRequestEntries3 = publishedBatchRequests(2).publishBatchRequestEntries()

    val messagesBatch1: List[String] = getMessagesFromBatchRequestEntries(batchRequestEntries1)
    val messagesBatch2: List[String] = getMessagesFromBatchRequestEntries(batchRequestEntries2)
    val messagesBatch3: List[String] = getMessagesFromBatchRequestEntries(batchRequestEntries3)

    publishedBatchRequests.length should be(3)
    batchRequestEntries1.size() should be(10)
    messagesBatch1 should be((1 to 10).toList.map(convertNumberToJsonMessage))

    batchRequestEntries2.size() should be(10)
    messagesBatch2 should be((11 to 20).toList.map(convertNumberToJsonMessage))

    batchRequestEntries3.size() should be(3)
    messagesBatch3 should be((21 to 23).toList.map(convertNumberToJsonMessage))
  }

  "publishBatch" should "return an error if there is an error sending to the topic" in {
    val snsAsyncClient = mock[SnsAsyncClient]
    when(snsAsyncClient.publishBatch(any[PublishBatchRequest])).thenThrow(new RuntimeException("Error sending message"))

    val client = DASNSClient[IO](snsAsyncClient)

    val ex = intercept[Exception] {
      client
        .publish("mockTopicArn")(List(Test("testMessage1", "testValue1"), Test("testMessage2", "testValue2")))
        .unsafeRunSync()
    }
    ex.getMessage should equal("Error sending message")
  }

  "publishBatch" should "attempt to send two batches but return an error if the second batch fails" in {
    val snsAsyncClient = mock[SnsAsyncClient]
    val publishCaptor: ArgumentCaptor[PublishBatchRequest] = ArgumentCaptor.forClass(classOf[PublishBatchRequest])
    val mockResponse = CompletableFuture.completedFuture(PublishBatchResponse.builder().build())
    when(snsAsyncClient.publishBatch(publishCaptor.capture()))
      .thenReturn(mockResponse)
      .thenThrow(new RuntimeException("Error sending messages"))

    val messages = (1 to 15).toList.map(number => Test(s"testMessage$number", s"testValue$number"))
    val convertNumberToJsonMessage = (number: Int) => s"""{"message":"testMessage$number","value":"testValue$number"}"""

    val client = DASNSClient[IO](snsAsyncClient)
    val ex = intercept[Exception] {
      client.publish("mockTopicArn")(messages).unsafeRunSync()
    }

    val publishedBatchRequests: List[PublishBatchRequest] = publishCaptor.getAllValues.asScala.toList
    val batchRequestEntries1 = publishedBatchRequests.head.publishBatchRequestEntries()
    val batchRequestEntries2 = publishedBatchRequests(1).publishBatchRequestEntries()

    val messagesBatch1: List[String] = getMessagesFromBatchRequestEntries(batchRequestEntries1)
    val messagesBatch2: List[String] = getMessagesFromBatchRequestEntries(batchRequestEntries2)

    publishedBatchRequests.length should be(2)
    batchRequestEntries1.size() should be(10)
    messagesBatch1 should be((1 to 10).toList.map(convertNumberToJsonMessage))

    batchRequestEntries2.size() should be(5)
    messagesBatch2 should be((11 to 15).toList.map(convertNumberToJsonMessage))

    ex.getMessage should equal("Error sending messages")
  }

  "publishBatch" should "attempt the first batch, return an error if the first batch fails and not send the second batch" in {
    val snsAsyncClient = mock[SnsAsyncClient]
    val publishCaptor: ArgumentCaptor[PublishBatchRequest] = ArgumentCaptor.forClass(classOf[PublishBatchRequest])
    val mockResponse = CompletableFuture.completedFuture(PublishBatchResponse.builder().build())
    when(snsAsyncClient.publishBatch(publishCaptor.capture()))
      .thenThrow(new RuntimeException("Error sending messages"))
      .thenAnswer((_: InvocationOnMock) => mockResponse)

    val messages = (1 to 15).toList.map(number => Test(s"testMessage$number", s"testValue$number"))
    val convertNumberToJsonMessage = (number: Int) => s"""{"message":"testMessage$number","value":"testValue$number"}"""

    val client = DASNSClient[IO](snsAsyncClient)
    val ex = intercept[Exception] {
      client.publish("mockTopicArn")(messages).unsafeRunSync()
    }

    val publishedBatchRequests: List[PublishBatchRequest] = publishCaptor.getAllValues.asScala.toList
    val batchRequestEntries1 = publishedBatchRequests.head.publishBatchRequestEntries()

    val messagesBatch1: List[String] = getMessagesFromBatchRequestEntries(batchRequestEntries1)

    publishedBatchRequests.length should be(1)
    batchRequestEntries1.size() should be(10)
    messagesBatch1 should be((1 to 10).toList.map(convertNumberToJsonMessage))

    ex.getMessage should equal("Error sending messages")
  }

  private def getMessagesFromBatchRequestEntries(
      batchRequestEntry: util.List[PublishBatchRequestEntry]
  ): List[String] = {
    val batchEntries: List[PublishBatchRequestEntry] = batchRequestEntry.asScala.toList
    batchEntries.map(_.message())
  }
}
