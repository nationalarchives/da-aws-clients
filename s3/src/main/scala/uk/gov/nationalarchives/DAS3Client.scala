package uk.gov.nationalarchives

import cats.effect.Async
import cats.implicits._
import org.reactivestreams.Publisher
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.async.{AsyncRequestBody, AsyncResponseTransformer, SdkPublisher}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model._
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model._

import java.nio.ByteBuffer
import java.util
import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters._

/** An S3 client. It is written generically so can be used for any effect which has an Async instance. Requires an
  * implicit instance of cats Async which is used to convert CompletableFuture to F
  */
trait DAS3Client[F[_]: Async]:
  /** Copies an S3 object to a destination bucket and key
    *
    * @param sourceBucket
    *   The bucket to copy from
    * @param sourceKey
    *   The key to copy from
    * @param destinationBucket
    *   The bucket to copy to
    * @param destinationKey
    *   The key to copy to
    * @return
    *   A CopyObjectResponse wrapped in the F effect
    */
  def copy(
      sourceBucket: String,
      sourceKey: String,
      destinationBucket: String,
      destinationKey: String
  ): F[CompletedCopy]

  /** Downloads a file from S3
    *
    * @param bucket
    *   The name of the bucket
    * @param key
    *   The object key
    * @return
    *   A reactive streams Publisher wrapped in the F effect
    */
  def download(bucket: String, key: String): F[Publisher[ByteBuffer]]

  /** Uploads a file to S3
    *
    * @param bucket
    *   The name of the bucket/
    * @param key
    *   The object key.
    * @param publisher
    *   A reactive streams publisher which streams the file to upload.
    * @return
    *   A CompletedUpload wrapped in the F effect.
    */
  def upload(bucket: String, key: String, publisher: Publisher[ByteBuffer]): F[CompletedUpload]

  /** Makes a head object request for an S3 object
    *
    * @param bucket
    *   The bucket where the object is stored
    * @param key
    *   The key of the object
    * @return
    *   The HeadObjectResponse from AWS wrapped in the F effect
    */
  def headObject(bucket: String, key: String): F[HeadObjectResponse]

  /** @param bucket
    *   The bucket to delete the objects from
    * @param keys
    *   The keys to delete from the bucket
    * @return
    *   The DeleteObjectsResponse from AWS wrapped in the F effect
    */
  def deleteObjects(bucket: String, keys: List[String]): F[DeleteObjectsResponse]

  /** @param bucket
    *   The bucket to check the prefixes for
    * @param keysPrefixedWith
    *   A string used to find the keys
    * @return
    *   A publisher of strings with the common prefixes, wrapped in the F effect
    */
  def listCommonPrefixes(
      bucket: String,
      keysPrefixedWith: String
  ): F[SdkPublisher[String]]

object DAS3Client:
  extension [F[_]: Async, T](completableFuture: CompletableFuture[T])
    private def liftF: F[T] = Async[F].fromCompletableFuture(Async[F].pure(completableFuture))

  private val asyncClient: S3AsyncClient = S3AsyncClient
    .crtBuilder()
    .region(Region.EU_WEST_2)
    .credentialsProvider(DefaultCredentialsProvider.create())
    .targetThroughputInGbps(20.0)
    .minimumPartSizeInBytes(10 * 1024 * 1024)
    .build()

  private def transferManager(asyncClient: S3AsyncClient = asyncClient): S3TransferManager = S3TransferManager
    .builder()
    .s3Client(asyncClient)
    .build()

  def apply[F[_]: Async](asyncClient: S3AsyncClient): DAS3Client[F] = {
    DAS3Client(transferManager(asyncClient), asyncClient)
  }

  def apply[F[_]: Async](
      transferManager: S3TransferManager = transferManager(),
      asyncClient: S3AsyncClient = asyncClient
  ): DAS3Client[F] =
    new DAS3Client[F] {

      def copy(
          sourceBucket: String,
          sourceKey: String,
          destinationBucket: String,
          destinationKey: String
      ): F[CompletedCopy] =
        val copyObjectRequest: CopyObjectRequest = CopyObjectRequest.builder
          .sourceBucket(sourceBucket)
          .sourceKey(sourceKey)
          .destinationBucket(destinationBucket)
          .destinationKey(destinationKey)
          .build()
        val copyRequest: CopyRequest = CopyRequest.builder.copyObjectRequest(copyObjectRequest).build
        transferManager.copy(copyRequest).completionFuture().liftF

      def download(bucket: String, key: String): F[Publisher[ByteBuffer]] =
        val getObjectRequest = GetObjectRequest.builder.key(key).bucket(bucket).build
        val downloadRequest = DownloadRequest.builder
          .getObjectRequest(getObjectRequest)
          .responseTransformer(AsyncResponseTransformer.toPublisher[GetObjectResponse])
          .build()
        transferManager
          .download(downloadRequest)
          .completionFuture()
          .liftF
          .map(_.result())

      def upload(bucket: String, key: String, publisher: Publisher[ByteBuffer]): F[CompletedUpload] =
        Async[F]
          .blocking {
            val putObjectRequest = PutObjectRequest.builder
              .bucket(bucket)
              .checksumAlgorithm(ChecksumAlgorithm.SHA256)
              .key(key)
              .build()

            val requestBody = AsyncRequestBody.fromPublisher(publisher)

            transferManager.upload(
              UploadRequest.builder().requestBody(requestBody).putObjectRequest(putObjectRequest).build()
            )
          }
          .flatMap(_.completionFuture().liftF)

      def headObject(bucket: String, key: String): F[HeadObjectResponse] =
        val headObjectRequest = HeadObjectRequest.builder
          .bucket(bucket)
          .key(key)
          .build
        asyncClient.headObject(headObjectRequest).liftF

      def deleteObjects(bucket: String, keys: List[String]): F[DeleteObjectsResponse] =
        val objects: util.List[ObjectIdentifier] = keys.map(ObjectIdentifier.builder.key(_).build).asJava
        val delete = Delete.builder.objects(objects).build
        val request = DeleteObjectsRequest.builder
          .bucket(bucket)
          .delete(delete)
          .build()
        asyncClient.deleteObjects(request).liftF

      def listCommonPrefixes(
          bucket: String,
          keysPrefixedWith: String
      ): F[SdkPublisher[String]] =
        val listObjectsV2Request = ListObjectsV2Request.builder
          .bucket(bucket)
          .delimiter("/")
          .prefix(keysPrefixedWith)
          .build

        val keysThatHaveSamePrefix = asyncClient
          .listObjectsV2Paginator(listObjectsV2Request)
          .commonPrefixes()
          .map(_.prefix())
        Async[F].pure(keysThatHaveSamePrefix)

    }
