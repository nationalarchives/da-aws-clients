# Use with ZIO

## Setup
You will need these dependencies

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-ssm-client_2.13" version=$version$
group3="dev.zio" artifact3="zio-interop-cats_2.13" version3="23.0.0.5"
}

## Examples
```scala
import uk.gov.nationalarchives.DASSMClient
import zio.Task
import zio.interop.catz._
import io.circe.generic.auto._ // Used to provide Decoder[T]

    case class StoreValue(key: String, value: String)
    val ssmClient: DASSMClient[Task] = DASSMClient[IO]()
    
    ssmClient.getParameter[StoreValue]("/test/name")
    ssmClient.getParameter[StoreValue]("/test/name/encrypted", withDecryption = true)
```
