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
import uk.gov.nationalarchives.DASecretsManagerClient.Stage._

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
trait DASecretsManagerClient[F[_]: Async]:

  /** Generates a random password
    * @param passwordLength
    *   The length of the password. Defaults to 15
    * @param excludeCharacters
    *   Characters to exclude from the password, defaults to ' and "" and \
    * @return
    *   The randomly generated password wrapped with F[_]
    */
  def generateRandomPassword(passwordLength: Int = 15, excludeCharacters: String = "\'\"\\"): F[String]

  /** Describes a secret
    * @return
    *   A DescribeSecretResponse object wrapped in F[_]
    */
  def describeSecret(): F[DescribeSecretResponse]

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
  def getSecretValue[T](stage: Stage = Current)(using decoder: Decoder[T]): F[T]

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
  def getSecretValue[T](versionId: String, stage: Stage)(using decoder: Decoder[T]): F[T]

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
  ): F[PutSecretValueResponse]

  /** Update the version stage. This is used to finalise secret rotation.
    * @param potentialMoveToVersionId
    *   The optional version you are moving to
    * @param potentialRemoveFromVersionId
    *   The optional version you are moving from
    * @param stage
    *   The stage of the version
    * @return
    *   UpdateSecretVersionStageResponse wrapped in F[_]
    */
  def updateSecretVersionStage(
      potentialMoveToVersionId: Option[String],
      potentialRemoveFromVersionId: Option[String],
      stage: Stage = Current
  ): F[UpdateSecretVersionStageResponse]

object DASecretsManagerClient:

  /** Represents a stage a secret can be in. The only possible values are Current, Pending and Previous
    */
  enum Stage:
    override def toString: String = this match
      case Current  => "AWSCURRENT"
      case Pending  => "AWSPENDING"
      case Previous => "AWSPREVIOUS"

    case Current, Pending, Previous

  private lazy val httpClient: SdkAsyncHttpClient = NettyNioAsyncHttpClient.builder().build()
  private lazy val secretsManagerAsyncClient: SecretsManagerAsyncClient = SecretsManagerAsyncClient.builder
    .region(Region.EU_WEST_2)
    .httpClient(httpClient)
    .build()

  def apply[F[_]: Async](
      secretId: String,
      secretsManagerAsyncClient: SecretsManagerAsyncClient = secretsManagerAsyncClient
  ): DASecretsManagerClient[F] =
    new DASecretsManagerClient[F] {

      extension [T](completableFuture: CompletableFuture[T])
        private def liftF: F[T] = Async[F].fromCompletableFuture(Async[F].pure(completableFuture))

      override def generateRandomPassword(passwordLength: Int = 15, excludeCharacters: String = "\'\"\\"): F[String] =
        val request = GetRandomPasswordRequest.builder
          .passwordLength(passwordLength.toLong)
          .excludeCharacters(excludeCharacters)
          .build
        secretsManagerAsyncClient.getRandomPassword(request).liftF.map(_.randomPassword)

      override def describeSecret(): F[DescribeSecretResponse] =
        val request = DescribeSecretRequest.builder().secretId(secretId).build()
        secretsManagerAsyncClient.describeSecret(request).liftF

      override def getSecretValue[T](stage: Stage = Current)(using decoder: Decoder[T]): F[T] = secretValue(stage)

      override def getSecretValue[T](versionId: String, stage: Stage)(using decoder: Decoder[T]): F[T] =
        secretValue(stage, Option(versionId))

      override def putSecretValue[T](secret: T, stage: Stage = Current, clientRequestToken: Option[String] = None)(using
          encoder: Encoder[T]
      ): F[PutSecretValueResponse] =
        val builder = PutSecretValueRequest.builder
          .secretId(secretId)
          .secretString(secret.asJson.printWith(noSpaces))
          .versionStages(stage.toString)
        val request = clientRequestToken.map(builder.clientRequestToken).getOrElse(builder).build
        secretsManagerAsyncClient.putSecretValue(request).liftF

      override def updateSecretVersionStage(
          potentialMoveToVersionId: Option[String],
          potentialRemoveFromVersionId: Option[String],
          stage: Stage = Current
      ): F[UpdateSecretVersionStageResponse] =
        val request = UpdateSecretVersionStageRequest
          .builder()
          .secretId(secretId)
          .versionStage(stage.toString)
          .moveToVersionId(potentialMoveToVersionId.orNull)
          .removeFromVersionId(potentialRemoveFromVersionId.orNull)
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
    }
