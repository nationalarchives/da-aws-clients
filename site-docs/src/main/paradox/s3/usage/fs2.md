# Use with Fs2

## Setup
You will need these dependencies:

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-s3-client_2.13" version=$version$
group2="co.fs2" artifact2="fs2-reactive-streams_2.13" version2="3.7.0"
}

## Examples
```scala
import cats.effect._
import fs2.Stream
import fs2.interop.reactivestreams._
import software.amazon.awssdk.transfer.s3.model.CompletedUpload
import uk.gov.nationalarchives.DAS3Client

val fs2Client = DAS3Client[IO, Stream[IO, Byte]]()

def upload(s: Stream[IO, Byte], fileSize: Long): IO[CompletedUpload] = {
  s.chunks.map(_.toByteBuffer).toUnicastPublisher.use { publisher =>
    fs2Client.upload("bucket", "key", fileSize, publisher)
  }
}

def download(bucket: String, key: String): IO[Stream[IO, Byte]] = {
  fs2Client.download(bucket, key)
    .map(_.toStreamBuffered[IO](1024 * 1024).map(_.get()))
}
```
