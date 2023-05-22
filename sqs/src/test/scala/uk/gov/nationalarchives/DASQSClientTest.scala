package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Encoder
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{SendMessageRequest, SendMessageResponse}

import java.util.concurrent.CompletableFuture

class DASQSClientTest extends AnyFlatSpec with MockitoSugar {

  case class Test(message: String, value: String)

  implicit val enc: Encoder[Test] = Encoder.forProduct2("message", "value")(t => (t.message, t.value))

  "sendMessage" should "send the correct message to the queue" in {
    val sqsAsyncClient = mock[SqsAsyncClient]
    val sendMessageCaptor: ArgumentCaptor[SendMessageRequest] = ArgumentCaptor.forClass(classOf[SendMessageRequest])
    val mockResponse = CompletableFuture.completedFuture(SendMessageResponse.builder().build())
    when(sqsAsyncClient.sendMessage(sendMessageCaptor.capture())).thenReturn(mockResponse)

    val client = new DASQSClient[IO](sqsAsyncClient)
    client.sendMessage("https://test")(Test("testMessage", "testValue")).unsafeRunSync()

    val sendMessageValue = sendMessageCaptor.getValue

    sendMessageValue.messageBody() should equal("""{"message":"testMessage","value":"testValue"}""")
    sendMessageValue.queueUrl() should equal("https://test")
  }

  "sendMessage" should "return an error if there is an error sending to the queue" in {
    val sqsAsyncClient = mock[SqsAsyncClient]
    when(sqsAsyncClient.sendMessage(any[SendMessageRequest])).thenThrow(new Exception("Error sending message"))

    val client = new DASQSClient[IO](sqsAsyncClient)

    val ex = intercept[Exception] {
      client.sendMessage("https://test")(Test("testMessage", "testValue")).unsafeRunSync()
    }
    ex.getMessage should equal("Error sending message")
  }
}
