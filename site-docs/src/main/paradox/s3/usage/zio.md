# Use with Zio

## Setup
You will need these dependencies:

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-s3-client_2.13" version=$version$
group2="dev.zio" artifact2="zio-interop-reactivestreams_2.13" version2="2.0.2"
group3="dev.zio" artifact3="zio-interop-cats_2.13" version3="23.0.0.5"
}

`zio-interop-cats` is needed to allow us to use the ZIO Task with the cats type classes

`zio-interop-reactivestreams` converts between a `ZStream` and a `Publisher`

## Examples
```scala
import zio.stream.Stream
import zio._
import software.amazon.awssdk.transfer.s3.model._
import java.nio.ByteBuffer
import zio.interop.reactivestreams._
import zio.interop.catz._
import uk.gov.nationalarchives.DAS3Client

val s3Client = DAS3Client[Task, zio.stream.Stream[Throwable, Byte]]()
def upload(stream: Stream[Throwable, Byte], contentLength: Long): Task[CompletedUpload] = for {
  publisher <- stream.chunks.map(c => ByteBuffer.wrap(c.toArray[Byte])).toPublisher //Convert Stream[Throwable,Byte] to Publisher[ByteBuffer]
  res <- s3Client.upload("bucket", "key", contentLength, publisher)
} yield res

def download(bucket: String, key: String) = {
  s3Client.download(bucket, key)
    .map(_.toZIOStream().map(_.get())) //Publisher[ByteBuffer] to Stream[ThrowableByte]
}

def copy(sourceBucket: String, sourceKey: String, destinationBucket: String, destinationKey: String): Task[CompletedCopy] = {
  s3Client.copy(sourceBucket, sourceKey, destinationBucket, destinationKey)
}

def headObject(bucket: String, key: String): Task[HeadObjectResponse] = {
  s3Client.headObject(bucket, key)
}

```

