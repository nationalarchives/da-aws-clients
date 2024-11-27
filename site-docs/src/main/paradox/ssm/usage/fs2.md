# Use with Cats Effect

## Setup

You will need these dependencies:

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-ssm-client_2.13" version=$version$
}

## Examples
```scala
import cats.effect._
import uk.gov.nationalarchives.DASSMClient
import io.circe.generic.auto._ // Used to provide Encoder[T]

  case class StoreValue(key: String, value: String)
  val ssmClient: DASSMClient[IO] = DASSMClient[IO]()

  ssmClient.getParameter[StoreValue]("/test/name")
  ssmClient.getParameter[StoreValue]("/test/name/encrypted", withDecryption = true)
```
