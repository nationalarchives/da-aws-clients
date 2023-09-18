# Use with Zio

## Setup

You will need these dependencies:

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-eventbridge-client_2.13" version=$version$
group3="dev.zio" artifact3="zio-interop-cats_2.13" version3="23.0.0.5"
}

`zio-interop-cats` is needed to allow us to use the ZIO Task with the cats type classes

## Examples

### Publish an event message

```scala
import cats.effect._
import uk.gov.nationalarchives.DAEventBridgeClient
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest
import zio._
import zio.interop.catz._
import io.circe.generic.auto._ // Used to provide Encoder[T] but you can provide your own

  def publishToEventBridge(): Task[PutEventsResponse] = {
    val eventBridgeClient = DAEventBridgeClient[Task]()

    case class Detail(value: String)
    
    eventBridgeClient.publishEventToEventBridge[Detail]("sourceId", "detailType", Detail("value"))
  }
```
