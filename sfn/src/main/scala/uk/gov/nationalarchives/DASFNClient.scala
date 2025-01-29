package uk.gov.nationalarchives

import cats.effect.Async
import cats.implicits.*
import io.circe.syntax.*
import io.circe.{Encoder, Printer}
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sfn.SfnAsyncClient
import software.amazon.awssdk.services.sfn.model.{
  ListExecutionsRequest,
  SendTaskSuccessRequest,
  StartExecutionRequest,
  StartExecutionResponse
}
import uk.gov.nationalarchives.DASFNClient.Status

import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters.*

/** An SFN client. It is written generically so can be used for any effect which has an Async instance. Requires an
  * implicit instance of cats Async which is used to convert CompletableFuture to F
  *
  * @tparam F
  *   Type of the effect
  */
trait DASFNClient[F[_]: Async]:

  /** @param stateMachineArn
    *   The arn of the state machine to start
    * @param input
    *   A case class. This will be deserialised to a json string and sent as input to the step function.
    * @param name
    *   An optional step function name. If this is omitted, AWS will generate a UUID for a name.
    * @param enc
    *   A circe encoder which will encode the case class to JSON
    * @tparam T
    *   The type of the input case class
    * @return
    *   The response from the startExecution call, wrapped in F[_]
    */
  def startExecution[T <: Product](stateMachineArn: String, input: T, name: Option[String] = None)(using
      enc: Encoder[T]
  ): F[StartExecutionResponse]

  def listStepFunctions(stepFunctionArn: String, status: Status): F[List[String]]

  def sendTaskSuccess[T: Encoder](taskToken: String, potentialOutput: Option[T] = None): F[Unit]

object DASFNClient:

  extension [F[_]: Async, T](completableFuture: CompletableFuture[T])
    private def liftF: F[T] = Async[F].fromCompletableFuture(Async[F].pure(completableFuture))

  enum Status(val sdkStatus: String):
    case Running extends Status("RUNNING")
    case Succeeded extends Status("SUCCEEDED")
    case Failed extends Status("FAILED")
    case TimedOut extends Status("TIMED_OUT")
    case Aborted extends Status("ABORTED")
    case PendingRedrive extends Status("PENDING_REDRIVE")

  def apply[F[_]: Async](sfnAsyncClient: SfnAsyncClient): DASFNClient[F] = new DASFNClient[F]:

    override def sendTaskSuccess[T: Encoder](taskToken: String, potentialOutput: Option[T] = None): F[Unit] = {
      val sendTaskSuccessRequest = SendTaskSuccessRequest.builder
        .taskToken(taskToken)
        .output(potentialOutput.map(_.asJson.noSpaces).getOrElse("{}"))
        .build
      sfnAsyncClient.sendTaskSuccess(sendTaskSuccessRequest).liftF.void
    }

    override def listStepFunctions(stepFunctionArn: String, status: Status): F[List[String]] = {
      val listExecutionsRequest = ListExecutionsRequest.builder
        .stateMachineArn(stepFunctionArn)
        .statusFilter(status.sdkStatus)
        .build
      sfnAsyncClient.listExecutions(listExecutionsRequest).liftF.map { response =>
        response.executions().asScala.toList.map(_.name)
      }
    }

    override def startExecution[T <: Product](stateMachineArn: String, input: T, name: Option[String] = None)(using
        enc: Encoder[T]
    ): F[StartExecutionResponse] =
      val builder = StartExecutionRequest.builder()
      val inputString = input.asJson.printWith(Printer.noSpaces)

      val startExecutionRequest: StartExecutionRequest = name
        .map(builder.name)
        .getOrElse(builder)
        .stateMachineArn(stateMachineArn)
        .input(inputString)
        .build()

      Async[F].fromCompletableFuture(Async[F].pure(sfnAsyncClient.startExecution(startExecutionRequest)))

  def apply[F[_]: Async](): DASFNClient[F] = {
    lazy val httpClient: SdkAsyncHttpClient = NettyNioAsyncHttpClient.builder().build()

    lazy val sfnClient: SfnAsyncClient = SfnAsyncClient.builder
      .region(Region.EU_WEST_2)
      .httpClient(httpClient)
      .build()
    DASFNClient[F](sfnClient)
  }
