package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import software.amazon.awssdk.http.SdkHttpResponse
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import uk.gov.nationalarchives.DADynamoDBClient.DynamoDbRequest

import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters.MapHasAsJava
import scala.language.postfixOps

class DADynamoDBClientTest extends AnyFlatSpec with MockitoSugar {

  "getAttributeValue" should "pass in the correct values to the GetItemRequest" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val clientGetItemResponse = GetItemResponse
      .builder()
      .item(Map("mockAttribute" -> AttributeValue.builder().s("mockAttributeValue").build()) asJava)
      .build()
    val clientGetItemResponseInCf = CompletableFuture.completedFuture(clientGetItemResponse)
    val getItemCaptor: ArgumentCaptor[GetItemRequest] = ArgumentCaptor.forClass(classOf[GetItemRequest])

    when(mockDynamoDbAsyncClient.getItem(getItemCaptor.capture())).thenReturn(clientGetItemResponseInCf)

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val dynamoDbRequest =
      DynamoDbRequest(
        "mockTableName",
        Map(
          "mockPrimaryKeyName" -> AttributeValue.builder().s("mockPrimaryKeyValue").build()
        ),
        "mockAttribute"
      )

    client.getAttributeValue(dynamoDbRequest).unsafeRunSync()
    val getItemCaptorValue = getItemCaptor.getValue

    getItemCaptorValue.tableName() should be("mockTableName")
    getItemCaptorValue.key().toString should be("""{mockPrimaryKeyName=AttributeValue(S=mockPrimaryKeyValue)}""")
  }

  "getAttributeValue" should "return the correct value if attributeName is valid" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val clientGetItemResponse = GetItemResponse
      .builder()
      .item(Map("mockAttribute" -> AttributeValue.builder().s("mockAttributeValue").build()) asJava)
      .build()
    val clientGetItemResponseInCf = CompletableFuture.completedFuture(clientGetItemResponse)

    when(mockDynamoDbAsyncClient.getItem(any[GetItemRequest])).thenReturn(clientGetItemResponseInCf)

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val dynamoDbRequest =
      DynamoDbRequest(
        "mockTableName",
        Map(
          "mockPrimaryKeyName" -> AttributeValue.builder().s("mockPrimaryKeyValue").build()
        ),
        "mockAttribute"
      )

    val getAttributeValueResponse = client.getAttributeValue(dynamoDbRequest).unsafeRunSync()
    getAttributeValueResponse.s() should be("mockAttributeValue")
  }

  "getAttributeValue" should "return an error if attributeName is invalid" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val clientGetItemResponse = GetItemResponse
      .builder()
      .item(Map("mockAttribute" -> AttributeValue.builder().s("mockAttributeValue").build()) asJava)
      .build()
    val clientGetItemResponseInCf = CompletableFuture.completedFuture(clientGetItemResponse)

    when(mockDynamoDbAsyncClient.getItem(any[GetItemRequest])).thenReturn(clientGetItemResponseInCf)

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val dynamoDbRequest = {
      DynamoDbRequest(
        "mockTableName",
        Map(
          "mockPrimaryKeyName" -> AttributeValue.builder().s("mockPrimaryKeyValue").build()
        ),
        "incorrectMockAttributeName"
      )
    }

    val getAttributeValueEx = intercept[Exception] {
      client.getAttributeValue(dynamoDbRequest).unsafeRunSync()
    }
    getAttributeValueEx.getMessage should be("key not found: incorrectMockAttributeName")
  }

  "getAttributeValue" should "return an error if the client does" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]

    when(mockDynamoDbAsyncClient.getItem(any[GetItemRequest])).thenThrow(
      ResourceNotFoundException.builder.message("Table name could not be found").build()
    )

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val dynamoDbRequest =
      DynamoDbRequest(
        "tableNameThatDoesNotExist",
        Map(
          "mockPrimaryKeyName" -> AttributeValue.builder().s("mockPrimaryKeyValue").build()
        ),
        "mockAttribute"
      )

    val getAttributeValueEx = intercept[Exception] {
      client.getAttributeValue(dynamoDbRequest).unsafeRunSync()
    }
    getAttributeValueEx.getMessage should be("Table name could not be found")
  }

  "updateAttributeValue" should "pass in the correct values to the UpdateItemRequest" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val sdkHttpResponse = SdkHttpResponse
      .builder()
      .statusCode(200)
      .build()

    val updateItemResponseBuilder = UpdateItemResponse.builder()
    updateItemResponseBuilder.sdkHttpResponse(sdkHttpResponse)

    val clientGetItemResponse = updateItemResponseBuilder.build()
    val clientGetItemResponseInCf = CompletableFuture.completedFuture(clientGetItemResponse)
    val updateItemCaptor: ArgumentCaptor[UpdateItemRequest] = ArgumentCaptor.forClass(classOf[UpdateItemRequest])

    when(mockDynamoDbAsyncClient.updateItem(updateItemCaptor.capture())).thenReturn(clientGetItemResponseInCf)

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val dynamoDbRequest =
      DynamoDbRequest(
        "mockTableName",
        Map(
          "mockPrimaryKeyName" -> AttributeValue.builder().s("mockPrimaryKeyValue").build()
        ),
        "mockAttribute",
        Some(AttributeValue.builder().s("newMockItemValue").build())
      )

    client.updateAttributeValue(dynamoDbRequest).unsafeRunSync()
    val updateItemCaptorValue = updateItemCaptor.getValue

    updateItemCaptorValue.tableName() should be("mockTableName")
    updateItemCaptorValue.key().toString should be("""{mockPrimaryKeyName=AttributeValue(S=mockPrimaryKeyValue)}""")
  }

  "updateAttributeValue" should "return a 200 status code if the request is fine" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val sdkHttpResponse = SdkHttpResponse
      .builder()
      .statusCode(200)
      .build()

    val updateItemResponseBuilder = UpdateItemResponse.builder()
    updateItemResponseBuilder.sdkHttpResponse(sdkHttpResponse)

    val clientGetItemResponse = updateItemResponseBuilder.build()
    val clientGetItemResponseInCf = CompletableFuture.completedFuture(clientGetItemResponse)

    when(mockDynamoDbAsyncClient.updateItem(any[UpdateItemRequest])).thenReturn(clientGetItemResponseInCf)

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val dynamoDbRequest =
      DynamoDbRequest(
        "mockTableName",
        Map(
          "mockPrimaryKeyName" -> AttributeValue.builder().s("mockPrimaryKeyValue").build()
        ),
        "mockAttribute",
        Some(AttributeValue.builder().s("newMockItemValue").build())
      )
    val updateItemResponseStatusCode = client.updateAttributeValue(dynamoDbRequest).unsafeRunSync()
    updateItemResponseStatusCode should be(200)
  }

  "updateAttributeValue" should "return an Exception if there is something wrong with the request/AWS" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]

    when(mockDynamoDbAsyncClient.updateItem(any[UpdateItemRequest])).thenThrow(
      ResourceNotFoundException.builder.message("Table name could not be found").build()
    )

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val dynamoDbRequest =
      DynamoDbRequest(
        "tableNameThatDoesNotExist",
        Map(
          "mockPrimaryKeyName" -> AttributeValue.builder().s("mockPrimaryKeyValue").build()
        ),
        "mockAttribute",
        Some(AttributeValue.builder().s("newMockAttributeValue").build())
      )
    val updateAttributeValueEx = intercept[Exception] {
      client.updateAttributeValue(dynamoDbRequest).unsafeRunSync()
    }
    updateAttributeValueEx.getMessage should be("Table name could not be found")
  }
}
