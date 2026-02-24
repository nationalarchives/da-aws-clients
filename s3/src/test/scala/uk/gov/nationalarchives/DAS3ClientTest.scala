package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.*
import org.scalatestplus.mockito.MockitoSugar
import reactor.test.StepVerifier
import software.amazon.awssdk.core.SdkResponse
import software.amazon.awssdk.core.async.{ResponsePublisher, SdkPublisher}
import software.amazon.awssdk.core.internal.async.ByteBuffersAsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Publisher
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.internal.model.{DefaultCopy, DefaultDownload, DefaultUpload}
import software.amazon.awssdk.transfer.s3.internal.progress.{DefaultTransferProgress, DefaultTransferProgressSnapshot}
import software.amazon.awssdk.transfer.s3.model.*

import java.nio.ByteBuffer
import java.util.Optional
import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters.*

class DAS3ClientTest extends AnyFlatSpec with MockitoSugar {
  type S3Download = DownloadRequest[ResponsePublisher[GetObjectResponse]]

  "download" should "return a publisher with the correct bytes" in {
    val transferManagerMock = mock[S3TransferManager]
    val testBytes = "test".getBytes()

    val defaultDownload = createDownloadResponse(testBytes)
    when(transferManagerMock.download(any[S3Download])).thenReturn(defaultDownload)
    val client = DAS3Client[IO](transferManagerMock, mock[S3AsyncClient])
    val publisher = client.download("bucket", "key").unsafeRunSync()

    StepVerifier
      .create(publisher)
      .expectNext(ByteBuffer.wrap(testBytes))
      .verifyComplete()
  }

  "download" should "call the transfer manager methods with the correct arguments" in {
    val downloadCaptor: ArgumentCaptor[S3Download] = ArgumentCaptor.forClass(classOf[S3Download])
    val transferManagerMock = mock[S3TransferManager]
    val defaultDownload = createDownloadResponse("test".getBytes())
    when(transferManagerMock.download(downloadCaptor.capture())).thenReturn(defaultDownload)

    val client = DAS3Client[IO](transferManagerMock, mock[S3AsyncClient])
    client.download("bucket", "key").unsafeRunSync()

    val responseObjectRequest = downloadCaptor.getValue.getObjectRequest
    responseObjectRequest.key() should equal("key")
    responseObjectRequest.bucket() should equal("bucket")
  }

  "download" should "return an error if the transfer manager returns an error" in {
    val transferManagerMock = mock[S3TransferManager]
    when(transferManagerMock.download(any[S3Download])).thenThrow(new RuntimeException("Error downloading"))

    val client = DAS3Client[IO](transferManagerMock, mock[S3AsyncClient])
    val ex = intercept[Exception] {
      client.download("bucket", "key").unsafeRunSync()
    }
    ex.getMessage should equal("Error downloading")
  }

  "upload" should "return the correct put object response" in {
    val transferManagerMock = mock[S3TransferManager]
    val uploadResponse = createUploadResponse()
    when(transferManagerMock.upload(any[UploadRequest])).thenReturn(uploadResponse)

    val client = DAS3Client[IO](transferManagerMock, mock[S3AsyncClient])
    val publisher = ByteBuffersAsyncRequestBody.from("application/octet-stream", "test".getBytes)
    val response = client.upload("bucket", "key", publisher).unsafeRunSync()
    response.response().eTag() should equal("testEtag")
  }

  "upload" should "call transfer manager upload with the correct arguments" in {
    val transferManagerMock = mock[S3TransferManager]
    val content = "test".getBytes
    val uploadCaptor: ArgumentCaptor[UploadRequest] = ArgumentCaptor.forClass(classOf[UploadRequest])
    val uploadResponse = createUploadResponse()
    when(transferManagerMock.upload(uploadCaptor.capture())).thenReturn(uploadResponse)

    val client = DAS3Client[IO](transferManagerMock, mock[S3AsyncClient])
    val publisher = ByteBuffersAsyncRequestBody.from("application/octet-stream", content)
    client.upload("bucket", "key", publisher).unsafeRunSync()

    val requestBody = uploadCaptor.getValue.requestBody()
    requestBody.contentLength() should be(Optional.empty()) // Content length is not sent when using a Publisher
    requestBody.contentType() should be("application/octet-stream")
  }

