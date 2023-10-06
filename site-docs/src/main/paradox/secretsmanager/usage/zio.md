# Use with ZIO

## Setup
You will need these dependencies

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-secretsmanager-client_2.13" version=$version$
group3="dev.zio" artifact3="zio-interop-cats_2.13" version3="23.0.0.5"
}

## Examples
```scala
import zio.Task
import zio.interop.catz._
import io.circe.generic.auto._ // Used to provide Encoder[T] and Decoder[T] but you can provide your own
import software.amazon.awssdk.services.secretsmanager.model._
import uk.gov.nationalarchives.DASecretsManagerClient._

object Examples {
  case class SecretResponse(username: String, password: String)
  case class SecretRequest(username: String, password: String)
  val secretsManagerAsyncClient: SecretsManagerAsyncClient = ???
  val client: DASecretsManagerClient[IO] = DASecretsManagerClient[IO]("secretId")
  val clientWithCustomSecretsManagerClient: DASecretsManagerClient[IO] = DASecretsManagerClient[IO](secretsManagerAsyncClient, "secretId")

  val randomPasswordWithDefaults: Task[String] = client.generateRandomPassword()
  val customisedRandomPassword: Task[String] = client.generateRandomPassword(passwordLength = 20, excludeCharacters = ";,")

  val describeSecret: Task[DescribeSecretResponse] = client.describeSecret()

  val secretValueWithoutVersionId: Task[SecretResponse] = client.getSecretValue[SecretResponse]()
  val secretValueWithoutVersionIdAndDifferentStage: Task[SecretResponse] = client.getSecretValue[SecretResponse](Pending)
  val secretValueWithVersionIdAndDifferentStage: Task[SecretResponse] = client.getSecretValue[SecretResponse]("versionI", Pending)

  val putSecretValue: Task[PutSecretValueResponse] = client.putSecretValue(SecretRequest("username", "password"))
  val putSecretValueWithStage: Task[PutSecretValueResponse] = client.putSecretValue(SecretRequest("username", "password"), Pending)
  val putSecretValueWithStageAndToken: Task[PutSecretValueResponse] = client.putSecretValue(SecretRequest("username", "password"), Pending, Option("clientRequestToken"))

  val updateSecretVersion: Task[UpdateSecretVersionStageResponse] = client.updateSecretVersionStage("moveToVersion", "removeFromVersion")
  val updateSecretVersionWithStage: Task[UpdateSecretVersionStageResponse] = client.updateSecretVersionStage("moveToVersion", "removeFromVersion", Pending)
}
```
