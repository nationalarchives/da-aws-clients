package uk.gov.nationalarchives

import cats.effect.Async
import cats.implicits.*
import io.circe.Decoder
import io.circe.parser.decode
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmAsyncClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

/** An SSM client. It is written generically so can be used for any effect which has an Async instance. Requires an
 * implicit instance of cats Async which is used to convert CompletableFuture to F
 *
 * @tparam F
 * Type of the effect
 */
trait DASSMClient[F[_]]:

  /**
   *
   * @param parameterName
   *   The name of the SSM parameter
   * @param withDecryption
   *   Whether to decrypt the parameter
   * @tparam T
   *   The return type of the decoded string
   * @return
   *   An object T decoded from the parameter value
   */
  def getParameter[T](parameterName: String, withDecryption: Boolean = false)(using Decoder[T]): F[T]

object DASSMClient:

  private lazy val httpClient: SdkAsyncHttpClient = NettyNioAsyncHttpClient.builder.build
  private lazy val ssmAsyncClient = SsmAsyncClient.builder.region(Region.EU_WEST_2).httpClient(httpClient).build

  def apply[F[_]: Async](ssmAsyncClient: SsmAsyncClient = ssmAsyncClient): DASSMClient[F] =
    new DASSMClient[F]:
      override def getParameter[T](parameterName: String, withDecryption: Boolean)(using enc: Decoder[T]): F[T] =
        val request = GetParameterRequest.builder.name(parameterName).withDecryption(withDecryption).build
        Async[F].fromCompletableFuture(Async[F].pure(ssmAsyncClient.getParameter(request))).flatMap { response =>
          Async[F].fromEither(decode[T](response.parameter().value()))
        }
