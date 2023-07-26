package uk.gov.nationalarchives

import cats.effect.Async
import cats.implicits._
import org.reactivestreams.Publisher
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.async.{AsyncRequestBody, AsyncResponseTransformer}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{ChecksumAlgorithm, CopyObjectRequest, CopyObjectResponse, GetObjectRequest, GetObjectResponse, PutObjectRequest}
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model.{CompletedUpload, DownloadRequest, Upload, UploadRequest}
import uk.gov.nationalarchives.DAS3Client.asyncClient

import java.nio.ByteBuffer

/** An S3 client. It is written generically so can be used for any effect which has an Async instance. Requires an
  * implicit instance of cats Async which is used to convert CompletableFuture to F
  *
  * @param transferManager
  *   S3 Transfer Manager
  * @tparam F
  *   Type of the effect
  */
class DAS3Client[F[_]: Async](transferManager: S3TransferManager, asyncClient: S3AsyncClient) {

  /**
   * Copies an S3 object to a destination bucket and key
   * @param sourceBucket The bucket to copy from
   * @param sourceKey The key to copy from
   * @param destinationBucket The bucket to copy to
   * @param destinationKey The key to copy to
   * @return A CopyObjectResponse wrapped in the F effect
   */
  def copy(sourceBucket: String, sourceKey: String, destinationBucket: String, destinationKey: String): F[CopyObjectResponse] = {
    val copyObjectRequest = CopyObjectRequest.builder
      .sourceBucket(sourceBucket)
      .sourceKey(sourceKey)
      .destinationBucket(destinationBucket)
      .destinationKey(destinationKey)
      .build()
    Async[F]
      .fromCompletableFuture {
        Async[F].pure(asyncClient.copyObject(copyObjectRequest))
      }
  }

  /** Downloads a file from S3
    * @param bucket
    *   The name of the bucket
    * @param key
    *   The object key
    * @return
    *   A reactive streams Publisher wrapped in the F effect
    */
  def download(bucket: String, key: String): F[Publisher[ByteBuffer]] = {
    val getObjectRequest = GetObjectRequest.builder.key(key).bucket(bucket).build
    val downloadRequest = DownloadRequest.builder
      .getObjectRequest(getObjectRequest)
      .responseTransformer(AsyncResponseTransformer.toPublisher[GetObjectResponse])
      .build()
    Async[F]
      .fromCompletableFuture {
        Async[F].pure(transferManager.download(downloadRequest).completionFuture())
      }
      .map(_.result())
  }

  /** Uploads a file to S3
    * @param bucket
    *   The name of the bucket/
    * @param key
    *   The object key.
    * @param contentLength
    *   The content length of the whole file.
    * @param publisher
    *   A reactive streams publisher which streams the file to upload.
    * @return
    *   A CompletedUpload wrapped in the F effect.
    */
  def upload(bucket: String, key: String, contentLength: Long, publisher: Publisher[ByteBuffer]): F[CompletedUpload] = {
    val putObjectRequest = PutObjectRequest.builder
      .contentLength(contentLength)
      .bucket(bucket)
      .checksumAlgorithm(ChecksumAlgorithm.SHA256)
      .key(key)
      .build()

    val requestBody = AsyncRequestBody.fromPublisher(publisher)

    val response: Upload = transferManager.upload(
      UploadRequest.builder().requestBody(requestBody).putObjectRequest(putObjectRequest).build()
    )
    Async[F].fromCompletableFuture(Async[F].pure(response.completionFuture()))
  }
}
object DAS3Client {
  private val asyncClient: S3AsyncClient = S3AsyncClient
    .crtBuilder()
    .region(Region.EU_WEST_2)
    .credentialsProvider(DefaultCredentialsProvider.create())
    .targetThroughputInGbps(20.0)
    .minimumPartSizeInBytes(10 * 1024 * 1024)
    .build()

  private val transferManager: S3TransferManager = S3TransferManager
    .builder()
    .s3Client(asyncClient)
    .build()

  def apply[F[_]: Async](): DAS3Client[F] = new DAS3Client[F](transferManager, asyncClient)
  def apply[F[_]: Async](asyncClient: S3AsyncClient): DAS3Client[F] = {
    val transferManager: S3TransferManager = S3TransferManager
      .builder()
      .s3Client(asyncClient)
      .build()
    new DAS3Client[F](transferManager, asyncClient)
  }
}
