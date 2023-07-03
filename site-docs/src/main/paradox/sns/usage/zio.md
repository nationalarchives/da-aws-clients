# Use with ZIO

## Setup
You will need these dependencies

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-sns-client_2.13" version=$version$
group3="dev.zio" artifact3="zio-interop-cats_2.13" version3="23.0.0.5"
}

## Examples
```scala
import software.amazon.awssdk.services.sns.model.PublishResponse
import uk.gov.nationalarchives.DASNSClient
import zio.Task
import zio.interop.catz._
import io.circe.generic.auto._ // Used to provide Encoder[T] but you can provide your own

val snsClient = DASNSClient[Task]()
val topicArn = "topicArn"
case class Message(value: String)

val publishFn: Message => Task[PublishResponse] = snsClient.publish(topicArn)

val responses: Task[List[String]] = for {
  res1 <- publishFn(Message("message1"))
  res2 <- publishFn(Message("message2"))
} yield List(res1.messageId(), res2.messageId())
```
