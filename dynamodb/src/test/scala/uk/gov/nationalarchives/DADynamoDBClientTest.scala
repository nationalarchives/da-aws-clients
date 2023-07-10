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

  "getAttributeValues" should "pass in the correct values to the GetItemRequest" in {
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
        Map("mockPrimaryKeyName" -> AttributeValue.builder().s("mockPrimaryKeyValue").build()),
        Map("mockAttribute" -> None)
      )

    client.getAttributeValues(dynamoDbRequest).unsafeRunSync()
    val getItemCaptorValue = getItemCaptor.getValue

    getItemCaptorValue.tableName() should be("mockTableName")
    getItemCaptorValue.key().toString should be("""{mockPrimaryKeyName=AttributeValue(S=mockPrimaryKeyValue)}""")
  }

  "getAttributeValues" should "return the correct value if attributeName is valid" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val clientGetItemResponse = GetItemResponse
      .builder()
      .item(
        Map(
          "mockAttribute" -> AttributeValue.builder().s("mockAttributeValue").build(),
          "mockAttribute2" -> AttributeValue.builder().s("mockAttributeValue2").build()
        ) asJava
      )
      .build()
    val clientGetItemResponseInCf = CompletableFuture.completedFuture(clientGetItemResponse)

    when(mockDynamoDbAsyncClient.getItem(any[GetItemRequest])).thenReturn(clientGetItemResponseInCf)

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val dynamoDbRequest =
      DynamoDbRequest(
        "mockTableName",
        Map("mockPrimaryKeyName" -> AttributeValue.builder().s("mockPrimaryKeyValue").build()),
        Map("mockAttribute" -> None, "mockAttribute2" -> None)
      )

    val getAttributeValueResponse = client.getAttributeValues(dynamoDbRequest).unsafeRunSync()

    getAttributeValueResponse("mockAttribute").s() should be("mockAttributeValue")
    getAttributeValueResponse("mockAttribute2").s() should be("mockAttributeValue2")
  }

  "getAttributeValues" should "return an error if even one of the attributeNames could not be found" in {
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
        Map("mockPrimaryKeyName" -> AttributeValue.builder().s("mockPrimaryKeyValue").build()),
        Map("incorrectMockAttributeName" -> None, "mockAttribute" -> None)
      )
    }

    val getAttributeValueEx = intercept[Exception] {
      client.getAttributeValues(dynamoDbRequest).unsafeRunSync()
    }
    getAttributeValueEx.getMessage should be("key not found: incorrectMockAttributeName")
  }

  "getAttributeValues" should "return an error if the client does" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]

    when(mockDynamoDbAsyncClient.getItem(any[GetItemRequest])).thenThrow(
      ResourceNotFoundException.builder.message("Table name could not be found").build()
    )

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val dynamoDbRequest =
      DynamoDbRequest(
        "tableNameThatDoesNotExist",
        Map("mockPrimaryKeyName" -> AttributeValue.builder().s("mockPrimaryKeyValue").build()),
        Map("mockAttribute" -> None)
      )

    val getAttributeValueEx = intercept[Exception] {
      client.getAttributeValues(dynamoDbRequest).unsafeRunSync()
    }
    getAttributeValueEx.getMessage should be("Table name could not be found")
  }

  "updateAttributeValues" should "pass in the correct values to the UpdateItemRequest" in {
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
        Map("mockPrimaryKeyName" -> AttributeValue.builder().s("mockPrimaryKeyValue").build()),
        Map("mockAttribute" -> Some(AttributeValue.builder().s("newMockItemValue").build()))
      )

    client.updateAttributeValues(dynamoDbRequest).unsafeRunSync()
    val updateItemCaptorValue = updateItemCaptor.getValue

    updateItemCaptorValue.tableName() should be("mockTableName")
    updateItemCaptorValue.key().toString should be("""{mockPrimaryKeyName=AttributeValue(S=mockPrimaryKeyValue)}""")
    updateItemCaptorValue.attributeUpdates().toString should be(
      """{mockAttribute=AttributeValueUpdate(Value=AttributeValue(S=newMockItemValue), Action=PUT)}"""
    )
  }

  "updateAttributeValues" should "return a 200 status code if the request is fine" in {
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
        Map("mockPrimaryKeyName" -> AttributeValue.builder().s("mockPrimaryKeyValue").build()),
        Map("mockAttribute" -> Some(AttributeValue.builder().s("newMockItemValue").build()))
      )
    val updateItemResponseStatusCode = client.updateAttributeValues(dynamoDbRequest).unsafeRunSync()
    updateItemResponseStatusCode should be(200)
  }

  "updateAttributeValues" should "return an Exception if there is something wrong with the request/AWS" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]

    when(mockDynamoDbAsyncClient.updateItem(any[UpdateItemRequest])).thenThrow(
      ResourceNotFoundException.builder.message("Table name could not be found").build()
    )

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val dynamoDbRequest =
      DynamoDbRequest(
        "tableNameThatDoesNotExist",
        Map("mockPrimaryKeyName" -> AttributeValue.builder().s("mockPrimaryKeyValue").build()),
        Map("mockAttribute" -> Some(AttributeValue.builder().s("newMockItemValue").build()))
      )
    val updateAttributeValueEx = intercept[Exception] {
      client.updateAttributeValues(dynamoDbRequest).unsafeRunSync()
    }
    updateAttributeValueEx.getMessage should be("Table name could not be found")
  }
}
