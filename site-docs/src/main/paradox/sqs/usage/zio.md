# Use with ZIO

## Setup
You will need these dependencies

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-sqs-client_2.13" version=$version$
group3="dev.zio" artifact3="zio-interop-cats_2.13" version3="23.0.05"
}

## Examples
```scala
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.DASQSClient
import zio.Task
import zio.interop.catz._
import io.circe.generic.auto._ // Used to provide Encoder[T] but you can provide your own

val sqsClient = DASQSClient[Task]()
val queueUrl = "https://queueurl"
case class Message(value: String)

val sendMessageFn: Message => Task[SendMessageResponse] = sqsClient.sendMessage(queueUrl)

val responses: Task[List[String]] = for {
  res1 <- sendMessageFn(Message("message1"))
  res2 <- sendMessageFn(Message("message2"))
} yield List(res1.messageId(), res2.messageId())
```
