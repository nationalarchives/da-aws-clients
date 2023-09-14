# Use with Zio

## Setup

You will need these dependencies:

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-eventbridge-client_2.13" version=$version$
group3="dev.zio" artifact3="zio-interop-cats_2.13" version3="23.0.0.5"
}

If you want to use the log4cats extension method, you will need log4cats as well.

@@dependency[sbt,Maven,Gradle] {
group="org.typelevel" artifact="log4cats-slf4j_2.13" version="2.6.0"
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

### Use the sendToSlack extension method

```scala
import cats.effect._
import uk.gov.nationalarchives.DAEventBridgeClient._
import org.typelevel.log4cats.slf4j.Slf4jLogger

  def loggerSendToSlack() = {
    implicit val eventBridgeClient: DAEventBridgeClient[Task] = DAEventBridgeClient[Task]()
    implicit val detailType: DetailType = "DetailType".toDetailType
    for {
      logger <- Slf4jLogger.create[Task]
      _ <- logger.sendToSlackMessage("A test slack message")
    } yield ()
  }
```
