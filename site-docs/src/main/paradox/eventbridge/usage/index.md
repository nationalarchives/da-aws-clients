# EventBridge Client

The client constructor takes an `EventBridgeAsyncClient` object. The default `apply` method passes one in with sensible defaults but you can pass your own in if necessary.

```scala
val customAsyncClient: EventBridgeAsyncClient = ???
val clientWithCustom = DAEventBridgeClient(customAsyncClient)

val clientWithDefault = DAEventBridgeClient()
```

The client exposes one method:

```scala
def publishEventToEventBridge[T, U](sourceId: String, detailType: U, detail: T)(implicit enc: Encoder[T]): F[PutEventsResponse]
```

The `U` type provided for detailType, should preferably be an enum or sealed trait as the `toString` method will be used on it
The message takes an object of type `T` which is deserialised to json and sent as the event detail.
You need an implicit circe encoder to deserialise the object.

@@@ index

* [Zio](zio.md)
* [Fs2](fs2.md)

@@@
