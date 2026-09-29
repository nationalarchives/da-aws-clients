# SFN Client

The client exposes one method
```scala
def startExecution[T <: Product](stateMachineArn: String, input: T, name: Option[String] = None)(implicit enc: Encoder[T]): F[StartExecutionResponse]

def listStepFunctions(stepFunctionArn: String, status: Status): F[List[String]]

def sendTaskSuccess[T: Encoder](taskToken: String, potentialOutput: Option[T] = None): F[Unit]

def sendTaskFailure(taskToken: String, potentialError: Option[String] = None): F[Unit]

```

The start execution method takes a case class and requires an implicit circe encoder to deserialise the case class to JSON.
The method will start an execution of the state machine described by the ARN and pass the deserialised json as input.

The list step functions method will return the names of any step functions with the given arn and status.

Send task success returns Unit because the response doesn't contain any useful information. If the task token doesn't exist, an exception will be thrown. 
If potentialOutput is provided, this is converted to JSON using the `Encoder` and returned as the response. Otherwise, an empty JSON object is returned.

Send task failure returns Unit because the response doesn't contain any useful information. If the task token doesn't exist, an exception will be thrown.
If potentialError os provided, this error is returned to the Step Function otherwise null is returned.
@@@ index

* [Zio](zio.md)
* [Fs2](fs2.md)

@@@
