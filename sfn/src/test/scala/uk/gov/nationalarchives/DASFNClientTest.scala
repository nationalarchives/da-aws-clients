package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Encoder
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.*
import software.amazon.awssdk.services.sfn.SfnAsyncClient
import software.amazon.awssdk.services.sfn.model.*
import uk.gov.nationalarchives.DASFNClient.Status.*

import java.util.concurrent.CompletableFuture
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

class DASFNClientTest extends AnyFlatSpec with EitherValues {
  case class TestInput(message: String, value: String)

  case class SfnExecutions(name: String, sfnArn: String, input: String, status: String, taskToken: String = "")
  given Encoder[TestInput] = Encoder.forProduct2("message", "value")(t => (t.message, t.value))
  val arn = "arn:aws:states:eu-west-2:123456789:stateMachine:TestStateMachine"

  case class Errors(startExecution: Boolean = false, listExecutions: Boolean = false, sendTaskSuccess: Boolean = false)

  def createClient(initial: ListBuffer[SfnExecutions], errors: Option[Errors] = None): SfnAsyncClient =
    new SfnAsyncClient:
      override def serviceName(): String = "test"

      override def close(): Unit = ()

      override def startExecution(
          startExecutionRequest: StartExecutionRequest
      ): CompletableFuture[StartExecutionResponse] =
        if errors.exists(_.startExecution) then throw new Exception("Error starting execution")
        else {
          initial += SfnExecutions(
            startExecutionRequest.name(),
            startExecutionRequest.stateMachineArn(),
            startExecutionRequest.input(),
            "running"
          )
          CompletableFuture.completedFuture(StartExecutionResponse.builder.build)
        }

      override def listExecutions(
          listExecutionsRequest: ListExecutionsRequest
      ): CompletableFuture[ListExecutionsResponse] = {
        if errors.exists(_.listExecutions) then throw new Exception("Error listing executions")
        else
          CompletableFuture.completedFuture {
            val executions = initial
              .filter(i =>
                i.sfnArn == listExecutionsRequest
                  .stateMachineArn() && i.status == listExecutionsRequest.statusFilter().toString.toLowerCase()
              )
              .map { execution =>
                ExecutionListItem.builder.name(execution.name).stateMachineArn(execution.sfnArn).build
              }
            ListExecutionsResponse.builder.executions(executions.asJava).build
          }
      }

      override def sendTaskSuccess(
          sendTaskSuccessRequest: SendTaskSuccessRequest
      ): CompletableFuture[SendTaskSuccessResponse] = {
        if errors.exists(_.sendTaskSuccess) then throw new Exception("Error sending task success")
        else
          initial
            .find(_.taskToken == sendTaskSuccessRequest.taskToken())
            .map { sfnExection =>
              CompletableFuture.completedFuture(SendTaskSuccessResponse.builder.build)
            }
            .getOrElse(
              throw TaskDoesNotExistException.builder
                .message(s"Task ${sendTaskSuccessRequest.taskToken()} does not exist")
                .build
            )
      }

  "startExecution" should "start an execution with the correct parameters when no name is provided" in {
    val sfnExecutions = ListBuffer[SfnExecutions]()
    val sfnAsyncClient = createClient(sfnExecutions)

    val client = DASFNClient[IO](sfnAsyncClient)
    client.startExecution(arn, TestInput("testMessage", "testValue")).unsafeRunSync()

    val request = sfnExecutions.head
    Option(request.name).isDefined should be(false)
    request.sfnArn should be(arn)
    request.input should be("""{"message":"testMessage","value":"testValue"}""")
  }

  "startExecution" should "start an execution with the specified name if one is provided" in {
    val sfnExecutions = ListBuffer[SfnExecutions]()
    val sfnAsyncClient = createClient(sfnExecutions)

    val client = DASFNClient[IO](sfnAsyncClient)
    client.startExecution(arn, TestInput("testMessage", "testValue"), Option("testName")).unsafeRunSync()

    val request = sfnExecutions.head
    request.name should be("testName")
    request.sfnArn should be(arn)
    request.input should be("""{"message":"testMessage","value":"testValue"}""")
  }

  "startExecution" should "return an error if there is an error from the SFN API" in {
    val sfnExecutions = ListBuffer[SfnExecutions]()
    val sfnAsyncClient = createClient(sfnExecutions, errors = Option(Errors(startExecution = true)))

    val client = DASFNClient[IO](sfnAsyncClient)
    val ex = intercept[Exception] {
      client.startExecution(arn, TestInput("testMessage", "testValue"), Option("testName")).attempt.unsafeRunSync()
    }

    ex.getMessage should equal("Error starting execution")
  }

  "listExecutions" should "list executions with the correct status and arn" in {
    val initialExecutions = ListBuffer(
      SfnExecutions("name1running", "arn1", "", "running"),
      SfnExecutions("name2failed", "arn2", "", "failed"),
      SfnExecutions("name2running", "arn2", "", "running")
    )
    val sfnAsyncClient = createClient(initialExecutions)
    val client = DASFNClient[IO](sfnAsyncClient)

    val runningArn2 = client.listStepFunctions("arn2", Running).unsafeRunSync()
    val failedArn2 = client.listStepFunctions("arn2", Failed).unsafeRunSync()
    val runningArn1 = client.listStepFunctions("arn1", Running).unsafeRunSync()

    runningArn2.size should equal(1)
    runningArn1.size should equal(1)
    failedArn2.size should equal(1)

    runningArn2.head should equal("name2running")
    runningArn1.head should equal("name1running")
    failedArn2.head should equal("name2failed")
  }

  "listExecutions" should "return an error if there is an error from AWS" in {
    val initialExecutions = ListBuffer[SfnExecutions]()
    val sfnAsyncClient = createClient(initialExecutions, errors = Option(Errors(listExecutions = true)))
    val client = DASFNClient[IO](sfnAsyncClient)

    val ex = intercept[Exception] {
      client.listStepFunctions("arn2", Running).attempt.unsafeRunSync()
    }

    ex.getMessage should equal("Error listing executions")
  }

  "sendTaskSuccess" should "return no errors if the task token is valid" in {
    val initialExecutions = ListBuffer(
      SfnExecutions("name1running", "arn1", "", "running", "taskToken")
    )
    val sfnAsyncClient = createClient(initialExecutions)
    val client = DASFNClient[IO](sfnAsyncClient)

    client.sendTaskSuccess("taskToken").unsafeRunSync()
  }

  "sendTaskSuccess" should "return an error if the task token is invalid" in {
    val initialExecutions = ListBuffer(
      SfnExecutions("name1running", "arn1", "", "running", "taskToken")
    )
    val sfnAsyncClient = createClient(initialExecutions)
    val client = DASFNClient[IO](sfnAsyncClient)

    val ex = intercept[TaskDoesNotExistException] {
      client.sendTaskSuccess("invalidTaskToken").unsafeRunSync()
    }
    ex.getMessage should equal("Task invalidTaskToken does not exist")
  }

  "sendTaskSuccess" should "return an error if there is an error with the sdk" in {
    val initialExecutions = ListBuffer[SfnExecutions]()
    val sfnAsyncClient = createClient(initialExecutions, errors = Option(Errors(sendTaskSuccess = true)))
    val client = DASFNClient[IO](sfnAsyncClient)

    val ex = intercept[Exception] {
      client.sendTaskSuccess("taskToken").unsafeRunSync()
    }
    ex.getMessage should equal("Error sending task success")
  }
}
