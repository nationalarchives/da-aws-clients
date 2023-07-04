package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Encoder
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.{PublishBatchRequest, PublishBatchRequestEntry, PublishBatchResponse}

import java.util
import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters.ListHasAsScala

class DASNSClientTest extends AnyFlatSpec with MockitoSugar {

  case class Test(message: String, value: String)

  implicit val enc: Encoder[Test] = Encoder.forProduct2("message", "value")(t => (t.message, t.value))

  "publishBatch" should "send the correct messages to the topic" in {
    val snsAsyncClient = mock[SnsAsyncClient]
    val publishCaptor: ArgumentCaptor[PublishBatchRequest] = ArgumentCaptor.forClass(classOf[PublishBatchRequest])
    val mockResponse = CompletableFuture.completedFuture(PublishBatchResponse.builder().build())
    when(snsAsyncClient.publishBatch(publishCaptor.capture())).thenReturn(mockResponse)

    val client = new DASNSClient[IO](snsAsyncClient)
    client.publish("mockTopicArn")(List(Test("testMessage1", "testValue1"), Test("testMessage2", "testValue2"))).unsafeRunSync()

    val publishValue = publishCaptor.getValue

    val batchMessages: List[String] = getMessagesFromBatchRequestEntries(publishValue.publishBatchRequestEntries())

    batchMessages should be(
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

    val client = new DASNSClient[IO](snsAsyncClient)
    client.publish("mockTopicArn")(messages).unsafeRunSync()

    val publishedBatchRequests: List[PublishBatchRequest] = publishCaptor.getAllValues.asScala.toList
    val batchRequestEntries1 =  publishedBatchRequests.head.publishBatchRequestEntries()
    val batchRequestEntries2 =  publishedBatchRequests(1).publishBatchRequestEntries()
    val batchRequestEntries3 =  publishedBatchRequests(2).publishBatchRequestEntries()

    val batchMessages1: List[String] = getMessagesFromBatchRequestEntries(batchRequestEntries1)
    val batchMessages2: List[String] = getMessagesFromBatchRequestEntries(batchRequestEntries2)
    val batchMessages3: List[String] = getMessagesFromBatchRequestEntries(batchRequestEntries3)

    publishedBatchRequests.length should be(3)
    batchRequestEntries1.size() should be(10)
    batchMessages1 should be((1 to 10).toList.map(convertNumberToJsonMessage))

    batchRequestEntries2.size() should be(10)
    batchMessages2 should be((11 to 20).toList.map(convertNumberToJsonMessage))

    batchRequestEntries3.size() should be(3)
    batchMessages3 should be((21 to 23).toList.map(convertNumberToJsonMessage))
  }

  "publishBatch" should "return an error if there is an error sending to the topic" in {
    val snsAsyncClient = mock[SnsAsyncClient]
    when(snsAsyncClient.publishBatch(any[PublishBatchRequest])).thenThrow(new Exception("Error sending message"))

    val client = new DASNSClient[IO](snsAsyncClient)

    val ex = intercept[Exception] {
      client.publish("mockTopicArn")(List(Test("testMessage1", "testValue1"), Test("testMessage2", "testValue2"))).unsafeRunSync()
    }
    ex.getMessage should equal("Error sending message")
  }

  private def getMessagesFromBatchRequestEntries(batchRequestEntry: util.List[PublishBatchRequestEntry]): List[String] = {
    val batchEntries: List[PublishBatchRequestEntry] = batchRequestEntry.asScala.toList
    batchEntries.map(_.message())
  }
}
