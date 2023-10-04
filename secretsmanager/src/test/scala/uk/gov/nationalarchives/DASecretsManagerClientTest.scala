package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, MockitoSugar, clazz}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient
import software.amazon.awssdk.services.secretsmanager.model._
import uk.gov.nationalarchives.DASecretsManagerClient.{Current, Pending, Stage}

import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

class DASecretsManagerClientTest extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {
  private val secretsManagerAsyncClient = mock[SecretsManagerAsyncClient]
  private val secretId = "secretId"
  private val mockRandomPasswordResponse = GetRandomPasswordResponse.builder.randomPassword("test").build
  private case class SecretResponse(secret: String)
  private case class SecretRequest(secretRequest: String)
  private case class InvalidSecretResponse(invalidField: String)

  override def beforeEach(): Unit = {
    reset(secretsManagerAsyncClient)
  }

  "generateRandomPassword" should "generate a password with the defaults" in {

    val (client, randomPasswordRequestCaptor) =
      setupClientAndCaptors[GetRandomPasswordRequest, GetRandomPasswordResponse](
        secretsManagerAsyncClient.getRandomPassword,
        mockRandomPasswordResponse
      )

    client.generateRandomPassword(10, ";").unsafeRunSync()

    val randomPasswordRequest = randomPasswordRequestCaptor.getValue
    randomPasswordRequest.passwordLength() should equal(10)
    randomPasswordRequest.excludeCharacters() should equal(";")
  }

  "generateRandomPassword" should "generate a password with the arguments passed in" in {
    val (client, randomPasswordRequestCaptor) =
      setupClientAndCaptors[GetRandomPasswordRequest, GetRandomPasswordResponse](
        secretsManagerAsyncClient.getRandomPassword,
        mockRandomPasswordResponse
      )

    client.generateRandomPassword().unsafeRunSync()

    val randomPasswordRequest = randomPasswordRequestCaptor.getValue
    randomPasswordRequest.passwordLength() should equal(15)
    randomPasswordRequest.excludeCharacters() should equal("\'\"\\")
  }

  "generateRandomPassword" should "return an error if the client returns an error" in {
    when(secretsManagerAsyncClient.getRandomPassword(any[GetRandomPasswordRequest]))
      .thenThrow(new Exception("Error from client"))
    val client = DASecretsManagerClient[IO](secretsManagerAsyncClient, secretId)
    val ex = intercept[Exception] {
      client.generateRandomPassword().unsafeRunSync()
    }
    ex.getMessage should equal("Error from client")

  }

  "describeSecret" should "pass the correct arguments to secrets manager" in {
    val mockResponse = DescribeSecretResponse.builder.name("secretName").build
    val (client, requestCaptor) =
      setupClientAndCaptors[DescribeSecretRequest, DescribeSecretResponse](
        secretsManagerAsyncClient.describeSecret,
        mockResponse
      )

    val response = client.describeSecret().unsafeRunSync()

    requestCaptor.getValue.secretId() should equal(secretId)
    response.name() should equal("secretName")
  }

  "describeSecret" should "return an error if the client returns an error" in {
    when(secretsManagerAsyncClient.describeSecret(any[DescribeSecretRequest]))
      .thenThrow(new Exception("Error from client"))
    val client = DASecretsManagerClient[IO](secretsManagerAsyncClient, secretId)
    val ex = intercept[Exception] {
      client.describeSecret().unsafeRunSync()
    }
    ex.getMessage should equal("Error from client")
  }

  "getSecretValue" should "pass the correct arguments to secrets manager if neither version id or stage are passed" in {
    val mockResponse = GetSecretValueResponse.builder.secretString("""{"secret": "very-secret"}""").build
    val (client, requestCaptor) =
      setupClientAndCaptors[GetSecretValueRequest, GetSecretValueResponse](
        secretsManagerAsyncClient.getSecretValue,
        mockResponse
      )

    client.getSecretValue[SecretResponse]().unsafeRunSync()

    requestCaptor.getValue.versionStage should equal(Current.toString)
    requestCaptor.getValue.versionId() should equal(null)
  }

