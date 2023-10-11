# S3 Client

The client constructor takes a `S3TransferManager` object. The default `apply` method passes one in with sensible defaults but you can pass your own in if necessary.

```scala
val customTransferManager: S3TransferManager = ???
val clientWithCustom = new DAS3Client(customTransferManager)

val clientWithDefault = DAS3Client()
```

The client exposes five methods:

```scala
def upload(bucket: String, key: String, contentLength: Long, publisher: Publisher[ByteBuffer]): F[CompletedUpload]

def download(bucket: String, key: String): F[Publisher[ByteBuffer]]

def copy(sourceBucket: String, sourceKey: String, destinationBucket: String, destinationKey: String ): F[CompletedCopy]

def headObject(bucket: String, key: String): F[HeadObjectResponse]

def deleteObjects(bucket: String, keys: List[String]): F[DeleteObjectsResponse]
```

The upload and download methods stream the data using the Java Reactive streams standard. 
It is possible to work directly with the `Publisher` objects, but it should be easier to wrap them in a streaming library. 
There are examples for both Zio and Fs2.

The upload method takes a content length because it's not currently possible to send a stream of arbitrary length to S3.
There is an [open GitHub issue](https://github.com/aws/aws-sdk-java-v2/issues/139) with [associated pull request](https://github.com/awslabs/aws-c-s3/pull/285)
which should fix this soon but until then, we need to pass this in.

@@@ index

* [Zio](zio.md)
* [Fs2](fs2.md)

@@@
