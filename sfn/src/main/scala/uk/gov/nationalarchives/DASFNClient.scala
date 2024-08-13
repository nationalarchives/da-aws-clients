package uk.gov.nationalarchives

import cats.effect.Async
import io.circe.{Encoder, Printer}
import io.circe.syntax._
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sfn.SfnAsyncClient
import software.amazon.awssdk.services.sfn.model.{StartExecutionRequest, StartExecutionResponse}

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

object DASFNClient:

  def apply[F[_]: Async](sfnAsyncClient: SfnAsyncClient): DASFNClient[F] = new DASFNClient[F]:
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