  "upload" should "return an error if the transfer manager returns an error" in {
    val transferManagerMock = mock[S3TransferManager]
    when(transferManagerMock.upload(any[UploadRequest])).thenThrow(new RuntimeException("Error uploading"))

    val publisher = ByteBuffersAsyncRequestBody.from("application/octet-stream", "test".getBytes)
    val client = DAS3Client[IO](transferManagerMock, mock[S3AsyncClient])
    val ex = intercept[Exception] {
      client.upload("bucket", "key", publisher).unsafeRunSync()
    }
    ex.getMessage should equal("Error uploading")
  }

  "copy" should "return the correct 'copy completed' response" in {
    val transferManagerMock = mock[S3TransferManager]
    val copyCompletedResponse = createCopyCompletedResponse()
    when(transferManagerMock.copy(any[CopyRequest])).thenReturn(copyCompletedResponse)

    val client = DAS3Client[IO](transferManagerMock, mock[S3AsyncClient])
    val response = client.copy("sourceBucket", "sourceKey", "destinationBucket", "destinationKey").unsafeRunSync()
    response.response().versionId() should equal("version")
  }

  "copy" should "call transfer manager copy with the correct arguments" in {
    val transferManagerMock = mock[S3TransferManager]
    val copyCaptor: ArgumentCaptor[CopyRequest] = ArgumentCaptor.forClass(classOf[CopyRequest])
    val copyCompletedResponse = createCopyCompletedResponse()
    when(transferManagerMock.copy(copyCaptor.capture())).thenReturn(copyCompletedResponse)

    val client = DAS3Client[IO](transferManagerMock, mock[S3AsyncClient])
    client.copy("sourceBucket", "sourceKey", "destinationBucket", "destinationKey").unsafeRunSync()

    val requestBody = copyCaptor.getValue.copyObjectRequest()
    requestBody.sourceBucket() should equal("sourceBucket")
    requestBody.sourceKey() should equal("sourceKey")
    requestBody.destinationBucket() should equal("destinationBucket")
    requestBody.destinationKey() should equal("destinationKey")
  }

  "copy" should "return an error if the transfer manager returns an error" in {
    val transferManagerMock = mock[S3TransferManager]
    when(transferManagerMock.copy(any[CopyRequest])).thenThrow(new RuntimeException("Error copying"))

    val client = DAS3Client[IO](transferManagerMock, mock[S3AsyncClient])
    val ex = intercept[Exception] {
      client.copy("sourceBucket", "sourceKey", "destinationBucket", "destinationKey").unsafeRunSync()
    }
    ex.getMessage should equal("Error copying")
  }

  "headObject" should "return the correct 'head object' response" in {
    val asyncClientMock = mock[S3AsyncClient]
    val headObjectResponse = createHeadObjectResponse()
    when(asyncClientMock.headObject(any[HeadObjectRequest])).thenReturn(headObjectResponse)

    val client = DAS3Client[IO](asyncClientMock)
    val response = client.headObject("bucket", "key").unsafeRunSync()
    response.contentLength() should equal(10)
  }

  "headObject" should "call the async client headObject with the correct arguments" in {
    val asyncClientMock = mock[S3AsyncClient]
    val headObjectCaptor: ArgumentCaptor[HeadObjectRequest] = ArgumentCaptor.forClass(classOf[HeadObjectRequest])
    val headObjectResponse = createHeadObjectResponse()
    when(asyncClientMock.headObject(headObjectCaptor.capture())).thenReturn(headObjectResponse)

    val client = DAS3Client[IO](asyncClientMock)
    client.headObject("bucket", "key").unsafeRunSync()

    val requestBody = headObjectCaptor.getValue
    requestBody.bucket() should equal("bucket")
    requestBody.key() should equal("key")
  }

