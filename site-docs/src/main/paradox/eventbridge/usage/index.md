# EventBridge Client

The client constructor takes an `EventBridgeAsyncClient` object. The default `apply` method passes one in with sensible defaults but you can pass your own in if necessary.

```scala
val customAsyncClient: EventBridgeAsyncClient = ???
val clientWithCustom = new DAEventBridgeClient(customAsyncClient)

val clientWithDefault = DAEventBridgeClient()
```

The client exposes one method:

```scala
def publishEventToEventBridge[T <: Product](sourceId: String, detailType: String, detail: T)(implicit enc: Encoder[T]): F[PutEventsResponse]
```

The message takes a case class of type `T` which is deserialised to json and sent as the event detail.
You need an implicit circe encoder to deserialise the case class.

There is also an implicit class in the companion object which adds a `sendToSlack` extension method to the log4cats `SelfAwareStructuredLogger[F]`

```
implicit class EventBridgeSlf4jLoggerExtensions[F[_] : Async](logger: SelfAwareStructuredLogger[F])(implicit val detailType: DetailType, client: DAEventBridgeClient[F]) {
    def sendToSlack(messageString: String): F[Unit]
}
```
This passes an event to EventBridge with a static source the implicit `detailType` from the class and a message string.
You will need an EventBridge rule to process these. There is [an example here](https://github.com/nationalarchives/dr2-terraform-environments/blob/main/common.tf#L214).

@@@ index

* [Zio](zio.md)
* [Fs2](fs2.md)

@@@
