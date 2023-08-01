# Use with ZIO

## Setup
You will need these dependencies

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-sfn-client_2.13" version=$version$
group3="dev.zio" artifact3="zio-interop-cats_2.13" version3="23.0.0.5"
}

## Examples
```scala
import uk.gov.nationalarchives.DASFNClient
import zio.Task
import zio.interop.catz._
import io.circe.generic.auto._ // Used to provide Encoder[T] but you can provide your own

    val sfnClient = DASFNClient[Task]()
    val arn = "arn:aws:states:eu-west-2:123456789:stateMachine:StateMachineName"
    case class Message(value: String)
    val executionResponse = client.startExecution(arn, Message("value"), Option("optionalName"))
```
