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
import io.circe.generic.auto._ // Used to provide Encoder[T] and Decoder[T] but you can provide your own

  case class FifoQueueConfiguration(messageGroupId: String, messageDeduplicationId: String)
  val sqsClient: DASQSClient[IO] = DASQSClient[IO]()
  val queueUrl = "https://queueurl"
  case class Message(value: String)
  val sendMessageFn: Message => IO[SendMessageResponse] = sqsClient.sendMessage(queueUrl)

  val responses: IO[List[String]] = for {
    res1 <- sendMessageFn(Message("message1"))
    res2 <- sendMessageFn(Message("message2"))
    res3 <- sendMessageFn(Message("message2"), Option(FifoQueueConfiguration("messageGroupId", "messageDeduplicationId")))
    res4 <- sendMessageFn(Message("message2"), None, 10)
  } yield List(res1.sequenceNumber(), res2.sequenceNumber(), res3.sequenceNumber())

  val receivedMessages: IO[List[MessageResponse[Message]]] = for {
    received <- client.receiveMessages[Message](queueUrl)
    receivedCustomMax <- client.receiveMessages[Message](queueUrl, 20)
  } yield received

val deletedMessages: IO[DeleteMessageResponse] = client.deleteMessage(queueUrl, "receiptHandle")
```
