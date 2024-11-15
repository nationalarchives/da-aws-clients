# SSM Client

The client exposes one method
```scala
def getParameter[T](parameterName: String, withDecryption: Boolean = false)(using Decoder[T]): F[T]
```

This method returns the value of the parameter with name `parameterName` as object `T`. `withDecryption` must be true if the parameter is encrypted.

@@@ index

* [Zio](zio.md)
* [Fs2](fs2.md)

@@@
