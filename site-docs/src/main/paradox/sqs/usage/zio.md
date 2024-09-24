# Use with ZIO

## Setup
You will need these dependencies

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-sqs-client_2.13" version=$version$
group3="dev.zio" artifact3="zio-interop-cats_2.13" version3="23.0.0.5"
}

## Examples
```scala
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.DASQSClient
import zio.Task
import zio.interop.catz._
import io.circe.generic.auto._ // Used to provide Encoder[T] and and Decoder[T] but you can provide your own

val sqsClient: DASQSClient[Task] = DASQSClient[Task]()
val queueUrl = "https://queueurl"
case class Message(value: String)
val sendMessageFn: Message => Task[SendMessageResponse] = sqsClient.sendMessage(queueUrl)

val responses: Task[List[String]] = for {
  res1 <- sendMessageFn(Message("message1"))
  res2 <- sendMessageFn(Message("message2"))
  res3 <- sendMessageFn(Message("message2"), Option(FifoQueueConfiguration("messageGroupId", "messageDeduplicationId")))
} yield List(res1.sequenceNumber(), res2.sequenceNumber(), res3.sequenceNumber())

val receivedMessages: Task[List[MessageResponse[Message]]] = for {
  received <- client.receiveMessages[Message](queueUrl)
  receivedCustomMax <- client.receiveMessages[Message](queueUrl, 20)
} yield received

val deletedMessages: Task[DeleteMessageResponse] = client.deleteMessage(queueUrl, "receiptHandle")

```
