# Use with Cats Effect

## Setup

You will need these dependencies:

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-sqs-client_2.13" version=$version$
}

## Examples
```scala
import cats.effect._
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.DASQSClient
import io.circe.generic.auto._ // Used to provide Encoder[T] but you can provide your own

  val sqsClient = DASQSClient[IO]()
  val queueUrl = "https://queueurl"
  case class Message(value: String)
  val sendMessageFn: Message => IO[SendMessageResponse] = sqsClient.sendMessage(queueUrl)

  val responses: IO[List[String]] = for {
    res1 <- sendMessageFn(Message("message1"))
    res2 <- sendMessageFn(Message("message2"))
  } yield List(res1.sequenceNumber(), res2.sequenceNumber())
```
