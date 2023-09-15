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

@@@ index

* [Zio](zio.md)
* [Fs2](fs2.md)

@@@
