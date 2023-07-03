package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Encoder
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.{PublishRequest, PublishResponse}

import java.util.concurrent.CompletableFuture

class DASNSClientTest extends AnyFlatSpec with MockitoSugar {

  case class Test(message: String, value: String)

  implicit val enc: Encoder[Test] = Encoder.forProduct2("message", "value")(t => (t.message, t.value))

  "publish" should "send the correct message to the topic" in {
    val snsAsyncClient = mock[SnsAsyncClient]
    val publishCaptor: ArgumentCaptor[PublishRequest] = ArgumentCaptor.forClass(classOf[PublishRequest])
    val mockResponse = CompletableFuture.completedFuture(PublishResponse.builder().build())
    when(snsAsyncClient.publish(publishCaptor.capture())).thenReturn(mockResponse)

    val client = new DASNSClient[IO](snsAsyncClient)
    client.publish("mockTopicArn")(Test("testMessage", "testValue")).unsafeRunSync()

    val publishValue = publishCaptor.getValue

    publishValue.message() should equal("""{"message":"testMessage","value":"testValue"}""")
    publishValue.topicArn() should equal("mockTopicArn")
  }

  "publish" should "return an error if there is an error sending to the topic" in {
    val snsAsyncClient = mock[SnsAsyncClient]
    when(snsAsyncClient.publish(any[PublishRequest])).thenThrow(new Exception("Error sending message"))

    val client = new DASNSClient[IO](snsAsyncClient)

    val ex = intercept[Exception] {
      client.publish("mockTopicArn")(Test("testMessage", "testValue")).unsafeRunSync()
    }
    ex.getMessage should equal("Error sending message")
  }
}
