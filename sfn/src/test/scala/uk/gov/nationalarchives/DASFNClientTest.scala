package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Encoder
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import software.amazon.awssdk.services.sfn.SfnAsyncClient
import software.amazon.awssdk.services.sfn.model.{StartExecutionRequest, StartExecutionResponse}

import java.util.concurrent.CompletableFuture

class DASFNClientTest extends AnyFlatSpec with MockitoSugar {
  case class Test(message: String, value: String)

  implicit val enc: Encoder[Test] = Encoder.forProduct2("message", "value")(t => (t.message, t.value))
  val arn = "arn:aws:states:eu-west-2:123456789:stateMachine:TestStateMachine"

  "startExecution" should "start an execution with the correct parameters when no name is provided" in {
    val sfnAsyncClient = mock[SfnAsyncClient]
    val startExecutionRequestCaptor: ArgumentCaptor[StartExecutionRequest] =
      ArgumentCaptor.forClass(classOf[StartExecutionRequest])
    val mockResponse = CompletableFuture.completedFuture(StartExecutionResponse.builder().build())
    when(sfnAsyncClient.startExecution(startExecutionRequestCaptor.capture())).thenReturn(mockResponse)

    val client = new DASFNClient[IO](sfnAsyncClient)
    client.startExecution(arn, Test("testMessage", "testValue")).unsafeRunSync()

    val request = startExecutionRequestCaptor.getValue
    Option(request.name).isDefined should be(false)
    request.stateMachineArn should be(arn)
    request.input should be("""{"message":"testMessage","value":"testValue"}""")
  }

  "startExecution" should "start an execution with the specified name if one is provided" in {
    val sfnAsyncClient = mock[SfnAsyncClient]
    val startExecutionRequestCaptor: ArgumentCaptor[StartExecutionRequest] =
      ArgumentCaptor.forClass(classOf[StartExecutionRequest])
    val mockResponse = CompletableFuture.completedFuture(StartExecutionResponse.builder().build())
    when(sfnAsyncClient.startExecution(startExecutionRequestCaptor.capture())).thenReturn(mockResponse)

    val client = new DASFNClient[IO](sfnAsyncClient)

    client.startExecution(arn, Test("testMessage", "testValue"), Option("testName")).unsafeRunSync()

    val request = startExecutionRequestCaptor.getValue
    request.name should be("testName")
    request.stateMachineArn should be(arn)
    request.input should be("""{"message":"testMessage","value":"testValue"}""")
  }

  "startExecution" should "return an error if there is an error from the SFN API" in {
    val sfnAsyncClient = mock[SfnAsyncClient]
    when(sfnAsyncClient.startExecution(any[StartExecutionRequest])).thenThrow(new Exception("Error starting execution"))

    val client = new DASFNClient[IO](sfnAsyncClient)

    val ex = intercept[Exception] {
      client.startExecution(arn, Test("testMessage", "testValue"), Option("testName")).unsafeRunSync()
    }
    ex.getMessage should equal("Error starting execution")
  }
}
