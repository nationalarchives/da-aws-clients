# Use with Cats Effect

## Setup

You will need these dependencies:

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-secretsmanager-client_2.13" version=$version$
}

## Examples
```scala
import cats.effect._
import io.circe.generic.auto._ // Used to provide Encoder[T] and Decoder[T] but you can provide your own
import software.amazon.awssdk.services.secretsmanager.model._
import uk.gov.nationalarchives.DASecretsManagerClient._

object Examples {
  case class SecretResponse(username: String, password: String)
  case class SecretRequest(username: String, password: String)
  val secretsManagerAsyncClient: SecretsManagerAsyncClient = ???
  val client: DASecretsManagerClient[IO] = DASecretsManagerClient[IO]("secretId")
  val clientWithCustomSecretsManagerClient: DASecretsManagerClient[IO] = DASecretsManagerClient[IO](secretsManagerAsyncClient, "secretId")

  val randomPasswordWithDefaults: IO[String] = client.generateRandomPassword()
  val customisedRandomPassword: IO[String] = client.generateRandomPassword(passwordLength = 20, excludeCharacters = ";,")

  val describeSecret: IO[DescribeSecretResponse] = client.describeSecret()

  val secretValueWithoutVersionId: IO[SecretResponse] = client.getSecretValue[SecretResponse]()
  val secretValueWithoutVersionIdAndDifferentStage: IO[SecretResponse] = client.getSecretValue[SecretResponse](Pending)
  val secretValueWithVersionIdAndDifferentStage: IO[SecretResponse] = client.getSecretValue[SecretResponse]("versionI", Pending)

  val putSecretValue: IO[PutSecretValueResponse] = client.putSecretValue(SecretRequest("username", "password"))
  val putSecretValueWithStage: IO[PutSecretValueResponse] = client.putSecretValue(SecretRequest("username", "password"), Pending)
  val putSecretValueWithStageAndToken: IO[PutSecretValueResponse] = client.putSecretValue(SecretRequest("username", "password"), Pending, Option("clientRequestToken"))

  val updateSecretVersion: IO[UpdateSecretVersionStageResponse] = client.updateSecretVersionStage(Option("moveToVersion"), Option("removeFromVersion"))
  val updateSecretVersionWithStage: IO[UpdateSecretVersionStageResponse] = client.updateSecretVersionStage(Option("moveToVersion"), Option("removeFromVersion"), Pending)
  val updateSecretVersionNoMoveTo: IO[UpdateSecretVersionStageResponse] = client.updateSecretVersionStage(None, Option("removeFromVersion"), Pending)
  val updateSecretVersionNoRemoveFrom: IO[UpdateSecretVersionStageResponse] = client.updateSecretVersionStage(Option("moveToVersion"), None, Pending)
}
```
