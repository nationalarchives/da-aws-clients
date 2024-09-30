# SQS Client

The client exposes three methods
```scala
case class FifoQueueConfiguration(messageGroupId: String, messageDeduplicationId: String)

def sendMessage[T](queueUrl: String)(message: T, potentialFifoConfiguration: Option[FifoQueueConfiguration] = None, delaySeconds: Int = 0)(using enc: Encoder[T]): F[SendMessageResponse]

def receiveMessages[T](queueUrl: String, maxNumberOfMessages: Int = 10)(implicit dec: Decoder[T]): F[List[MessageResponse[T]]]

def deleteMessage(queueUrl: String, receiptHandle: String): F[DeleteMessageResponse]
```

The sendMessage method takes a case class and requires an implicit circe encoder to serialise the case class to JSON. 
There is an optional parameter to take a message group ID and deduplication id if we're sending to a FIFO queue. 

The receiveMessages method takes a type parameter and an implicit circe decoder which is used to decode the message json into type `T`

The deleteMessages method deletes a single message with the provided receipt handle.

@@@ index

* [Zio](zio.md)
* [Fs2](fs2.md)

@@@