  "headObject" should "return an error if the async client returns an error" in {
    val asyncClientMock = mock[S3AsyncClient]
    when(asyncClientMock.headObject(any[HeadObjectRequest]))
      .thenThrow(new RuntimeException("Error calling head object"))

    val client = DAS3Client[IO](asyncClientMock)
    val ex = intercept[Exception] {
      client.headObject("bucket", "key").unsafeRunSync()
    }
    ex.getMessage should equal("Error calling head object")
  }

  "delete" should "return the correct 'delete objects' response" in {
    val asyncClientMock = mock[S3AsyncClient]
    val deleteObjectsResponse = createDeleteObjectsResponse()
    when(asyncClientMock.deleteObjects(any[DeleteObjectsRequest])).thenReturn(deleteObjectsResponse)

    val client = DAS3Client[IO](asyncClientMock)
    val response = client.deleteObjects("bucket", List("test")).unsafeRunSync()
    response.deleted.get(0).key should equal("test")
  }

  "delete" should "call the async client deleteObjects with the correct arguments" in {
    val asyncClientMock = mock[S3AsyncClient]
    val deleteObjectsCaptor: ArgumentCaptor[DeleteObjectsRequest] =
      ArgumentCaptor.forClass(classOf[DeleteObjectsRequest])
    val deleteObjectsResponse = createDeleteObjectsResponse()
    when(asyncClientMock.deleteObjects(deleteObjectsCaptor.capture())).thenReturn(deleteObjectsResponse)

    val client = DAS3Client[IO](asyncClientMock)
    client.deleteObjects("bucket", List("test")).unsafeRunSync()

    val requestBody = deleteObjectsCaptor.getValue
    requestBody.bucket() should equal("bucket")
    requestBody.delete().objects().get(0).key() should equal("test")
  }

  "delete" should "return an error if the async client returns an error" in {
    val asyncClientMock = mock[S3AsyncClient]
    when(asyncClientMock.deleteObjects(any[DeleteObjectsRequest]))
      .thenThrow(new RuntimeException("Error calling delete objects"))

    val client = DAS3Client[IO](asyncClientMock)
    val ex = intercept[Exception] {
      client.deleteObjects("bucket", List("key")).unsafeRunSync()
    }
    ex.getMessage should equal("Error calling delete objects")
  }

  "listCommonPrefixes" should "return a publisher with the expected common prefixes" in {
    val asyncClientMock = mock[S3AsyncClient]

    val listObjectsV2Request = ListObjectsV2Request.builder
      .bucket("bucket")
      .delimiter("/")
      .prefix("prefix")
      .build

    val response =
      ListObjectsV2Response.builder.commonPrefixes(CommonPrefix.builder.prefix("commonPrefix").build).build()
    val listObjectsV2Publisher = new ListObjectsV2Publisher(asyncClientMock, listObjectsV2Request)

    when(asyncClientMock.listObjectsV2(any[ListObjectsV2Request]))
      .thenReturn(CompletableFuture.completedFuture(response))
    when(asyncClientMock.listObjectsV2Paginator(any[ListObjectsV2Request])).thenReturn(listObjectsV2Publisher)
    val client = DAS3Client[IO](asyncClientMock)
    val publisher: SdkPublisher[String] = client.listCommonPrefixes("bucket", "keyPrefix/").unsafeRunSync()

    StepVerifier
      .create(publisher)
      .expectNext("commonPrefix")
      .verifyComplete()
  }

  "listCommonPrefixes" should "make a request to 'listCommonPrefixes' with the correct arguments" in {
    val listObjectsV2PaginatorCaptor: ArgumentCaptor[ListObjectsV2Request] =
      ArgumentCaptor.forClass(classOf[ListObjectsV2Request])
    val asyncClientMock = mock[S3AsyncClient]
    val listObjectsV2Request = ListObjectsV2Request.builder
      .bucket("bucket")
      .delimiter("/")
      .prefix("prefix")
      .build

    val listObjectsV2Publisher = new ListObjectsV2Publisher(asyncClientMock, listObjectsV2Request)
    when(asyncClientMock.listObjectsV2Paginator(listObjectsV2PaginatorCaptor.capture()))
      .thenReturn(listObjectsV2Publisher)
    val client = DAS3Client[IO](asyncClientMock)
    client.listCommonPrefixes("bucket", "keyPrefix/").unsafeRunSync()

    val listObjectsRequest = listObjectsV2PaginatorCaptor.getValue
    listObjectsRequest.delimiter() should equal("/")
    listObjectsRequest.prefix() should equal("keyPrefix/")
    listObjectsRequest.bucket() should equal("bucket")
  }

