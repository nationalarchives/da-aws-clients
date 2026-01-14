# SQS Client

The client exposes five methods
```scala
case class FifoQueueConfiguration(messageGroupId: String, messageDeduplicationId: String)

def sendMessage[T](queueUrl: String)(message: T, potentialFifoConfiguration: Option[FifoQueueConfiguration] = None, delaySeconds: Int = 0)(using enc: Encoder[T]): F[SendMessageResponse]

def receiveMessages[T](queueUrl: String, maxNumberOfMessages: Int = 10)(implicit dec: Decoder[T]): F[List[MessageResponse[T]]]

def deleteMessage(queueUrl: String, receiptHandle: String): F[DeleteMessageResponse]

def changeVisibilityTimeout(queueUrl: String)(receiptHandle: String, timeout: Duration): F[ChangeMessageVisibilityResponse]
```

The sendMessage method takes a case class and requires an implicit circe encoder to serialise the case class to JSON. 
There is an optional parameter to take a message group ID and deduplication id if we're sending to a FIFO queue. 

The receiveMessages method takes a type parameter and an implicit circe decoder which is used to decode the message json into type `T`

The deleteMessages method deletes a single message with the provided receipt handle.

The getQueueAttributes method returns requested attributes of the queue. There is an optional parameter to take a list of attribute names. If omitted, it retrieves `ALL` attributes of the queue.

The changeVisibilityTimeout will change the visibility timeout of a single message to the value provided.

@@@ index

* [Zio](zio.md)
* [Fs2](fs2.md)

@@@
