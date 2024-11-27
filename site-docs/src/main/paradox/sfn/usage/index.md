# SFN Client

The client exposes one method
```scala
def startExecution[T <: Product](stateMachineArn: String, input: T, name: Option[String] = None)(implicit enc: Encoder[T]): F[StartExecutionResponse]

def listStepFunctions(stepFunctionArn: String, status: Status): F[List[String]]

def sendTaskSuccess(taskToken: String): F[Unit]

```

The start execution method takes a case class and requires an implicit circe encoder to deserialise the case class to JSON.
The method will start an execution of the state machine described by the ARN and pass the deserialised json as input.

The list step functions method will return the names of any step functions with the given arn and status.

Send task success returns Unit because the response doesn't contain any useful information. If the task token doesn't exist, an exception will be thrown.

@@@ index

* [Zio](zio.md)
* [Fs2](fs2.md)

@@@