  "listCommonPrefixes" should "return an error if the call to 'listObjectsV2Paginator' returns an error" in {
    val asyncClientMock = mock[S3AsyncClient]

    when(asyncClientMock.listObjectsV2Paginator(any[ListObjectsV2Request]))
      .thenThrow(new RuntimeException("Bucket does not exist"))
    val client = DAS3Client[IO](asyncClientMock)

    val ex = intercept[Exception] {
      client.listCommonPrefixes("bucket", "keyPrefix/").unsafeRunSync()
    }
    ex.getMessage should equal("Bucket does not exist")
  }

  "listObjects" should "request for all objects in a bucket if a prefix is not passed in" in {
    val asyncClientMock = mock[S3AsyncClient]

    val listObjectsRequestCaptor: ArgumentCaptor[ListObjectsV2Request] =
      ArgumentCaptor.forClass(classOf[ListObjectsV2Request])

    val mockResponseObject = S3Object.builder.key("test").build
    val mockResponse = ListObjectsV2Response.builder.contents(List(mockResponseObject).asJava).build
    when(asyncClientMock.listObjectsV2(listObjectsRequestCaptor.capture()))
      .thenReturn(CompletableFuture.completedFuture(mockResponse))

    val client = DAS3Client[IO](asyncClientMock)

    val response = client.listObjects("bucket").unsafeRunSync()

    response.contents.get(0).key() should equal("test")

    val capturedRequest = listObjectsRequestCaptor.getValue
    capturedRequest.bucket() should equal("bucket")
    Option(capturedRequest.prefix()) should equal(None)
  }

  "listObjects" should "request for all objects in a bucket with the specified prefix" in {
    val asyncClientMock = mock[S3AsyncClient]

    val listObjectsRequestCaptor: ArgumentCaptor[ListObjectsV2Request] =
      ArgumentCaptor.forClass(classOf[ListObjectsV2Request])

    val mockResponseObject = S3Object.builder.key("test").build
    val mockResponse = ListObjectsV2Response.builder.contents(List(mockResponseObject).asJava).build
    when(asyncClientMock.listObjectsV2(listObjectsRequestCaptor.capture()))
      .thenReturn(CompletableFuture.completedFuture(mockResponse))

    val client = DAS3Client[IO](asyncClientMock)

    val response = client.listObjects("bucket", Option("prefix")).unsafeRunSync()

    response.contents.get(0).key() should equal("test")

    val capturedRequest = listObjectsRequestCaptor.getValue
    capturedRequest.bucket() should equal("bucket")
    capturedRequest.prefix() should equal("prefix")
  }

  "listObjects" should "return an error if there is an error from S3" in {
    val asyncClientMock = mock[S3AsyncClient]

    val listObjectsRequestCaptor: ArgumentCaptor[ListObjectsV2Request] =
      ArgumentCaptor.forClass(classOf[ListObjectsV2Request])

    when(asyncClientMock.listObjectsV2(listObjectsRequestCaptor.capture()))
      .thenThrow(new RuntimeException("Error from S3"))

    val client = DAS3Client[IO](asyncClientMock)

    val error = intercept[Exception] {
      client.listObjects("bucket").unsafeRunSync()
    }

    error.getMessage should equal("Error from S3")
  }

  "updateObjectTags" should "update tags on an object with the supplied tag" in {
    val asyncClientMock = mock[S3AsyncClient]
    val client = DAS3Client[IO](asyncClientMock)

    val mockPutResponseObject: PutObjectTaggingResponse =
      setupMockTaggingInteractions(asyncClientMock, java.util.Collections.emptyList())

    val response = client.updateObjectTags("some_bucket", "some_obj", Map("soft_delete" -> "true")).unsafeRunSync()
    response shouldBe mockPutResponseObject

    val captor = ArgumentCaptor.forClass(classOf[PutObjectTaggingRequest])
    verify(asyncClientMock).putObjectTagging(captor.capture())

    val sentTags: Map[String, String] = captor.getValue.tagging().tagSet().asScala.map(t => t.key() -> t.value()).toMap

    sentTags shouldBe Map("soft_delete" -> "true")
  }

