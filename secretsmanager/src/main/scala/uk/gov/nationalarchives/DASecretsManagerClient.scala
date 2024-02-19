package uk.gov.nationalarchives

import cats.effect.Async
import cats.implicits._
import io.circe.Printer.noSpaces
import io.circe._
import io.circe.parser.decode
import io.circe.syntax._
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient
import software.amazon.awssdk.services.secretsmanager.model._
import uk.gov.nationalarchives.DASecretsManagerClient._

import java.util.concurrent.CompletableFuture

/** A secrets manager client. It is written generically so can be used for any effect which has an Async instance.
  * Requires an implicit instance of cats Async which is used to convert CompletableFuture to F
  * @param secretsManagerAsyncClient
  *   An AWS secrets manager async client
  * @param secretId
  *   The id of the secret. You will need multiple client instances for multiple secrets
  * @tparam F
  *   The type of the effect
  */
class DASecretsManagerClient[F[_]: Async](secretsManagerAsyncClient: SecretsManagerAsyncClient, secretId: String):

  extension [T](completableFuture: CompletableFuture[T])
    private def liftF: F[T] = Async[F].fromCompletableFuture(Async[F].pure(completableFuture))

  /** Generates a random password
    * @param passwordLength
    *   The length of the password. Defaults to 15
    * @param excludeCharacters
    *   Characters to exclude from the password, defaults to ' and "" and \
    * @return
    *   The randomly generated password wrapped with F[_]
    */
  def generateRandomPassword(passwordLength: Int = 15, excludeCharacters: String = "\'\"\\"): F[String] =
    val request = GetRandomPasswordRequest.builder
      .passwordLength(passwordLength.toLong)
      .excludeCharacters(excludeCharacters)
      .build
    secretsManagerAsyncClient.getRandomPassword(request).liftF.map(_.randomPassword)

  /** Describes a secret
    * @return
    *   A DescribeSecretResponse object wrapped in F[_]
    */
  def describeSecret(): F[DescribeSecretResponse] =
    val request = DescribeSecretRequest.builder().secretId(secretId).build()
    secretsManagerAsyncClient.describeSecret(request).liftF

  /** Gets a secret value by stage with a default of Current
    * @param stage
    *   The stage you want the value for, either Current or Pending
    * @param decoder
    *   A circe decoder to convert the secrets manager response to a case class
    * @tparam T
    *   The type of the case class to return
    * @return
    *   An instance of type T wrapped in F[_]
    */
  def getSecretValue[T](stage: Stage = Current)(using decoder: Decoder[T]): F[T] = secretValue(stage)

  /** Gets a secret value by stage and version id
    * @param stage
    *   The stage you want the value for, either Current or Pending
    * @param versionId
    *   The version id of the secret you want to retrieve.
    * @param decoder
    *   A circe decoder to convert the secrets manager response to a case class
    * @tparam T
    *   The type of the case class to return
    * @return
    *   An instance of type T wrapped in F[_]
    */
  def getSecretValue[T](versionId: String, stage: Stage)(using decoder: Decoder[T]): F[T] =
    secretValue(stage, Option(versionId))

  /** Creates a new secret value for a given stage and optional request token.
    * @param secret
    *   An object of type T. This will be deserialised to a string and passed to secrets manager
    * @param stage
    *   The stage to put the value into. The default is Current.
    * @param clientRequestToken
    *   An optional clientRequestToken
    * @param encoder
    *   A circe encoder to convert the secret from T to String
    * @tparam T
    *   The type of the secret object
    * @return
    *   PutSecretValueResponse wrapped in F[_]
    */
  def putSecretValue[T](secret: T, stage: Stage = Current, clientRequestToken: Option[String] = None)(using
      encoder: Encoder[T]
  ): F[PutSecretValueResponse] =
    val builder = PutSecretValueRequest.builder
      .secretId(secretId)
      .secretString(secret.asJson.printWith(noSpaces))
      .versionStages(stage.toString)
    val request = clientRequestToken.map(builder.clientRequestToken).getOrElse(builder).build
    secretsManagerAsyncClient.putSecretValue(request).liftF

  /** Update the version stage. This is used to finalise secret rotation.
    * @param moveToVersionId
    *   The version you are moving to
    * @param removeFromVersionId
    *   The version you are moving from
    * @param stage
    *   The stage of the version
    * @return
    *   UpdateSecretVersionStageResponse wrapped in F[_]
    */
  def updateSecretVersionStage(
      moveToVersionId: String,
      removeFromVersionId: String,
      stage: Stage = Current
  ): F[UpdateSecretVersionStageResponse] =
    val request = UpdateSecretVersionStageRequest
      .builder()
      .secretId(secretId)
      .versionStage(stage.toString)
      .moveToVersionId(moveToVersionId)
      .removeFromVersionId(removeFromVersionId)
      .build
    secretsManagerAsyncClient.updateSecretVersionStage(request).liftF

  private def secretValue[T](stage: Stage, versionId: Option[String] = None)(using decoder: Decoder[T]) =
    val builder = GetSecretValueRequest.builder
      .secretId(secretId)
      .versionStage(stage.toString)

    val request = versionId.map(builder.versionId).getOrElse(builder).build
    secretsManagerAsyncClient.getSecretValue(request).liftF.flatMap { response =>
      Async[F].fromEither(decode[T](response.secretString()))
    }
object DASecretsManagerClient:

  /** Represents a stage a secret can be in. The only possible values are Current and Pending
    */
  sealed trait Stage:
    override def toString: String = this match
      case Current => "AWSCURRENT"
      case Pending => "AWSPENDING"

  /** This maps to the AWSCURRENT stage
    */
  case object Current extends Stage

  /** This maps to the AWSPENDING stage
    */
  case object Pending extends Stage

  private val httpClient: SdkAsyncHttpClient = NettyNioAsyncHttpClient.builder().build()
  private val secretsManagerAsyncClient: SecretsManagerAsyncClient = SecretsManagerAsyncClient.builder
    .region(Region.EU_WEST_2)
    .httpClient(httpClient)
    .build()

  def apply[F[_]: Async](secretId: String) = new DASecretsManagerClient[F](secretsManagerAsyncClient, secretId)
  def apply[F[_]: Async](secretsManagerAsyncClient: SecretsManagerAsyncClient, secretId: String) =
    new DASecretsManagerClient[F](secretsManagerAsyncClient, secretId)
