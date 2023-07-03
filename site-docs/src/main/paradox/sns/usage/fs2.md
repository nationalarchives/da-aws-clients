# Use with Cats Effect

## Setup

You will need these dependencies:

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-sns-client_2.13" version=$version$
}

## Examples
```scala
import cats.effect._
import software.amazon.awssdk.services.sns.model.PublishResponse
import uk.gov.nationalarchives.DASNSClient
import io.circe.generic.auto._ // Used to provide Encoder[T] but you can provide your own

  val snsClient = DASNSClient[IO]()
  val topicArn = "topicArn"
  case class Message(value: String)
  val publishFn: Message => IO[PublishResponse] = snsClient.publish(topicArn)

  val responses: IO[List[String]] = for {
    res1 <- publishFn(Message("message1"))
    res2 <- publishFn(Message("message2"))
  } yield List(res1.sequenceNumber(), res2.sequenceNumber())
```