  "updateObjectTags" should "update tags on an object with the existing as well as newly supplied tag" in {
    val asyncClientMock = mock[S3AsyncClient]
    val client = DAS3Client[IO](asyncClientMock)

    val existingTags = List(Tag.builder().key("existing-key").value("existing-value").build()).asJava
    val mockPutResponseObject: PutObjectTaggingResponse = setupMockTaggingInteractions(asyncClientMock, existingTags)

    val response = client.updateObjectTags("some_bucket", "some_obj", Map("soft_delete" -> "true")).unsafeRunSync()
    response shouldBe mockPutResponseObject

    val captor = ArgumentCaptor.forClass(classOf[PutObjectTaggingRequest])
    verify(asyncClientMock).putObjectTagging(captor.capture())

    val sentTags: Map[String, String] = captor.getValue.tagging().tagSet().asScala.map(t => t.key() -> t.value()).toMap

    sentTags shouldBe Map("existing-key" -> "existing-value", "soft_delete" -> "true")
  }

  "updateObjectTags" should "report an error if the number of tags exceed 10" in {
    val asyncClientMock = mock[S3AsyncClient]
    val client = DAS3Client[IO](asyncClientMock)

    val _ = setupMockTaggingInteractions(asyncClientMock, java.util.Collections.emptyList())

    val moreThanTenTags = Map(
      "one_key" -> "one_val",
      "two_key" -> "two_val",
      "three_key" -> "three_val",
      "four_key" -> "four_val",
      "five_key" -> "five_val",
      "six_key" -> "six_val",
      "seven_key" -> "seven_val",
      "eight_key" -> "eight_val",
      "nine_key" -> "nine_val",
      "ten_key" -> "ten_val",
      "eleven_key" -> "eleven_val"
    )

    val error = intercept[Exception] {
      client.updateObjectTags("some_bucket", "some_obj", moreThanTenTags).unsafeRunSync()
    }

    error.getMessage shouldBe "S3 objects cannot have nore than 10 tags"
  }

  "updateObjectTags" should "report an error if a key exceeds 128 character" in {
    val asyncClientMock = mock[S3AsyncClient]
    val client = DAS3Client[IO](asyncClientMock)

    val _ = setupMockTaggingInteractions(asyncClientMock, java.util.Collections.emptyList())

    val tagWithKeyNameTooLong = Map(
      "this_is_an_important_s3_tag_key_that_somehow_kept_typing_and_typing_until_it_broke_the_128_character_limit_allowance_of_s3_object" -> "one_val"
    )

    val error = intercept[Exception] {
      client.updateObjectTags("some_bucket", "some_obj", tagWithKeyNameTooLong).unsafeRunSync()
    }

    error.getMessage shouldBe "One or more tag keys exceed the limit of 128 characters"
  }

  "updateObjectTags" should "report an error if a value exceeds 256 character" in {
    val asyncClientMock = mock[S3AsyncClient]
    val client = DAS3Client[IO](asyncClientMock)

    val _ = setupMockTaggingInteractions(asyncClientMock, java.util.Collections.emptyList())

    val tagWithKeyNameTooLong = Map(
      "won_ky" -> "this_is_an_important_s3_tag_value_that_somehow_kept_typing_and_typing_until_it_broke_the_256_character_limit_allowance_of_s3_object_by_writing_a_few_random_words_in_a_sentence_until_all_of_these_words_ended_up_into_a_bunch_of_words_that_no_person_understood"
    )

    val error = intercept[Exception] {
      client.updateObjectTags("some_bucket", "some_obj", tagWithKeyNameTooLong).unsafeRunSync()
    }

    error.getMessage shouldBe "One or more tag values exceed the limit of 256 characters"
  }

