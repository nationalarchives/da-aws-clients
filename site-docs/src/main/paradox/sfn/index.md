# SFN Client

The client exposes one method
```scala
def startExecution[T <: Product](stateMachineArn: String, input: T, name: Option[String] = None)(implicit enc: Encoder[T]): F[StartExecutionResponse]
```

This takes a case class and requires an implicit circe encoder to deserialise the case class to JSON.
The method will start an execution of the state machine described by the ARN and pass the deserialised json as input.

@@@ index

* [Zio](zio.md)
* [Fs2](fs2.md)

@@@