  "getSecretValue" should "pass the correct arguments to secrets manager if the stage is passed but the version id isn't" in {
    val mockResponse = GetSecretValueResponse.builder.secretString("""{"secret": "very-secret"}""").build
    val (client, requestCaptor) =
      setupClientAndCaptors[GetSecretValueRequest, GetSecretValueResponse](
        secretsManagerAsyncClient.getSecretValue,
        mockResponse
      )

    client.getSecretValue[SecretResponse](Pending).unsafeRunSync()

    requestCaptor.getValue.versionStage should equal(Pending.toString)
    requestCaptor.getValue.versionId() should equal(null)
  }

  "getSecretValue" should "pass the correct arguments to secrets manager if the stage and version id are passed" in {
    val mockResponse = GetSecretValueResponse.builder.secretString("""{"secret": "very-secret"}""").build
    val (client, requestCaptor) =
      setupClientAndCaptors[GetSecretValueRequest, GetSecretValueResponse](
        secretsManagerAsyncClient.getSecretValue,
        mockResponse
      )

    client.getSecretValue[SecretResponse]("VersionId", Pending).unsafeRunSync()

    requestCaptor.getValue.versionStage should equal(Pending.toString)
    requestCaptor.getValue.versionId() should equal("VersionId")
  }

  "getSecretValue" should "return an error if the secrets manager response doesn't match the provided type" in {
    val mockResponse = GetSecretValueResponse.builder.secretString("""{"secret": "very-secret"}""").build
    val (client, _) =
      setupClientAndCaptors[GetSecretValueRequest, GetSecretValueResponse](
        secretsManagerAsyncClient.getSecretValue,
        mockResponse
      )

    val ex = intercept[Exception] {
      client.getSecretValue[InvalidSecretResponse]("VersionId", Pending).unsafeRunSync()
    }
    ex.getMessage should equal("DecodingFailure at .invalidField: Missing required field")
  }

  "getSecretValue" should "return an error if the client returns an error" in {
    when(secretsManagerAsyncClient.getSecretValue(any[GetSecretValueRequest]))
      .thenThrow(new Exception("Error from client"))
    val client = DASecretsManagerClient[IO](secretsManagerAsyncClient, secretId)
    val ex = intercept[Exception] {
      client.getSecretValue[SecretResponse]().unsafeRunSync()
    }
    ex.getMessage should equal("Error from client")
  }

  "putSecretValue" should "send the correct arguments to secrets manager if no stage or token are passed" in {
    val mockResponse = PutSecretValueResponse.builder.name("secretName").build()
    val (client, argumentCaptor) =
      setupClientAndCaptors[PutSecretValueRequest, PutSecretValueResponse](
        secretsManagerAsyncClient.putSecretValue,
        mockResponse
      )

    client.putSecretValue(SecretRequest("secret")).unsafeRunSync()

    checkPutSecretValue(argumentCaptor)
  }

  "putSecretValue" should "send the correct arguments to secrets manager if the stage is passed but not the token" in {
    val mockResponse = PutSecretValueResponse.builder.name("secretName").build()
    val (client, argumentCaptor) =
      setupClientAndCaptors[PutSecretValueRequest, PutSecretValueResponse](
        secretsManagerAsyncClient.putSecretValue,
        mockResponse
      )

    client.putSecretValue(SecretRequest("secret"), Pending).unsafeRunSync()

    checkPutSecretValue(argumentCaptor, Pending)
  }

  "putSecretValue" should "send the correct arguments to secrets manager if the token and stage are passed" in {
    val mockResponse = PutSecretValueResponse.builder.name("secretName").build()
    val (client, argumentCaptor) =
      setupClientAndCaptors[PutSecretValueRequest, PutSecretValueResponse](
        secretsManagerAsyncClient.putSecretValue,
        mockResponse
      )

    val token = Option("token")

    client.putSecretValue(SecretRequest("secret"), Pending, token).unsafeRunSync()

    checkPutSecretValue(argumentCaptor, Pending, token)
  }

