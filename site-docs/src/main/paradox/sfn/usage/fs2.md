# Use with Cats Effect

## Setup

You will need this dependency:

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-sfn-client_2.13" version=$version$
}

## Examples
```scala
import cats.effect._
import io.circe.generic.auto._

  val client = DASFNClient[IO]()
  val arn = "arn:aws:states:eu-west-2:123456789:stateMachine:StateMachineName"
  case class Message(value: String)
  val executionResponse = client.startExecution(arn, Message("value"), Option("optionalName"))
```
