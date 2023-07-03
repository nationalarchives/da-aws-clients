# SNS Client

The client exposes one method
```scala
def publish[T](topicArn: String)(message: T)(implicit enc: Encoder[T]): F[PublishResponse]
```

This takes a case class and requires an implicit circe encoder to deserialise the case class to JSON.

@@@ index

* [Zio](zio.md)
* [Fs2](fs2.md)

@@@