  "putSecretValue" should "return an error if the client returns an error" in {
    when(secretsManagerAsyncClient.putSecretValue(any[PutSecretValueRequest]))
      .thenThrow(new Exception("Error from client"))
    val client = DASecretsManagerClient[IO](secretsManagerAsyncClient, secretId)
    val ex = intercept[Exception] {
      client.putSecretValue(SecretRequest("secret")).unsafeRunSync()
    }
    ex.getMessage should equal("Error from client")
  }

  "updateSecretVersionStage" should "send the correct arguments to secrets manager if no stage is passed" in {
    val mockResponse = UpdateSecretVersionStageResponse.builder.name("secretName").build()
    val (client, argumentCaptor) =
      setupClientAndCaptors[UpdateSecretVersionStageRequest, UpdateSecretVersionStageResponse](
        secretsManagerAsyncClient.updateSecretVersionStage,
        mockResponse
      )

    client.updateSecretVersionStage("moveToVersionId", "removeFromVersionId").unsafeRunSync()

    checkUpdateSecretVersionResponse(argumentCaptor, Current)
  }

  "updateSecretVersionStage" should "send the correct arguments to secrets manager if the stage is passed" in {
    val mockResponse = UpdateSecretVersionStageResponse.builder.name("secretName").build()
    val (client, argumentCaptor) =
      setupClientAndCaptors[UpdateSecretVersionStageRequest, UpdateSecretVersionStageResponse](
        secretsManagerAsyncClient.updateSecretVersionStage,
        mockResponse
      )

    client.updateSecretVersionStage("moveToVersionId", "removeFromVersionId", Pending).unsafeRunSync()

    checkUpdateSecretVersionResponse(argumentCaptor, Pending)
  }

  "updateSecretVersionStage" should "return an error if the client returns an error" in {
    when(secretsManagerAsyncClient.updateSecretVersionStage(any[UpdateSecretVersionStageRequest]))
      .thenThrow(new Exception("Error from client"))
    val client = DASecretsManagerClient[IO](secretsManagerAsyncClient, secretId)
    val ex = intercept[Exception] {
      client.updateSecretVersionStage("moveToVersion", "removeFromVersion").unsafeRunSync()
    }
    ex.getMessage should equal("Error from client")
  }

  private def checkUpdateSecretVersionResponse(
      argumentCaptor: ArgumentCaptor[UpdateSecretVersionStageRequest],
      stage: Stage
  ) = {
    val updateSecretVersionStageResponse = argumentCaptor.getValue
    updateSecretVersionStageResponse.moveToVersionId should equal("moveToVersionId")
    updateSecretVersionStageResponse.removeFromVersionId should equal("removeFromVersionId")
    updateSecretVersionStageResponse.versionStage should equal(stage.toString)
  }

  private def checkPutSecretValue(
      argumentCaptor: ArgumentCaptor[PutSecretValueRequest],
      stage: Stage = Current,
      token: Option[String] = None
  ) = {
    val putSecretValueRequest = argumentCaptor.getValue
    putSecretValueRequest.secretString should equal("""{"secretRequest":"secret"}""")
    putSecretValueRequest.clientRequestToken should equal(token.orNull)
    val versionStages = putSecretValueRequest.versionStages.asScala
    versionStages.size should equal(1)
    versionStages.head should equal(stage.toString)
  }

  private def setupClientAndCaptors[T: ClassTag, R](
      fnToTest: T => CompletableFuture[R],
      mockResponse: R
  ): (DASecretsManagerClient[IO], ArgumentCaptor[T]) = {
    val client = DASecretsManagerClient[IO](secretsManagerAsyncClient, secretId)
    val requestCaptor: ArgumentCaptor[T] = ArgumentCaptor.forClass(clazz[T])
    val response: CompletableFuture[R] = CompletableFuture.completedFuture(mockResponse)
    when(fnToTest.apply(requestCaptor.capture())).thenReturn(response)
    (client, requestCaptor)
  }
}