  "updateObjectTags" should "replace the existing tag if a new value is supplied for existing key" in {
    val asyncClientMock = mock[S3AsyncClient]
    val client = DAS3Client[IO](asyncClientMock)

    val existingTags = List(Tag.builder().key("existing-key").value("existing-value").build()).asJava
    val mockPutResponseObject: PutObjectTaggingResponse = setupMockTaggingInteractions(asyncClientMock, existingTags)

    val response =
      client.updateObjectTags("some_bucket", "some_obj", Map("existing-key" -> "new-value")).unsafeRunSync()
    response shouldBe mockPutResponseObject

    val captor = ArgumentCaptor.forClass(classOf[PutObjectTaggingRequest])
    verify(asyncClientMock).putObjectTagging(captor.capture())

    val sentTags: Map[String, String] = captor.getValue.tagging().tagSet().asScala.map(t => t.key() -> t.value()).toMap

    sentTags shouldBe Map("existing-key" -> "new-value")
  }

  private def createHeadObjectResponse(): CompletableFuture[HeadObjectResponse] = {
    val headObjectResponse = HeadObjectResponse.builder().contentLength(10).build
    CompletableFuture.completedFuture(headObjectResponse)
  }

  private def createDeleteObjectsResponse(): CompletableFuture[DeleteObjectsResponse] = {
    val deletedObject = DeletedObject.builder.key("test").build
    val deleteObjectsResponse = DeleteObjectsResponse.builder.deleted(List(deletedObject).asJava).build
    CompletableFuture.completedFuture(deleteObjectsResponse)
  }

  private def createCopyCompletedResponse(): Copy = {
    val copyObjectResponse = CopyObjectResponse.builder.versionId("version").build
    val completedCopy = CompletedCopy.builder.response(copyObjectResponse).build
    new DefaultCopy(CompletableFuture.completedFuture(completedCopy), getTransferProgress(copyObjectResponse, 1))
  }

  private def createUploadResponse(): DefaultUpload = {
    val putObjectResponse = PutObjectResponse.builder().eTag("testEtag").build()
    val completedUpload = CompletedUpload.builder().response(putObjectResponse).build()
    new DefaultUpload(CompletableFuture.completedFuture(completedUpload), getTransferProgress(putObjectResponse, 1))
  }

  private def createDownloadResponse(testBytes: Array[Byte]): DefaultDownload[ResponsePublisher[GetObjectResponse]] = {
    val length = testBytes.length
    val sdkPublisher = ByteBuffersAsyncRequestBody.from("application/octet-stream", testBytes)
    val getObjectResponse = GetObjectResponse.builder().build()
    val responsePublisher = new ResponsePublisher[GetObjectResponse](getObjectResponse, sdkPublisher)
    val completedDownload = CompletedDownload.builder().result(responsePublisher).build()
    val fut = CompletableFuture.completedFuture(completedDownload)
    val transferProgress = getTransferProgress(getObjectResponse, length)
    new DefaultDownload[ResponsePublisher[GetObjectResponse]](fut, transferProgress)
  }

  private def getTransferProgress(sdkResponse: SdkResponse, transferredBytes: Long) = {
    val progressSnapshot = DefaultTransferProgressSnapshot
      .builder()
      .transferredBytes(transferredBytes)
      .totalBytes(transferredBytes)
      .sdkResponse(sdkResponse)
      .build()
    new DefaultTransferProgress(progressSnapshot)
  }

  private def setupMockTaggingInteractions(asyncClientMock: S3AsyncClient, existingTags: java.util.Collection[Tag]) = {
    val mockGetResponseObject = GetObjectTaggingResponse.builder().tagSet(existingTags).build()
    when(asyncClientMock.getObjectTagging(any[GetObjectTaggingRequest]))
      .thenReturn(CompletableFuture.completedFuture(mockGetResponseObject))

    val mockPutResponseObject = PutObjectTaggingResponse.builder().build()
    when(asyncClientMock.putObjectTagging(any[PutObjectTaggingRequest]))
      .thenReturn(CompletableFuture.completedFuture(mockPutResponseObject))
    mockPutResponseObject
  }

}
