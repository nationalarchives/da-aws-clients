# S3 Client

## Creating new instances 

```scala
val customTransferManager: S3TransferManager = ???
val customAsyncClient: S3AsyncClient = ???

val clientWithTransferManagerAndClient = DAS3Client[IO](customTransferManager, customAsyncClient)
val clientWithAsyncClient = DAS3Client[IO](customAsyncClient)
val clientWithAssumeRole = DAS3Client[IO]("role-arn-to-assume", "role-session-name")
val clientWithDefault = DAS3Client()
```

The client exposes seven methods:

```scala
def upload(bucket: String, key: String, contentLength: Long, publisher: Publisher[ByteBuffer]): F[CompletedUpload]

def download(bucket: String, key: String): F[Publisher[ByteBuffer]]

def copy(sourceBucket: String, sourceKey: String, destinationBucket: String, destinationKey: String ): F[CompletedCopy]

def headObject(bucket: String, key: String): F[HeadObjectResponse]

def deleteObjects(bucket: String, keys: List[String]): F[DeleteObjectsResponse]

def listCommonPrefixes(bucket: String, keysPrefixedWith: String): F[SdkPublisher[String]]

def listObjects(bucket: String, potentialPrefix: Option[String] = None): F[ListObjectsV2Response]

def updateObjectTags(bucket: String, key: String, newTags: Map[String, String], potentialVersionId: Option[String] = None): F[PutObjectTaggingResponse]

```

The upload and download methods stream the data using the Java Reactive streams standard. 
It is possible to work directly with the `Publisher` objects, but it should be easier to wrap them in a streaming library. 
There are examples for both Zio and Fs2.

The upload method takes a content length because it's not currently possible to send a stream of arbitrary length to S3.
There is an [open GitHub issue](https://github.com/aws/aws-sdk-java-v2/issues/139) with [associated pull request](https://github.com/awslabs/aws-c-s3/pull/285)
which should fix this soon but until then, we need to pass this in.


The listCommonPrefixes method takes the bucket with the keys, the prefix that these keys should have and the
string that these keys should be cut off at e.g. if the keys in the bucket are:
`dir1/subdir1/fileName.txt`, `dir1/subdir1/fileName2.txt`, `dir1/subdir2/fileName.txt`, `dir1/subdir2/fileName2.txt`,
`dir1/subdir3/fileName.txt`, `dir1/subdir3/fileName2.txt`,

if you call the `listCommonPrefixes` method with `keysPrefixedWith` set to `"dir1/"`

it will find all keys that start with `dir1/`, strip off everything after the first `/` (after the prefix) and
deduplicate the values; therefore the results would be `["dir1/subdir1/", "dir1/subdir2/", "dir1/subdir3/"]`.

If there are more than 1000 common prefixes, the method will deal with the pagination for you.

@@@ index

* [Zio](zio.md)
* [Fs2](fs2.md)

@@@
