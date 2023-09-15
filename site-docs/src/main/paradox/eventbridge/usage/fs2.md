# Use with Cats Effect

## Setup

You will need these dependencies:

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-eventbridge-client_2.13" version=$version$
}

## Examples

### Publish an event message

```scala
import cats.effect._
import uk.gov.nationalarchives.DAEventBridgeClient
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest
import io.circe.generic.auto._ // Used to provide Encoder[T] but you can provide your own

  def publishToEventBridge(): IO[PutEventsResponse] = {
    val eventBridgeClient = DAEventBridgeClient[IO]()

    case class Detail(value: String)
    
    eventBridgeClient.publishEventToEventBridge[Detail]("sourceId", "detailType", Detail("value"))
  }
```
