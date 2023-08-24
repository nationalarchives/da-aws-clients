package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}
import org.scanamo.generic.auto._
import org.scanamo.query.ConditionExpression._
import org.scanamo.request.RequestCondition
import org.scanamo.syntax._
import org.scanamo.{DynamoReadError, DynamoValue}
import software.amazon.awssdk.http.SdkHttpResponse
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import uk.gov.nationalarchives.DADynamoDBClient._

import java.util
import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

class DADynamoDBClientTest extends AnyFlatSpec with MockitoSugar with TableDrivenPropertyChecks {
  case class Pk(mockPrimaryKeyName: String)
  trait MockResponse extends Product
  case class MockNestedRequest(mockSingleAttributeResponse: MockSingleAttributeRequest)
  case class MockTwoAttributesRequest(mockAttribute: String, mockAttribute2: String) extends MockResponse
  case class MockSingleAttributeRequest(mockAttribute: String) extends MockResponse

  val primaryKeysTable: TableFor2[List[Pk], FieldName] = Table(
    ("keys", "expectedAttributeNamesAndValues"),
    (List(Pk("mockPrimaryKeyValue1")), "[{mockPrimaryKeyName=AttributeValue(S=mockPrimaryKeyValue1)}]"),
    (
      List(Pk("mockPrimaryKeyValue1"), Pk("mockPrimaryKeyValue2")),
      "[{mockPrimaryKeyName=AttributeValue(S=mockPrimaryKeyValue1)}, {mockPrimaryKeyName=AttributeValue(S=mockPrimaryKeyValue2)}]"
    )
  )
  forAll(primaryKeysTable) { (keys, expectedAttributeNamesAndValues) =>
    "getItems" should s"pass in the correct table name and $expectedAttributeNamesAndValues to the BatchGetItemRequest" in {
      val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
      val response = Map("mockAttribute" -> AttributeValue.builder().s("mockAttributeValue").build()).asJava
      val responses = Map("mockTableName" -> util.Arrays.asList(response)).asJava
      val clientBatchGetItemResponse = BatchGetItemResponse
        .builder()
        .responses(responses)
        .build()
      val clientGetItemResponseInCf = CompletableFuture.completedFuture(clientBatchGetItemResponse)
      val getBatchItemCaptor: ArgumentCaptor[BatchGetItemRequest] =
        ArgumentCaptor.forClass(classOf[BatchGetItemRequest])

      when(mockDynamoDbAsyncClient.batchGetItem(getBatchItemCaptor.capture())).thenReturn(clientGetItemResponseInCf)

      val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

      val result = client.getItems[MockSingleAttributeRequest, Pk](keys, "mockTableName").unsafeRunSync()

      result.size should equal(1)
      result.head.mockAttribute should equal("mockAttributeValue")

      val getBatchItemCaptorValue = getBatchItemCaptor.getValue
      val requestItems = getBatchItemCaptorValue.requestItems()
      requestItems.size() should equal(1)
      val tableName = requestItems.keySet().asScala.head
      tableName should equal("mockTableName")
      requestItems.get(tableName).keys().toString should equal(expectedAttributeNamesAndValues)
    }
  }

  "getItems" should "return the correct value if attributeName is valid" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val responses = Map(
      "mockTableName" -> util.Arrays.asList(
        Map(
          "mockAttribute" -> AttributeValue.builder().s("mockAttributeValue").build(),
          "mockAttribute2" -> AttributeValue.builder().s("mockAttributeValue2").build()
        ) asJava
      )
    ).asJava
    val clientBatchGetItemResponse = BatchGetItemResponse
      .builder()
      .responses(responses)
      .build()
    val clientGetItemResponseInCf = CompletableFuture.completedFuture(clientBatchGetItemResponse)

    when(mockDynamoDbAsyncClient.batchGetItem(any[BatchGetItemRequest])).thenReturn(clientGetItemResponseInCf)

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val getAttributeValueResponse: List[MockTwoAttributesRequest] =
      client.getItems[MockTwoAttributesRequest, Pk](List(Pk("mockPrimaryKeyValue")), "mockTableName").unsafeRunSync()

    getAttributeValueResponse.size should equal(1)
    getAttributeValueResponse.head.mockAttribute should equal("mockAttributeValue")
    getAttributeValueResponse.head.mockAttribute2 should equal("mockAttributeValue2")
  }

  "getItems" should "return an empty string if the dynamo doesn't return an value for an attribute" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val responses = Map(
      "mockTableName" -> util.Arrays.asList(
        Map(
          "mockAttribute" -> AttributeValue.builder().s("mockAttributeValue").build(),
          "invalidAttribute" -> AttributeValue.builder().s("mockAttributeValue").build()
        ) asJava
      )
    ).asJava
    val clientBatchGetItemResponse = BatchGetItemResponse
      .builder()
      .responses(responses)
      .build()
    val clientGetItemResponseInCf = CompletableFuture.completedFuture(clientBatchGetItemResponse)

    when(mockDynamoDbAsyncClient.batchGetItem(any[BatchGetItemRequest])).thenReturn(clientGetItemResponseInCf)

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val getAttributeValueResponse: List[MockTwoAttributesRequest] =
      client.getItems[MockTwoAttributesRequest, Pk](List(Pk("mockPrimaryKeyValue")), "mockTableName").unsafeRunSync()

    getAttributeValueResponse.head.mockAttribute2 should equal("")
  }

  "getItems" should "return an error if the value is of an unexpected type" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val responses = Map(
      "mockTableName" -> util.Arrays.asList(
        Map(
          "mockAttribute" -> AttributeValue.builder().n("1").build()
        ) asJava
      )
    ).asJava
    val clientBatchGetItemResponse = BatchGetItemResponse
      .builder()
      .responses(responses)
      .build()
    val clientGetItemResponseInCf = CompletableFuture.completedFuture(clientBatchGetItemResponse)

    when(mockDynamoDbAsyncClient.batchGetItem(any[BatchGetItemRequest])).thenReturn(clientGetItemResponseInCf)

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val ex = intercept[Exception] {
      client.getItems[MockSingleAttributeRequest, Pk](List(Pk("mockPrimaryKeyValue")), "mockTableName").unsafeRunSync()
    }
    ex.getMessage should equal("'mockAttribute': not of type: 'S' was 'DynNum(1)'")
  }

  "getItems" should "return an error if there are nested field values missing" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val responses = Map(
      "mockTableName" -> util.Arrays.asList(
        Map(
          "invalidAttribute" -> AttributeValue.builder().s("mockAttributeValue").build()
        ) asJava
      )
    ).asJava
    val clientBatchGetItemResponse = BatchGetItemResponse
      .builder()
      .responses(responses)
      .build()
    val clientGetItemResponseInCf = CompletableFuture.completedFuture(clientBatchGetItemResponse)

    when(mockDynamoDbAsyncClient.batchGetItem(any[BatchGetItemRequest])).thenReturn(clientGetItemResponseInCf)

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val ex = intercept[Exception] {
      client.getItems[MockNestedRequest, Pk](List(Pk("mockPrimaryKeyValue")), "mockTableName").unsafeRunSync()
    }
    ex.getMessage should equal("'mockSingleAttributeResponse': missing")
  }

  "getItems" should "return an error if the client does" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]

    when(mockDynamoDbAsyncClient.batchGetItem(any[BatchGetItemRequest])).thenThrow(
      ResourceNotFoundException.builder.message("Table name could not be found").build()
    )

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val getAttributeValueEx = intercept[Exception] {
      client.getItems[MockSingleAttributeRequest, Pk](List(Pk("id")), "table").unsafeRunSync()
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
      DADynamoDbRequest(
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
      DADynamoDbRequest(
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
      DADynamoDbRequest(
        "tableNameThatDoesNotExist",
        Map("mockPrimaryKeyName" -> AttributeValue.builder().s("mockPrimaryKeyValue").build()),
        Map("mockAttribute" -> Some(AttributeValue.builder().s("newMockItemValue").build()))
      )
    val updateAttributeValueEx = intercept[Exception] {
      client.updateAttributeValues(dynamoDbRequest).unsafeRunSync()
    }
    updateAttributeValueEx.getMessage should be("Table name could not be found")
  }

  implicit val productFormat: Typeclass[MockResponse] = new Typeclass[MockResponse] {
    override def read(av: DynamoValue): Either[DynamoReadError, MockResponse] = Right(MockSingleAttributeRequest(""))

    override def write(t: MockResponse): DynamoValue = t match {
      case MockSingleAttributeRequest(mockAttribute) =>
        DynamoValue.fromMap(Map("mockAttribute" -> DynamoValue.fromString(mockAttribute)))
      case MockTwoAttributesRequest(mockAttribute, mockAttribute2) =>
        DynamoValue.fromMap(
          Map(
            "mockAttribute" -> DynamoValue.fromString(mockAttribute),
            "mockAttribute2" -> DynamoValue.fromString(mockAttribute2)
          )
        )
    }
  }

  val writeItemsTable: TableFor2[List[MockResponse], FieldName] = Table(
    ("input", "expectedAttributeNamesAndValues"),
    (List(MockSingleAttributeRequest("mockValue")), "{mockAttribute=AttributeValue(S=mockValue)}"),
    (
      List(MockSingleAttributeRequest("mockValue1"), MockSingleAttributeRequest("mockValue2")),
      "{mockAttribute=AttributeValue(S=mockValue1)} {mockAttribute=AttributeValue(S=mockValue2)}"
    ),
    (
      List(MockTwoAttributesRequest("mockValue1", "mockValue2")),
      "{mockAttribute=AttributeValue(S=mockValue1), mockAttribute2=AttributeValue(S=mockValue2)}"
    ),
    (
      List(
        MockTwoAttributesRequest("mockValue1", "mockValue2"),
        MockTwoAttributesRequest("mockValue3", "mockValue4")
      ),
      "{mockAttribute=AttributeValue(S=mockValue1), mockAttribute2=AttributeValue(S=mockValue2)} {mockAttribute=AttributeValue(S=mockValue3), mockAttribute2=AttributeValue(S=mockValue4)}"
    )
  )

  forAll(writeItemsTable) { (input, expectedAttributeNamesAndValues) =>
    "writeItems" should s"send the correct table name and $expectedAttributeNamesAndValues to the BatchWriteItemRequest" in {
      val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
      val sdkHttpResponse = SdkHttpResponse
        .builder()
        .statusCode(200)
        .build()
      val writeItemResponse = BatchWriteItemResponse.builder
      writeItemResponse.sdkHttpResponse(sdkHttpResponse)
      val writeCaptor: ArgumentCaptor[BatchWriteItemRequest] = ArgumentCaptor.forClass(classOf[BatchWriteItemRequest])

      when(mockDynamoDbAsyncClient.batchWriteItem(writeCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(writeItemResponse.build()))
      val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)
      val response = client.writeItems("table", input).unsafeRunSync()

      response.sdkHttpResponse().statusCode() should equal(200)

      writeCaptor.getAllValues.size should equal(1)
      val batchWriteItemRequest = writeCaptor.getAllValues.asScala.head
      val batchWriteItemRequestKeys = batchWriteItemRequest.requestItems().keySet().asScala
      batchWriteItemRequestKeys.size should equal(1)
      batchWriteItemRequestKeys.head should equal("table")
      val batchWriteItemRequestValues =
        batchWriteItemRequest.requestItems().get(batchWriteItemRequestKeys.head).asScala
      batchWriteItemRequestValues.size should equal(input.length)
      val attributeNameAndValues = batchWriteItemRequestValues.map(_.putRequest().item().toString).mkString(" ")

      attributeNameAndValues should equal(expectedAttributeNamesAndValues)
    }
  }

  "writeItems" should "return an error if the dynamo client returns an error" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    when(mockDynamoDbAsyncClient.batchWriteItem(any[BatchWriteItemRequest]))
      .thenThrow(new Exception("Error writing to dynamo"))

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)
    val ex = intercept[Exception] {
      client.writeItems("table", List(MockSingleAttributeRequest("mockValue"))).unsafeRunSync()
    }
    ex.getMessage should equal("Error writing to dynamo")
  }

  val queryTable: TableFor2[RequestCondition, FieldName] = Table(
    ("query", "expectedQuery"),
    ("testAttribute" === "testValue", "testAttribute = AttributeValue(S=testValue)"),
    (
      "testAttribute" === "testValue" and "testAttribute2" === "testValue2",
      "testAttribute = AttributeValue(S=testValue) AND testAttribute2 = AttributeValue(S=testValue)2"
    ),
    (
      "testAttribute" === "testValue" or "testNumber" < 5,
      "(testAttribute = AttributeValue(S=testValue) OR testNumber < AttributeValue(N=5))"
    ),
    ("testNumber" > 6 or "testNumber2" < 5, "(testNumber > AttributeValue(N=6) OR testNumber2 < AttributeValue(N=5))"),
    (
      "testNumber" > 0 and "testNumber2" < 3,
      "(testNumber > AttributeValue(N=0) AND testNumber2 < AttributeValue(N=3))"
    ),
    (
      "testAttribute" < 0 and ("beginsWithAttribute" beginsWith 3),
      "(testAttribute < AttributeValue(N=0) AND begins_with(beginsWithAttribute, AttributeValue(N=3)))"
    )
  )

  forAll(queryTable) { (query, expectedQuery) =>
    "scanItems" should s"send the correct $expectedQuery to the ScanRequest" in {
      val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
      val scanRequestCaptor: ArgumentCaptor[ScanRequest] = ArgumentCaptor.forClass(classOf[ScanRequest])
      val items = util.Collections.emptyMap[String, AttributeValue]()
      val scanResponse = ScanResponse.builder
        .items(items)
        .build()

      val clientScanResponseInCf: CompletableFuture[ScanResponse] = CompletableFuture.completedFuture(scanResponse)

      when(mockDynamoDbAsyncClient.scan(scanRequestCaptor.capture())).thenReturn(clientScanResponseInCf)

      val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

      client.scanItems[MockSingleAttributeRequest]("testTable", query).unsafeRunSync()

      val scanRequestValue = scanRequestCaptor.getValue
      val attributeNames = scanRequestValue.expressionAttributeNames()
      val filterExpressionKeysReplaced = attributeNames.asScala.foldLeft(scanRequestValue.filterExpression()) {
        (filterExpression, attributeNames) =>
          filterExpression.replaceAll(attributeNames._1, attributeNames._2)
      }
      val filterExpressionValuesReplaced =
        scanRequestValue.expressionAttributeValues.asScala.foldLeft(filterExpressionKeysReplaced) {
          (filterExpression, expressionValues) =>
            filterExpression.replaceAll(expressionValues._1, expressionValues._2.toString)
        }
      filterExpressionValuesReplaced should equal(expectedQuery)
    }
  }

  "scanItems" should "return the correct value if attributeName is valid" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val items = Map(
      "mockAttribute" -> AttributeValue.builder().s("mockAttributeValue").build(),
      "mockAttribute2" -> AttributeValue.builder().s("mockAttributeValue2").build()
    ).asJava
    val scanResponse = ScanResponse.builder
      .items(items)
      .build()
    val clientScanResponseInCf: CompletableFuture[ScanResponse] = CompletableFuture.completedFuture(scanResponse)

    when(mockDynamoDbAsyncClient.scan(any[ScanRequest])).thenReturn(clientScanResponseInCf)
    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val scanItemsResponseF: IO[List[MockTwoAttributesRequest]] =
      client.scanItems[MockTwoAttributesRequest]("testTable", "mockAttribute" > 0)
    val scanItemsResponse = scanItemsResponseF.unsafeRunSync()

    scanItemsResponse.head.mockAttribute should equal("mockAttributeValue")
    scanItemsResponse.head.mockAttribute2 should equal("mockAttributeValue2")
  }

  "scanItems" should "return an empty string if the dynamo doesn't return an value for an attribute" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val items = Map(
      "mockAttribute" -> AttributeValue.builder().s("mockAttributeValue").build(),
      "invalidAttribute" -> AttributeValue.builder().s("mockAttributeValue").build()
    ).asJava
    val scanResponse = ScanResponse.builder
      .items(items)
      .build()
    val clientScanResponseInCf: CompletableFuture[ScanResponse] = CompletableFuture.completedFuture(scanResponse)

    when(mockDynamoDbAsyncClient.scan(any[ScanRequest])).thenReturn(clientScanResponseInCf)

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val scanItemsResponse = client
      .scanItems[MockTwoAttributesRequest]("testTable", "mockAttribute" === "mockAttributeValue" and "asdasd" === "")
      .unsafeRunSync()

    scanItemsResponse.head.mockAttribute should equal("mockAttributeValue")
    scanItemsResponse.head.mockAttribute2 should equal("")

  }

  "scanItems" should "return an error if the value is of an unexpected type" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val items = Map(
      "mockAttribute" -> AttributeValue.builder().n("1").build()
    ).asJava
    val scanResponse = ScanResponse.builder
      .items(items)
      .build()
    val clientScanResponseInCf: CompletableFuture[ScanResponse] = CompletableFuture.completedFuture(scanResponse)

    when(mockDynamoDbAsyncClient.scan(any[ScanRequest])).thenReturn(clientScanResponseInCf)

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val ex = intercept[Exception] {
      client
        .scanItems[MockSingleAttributeRequest]("testTable", "mockAttribute" === "mockAttributeValue")
        .unsafeRunSync()
    }
    ex.getMessage should equal("'mockAttribute': not of type: 'S' was 'DynNum(1)'")
  }

  "scanItems" should "return an error if there are nested field values missing" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val items = Map(
      "invalidAttribute" -> AttributeValue.builder().s("mockAttributeValue").build()
    ).asJava

    val scanResponse = ScanResponse.builder
      .items(items)
      .build()
    val clientScanResponseInCf: CompletableFuture[ScanResponse] = CompletableFuture.completedFuture(scanResponse)

    when(mockDynamoDbAsyncClient.scan(any[ScanRequest])).thenReturn(clientScanResponseInCf)

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val ex = intercept[Exception] {
      client.scanItems[MockNestedRequest]("testTable", "mockAttribute" === "mockAttributeValue").unsafeRunSync()
    }
    ex.getMessage should equal("'mockSingleAttributeResponse': missing")
  }

  "scanItems" should "return an error if the client does" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]

    when(mockDynamoDbAsyncClient.scan(any[ScanRequest])).thenThrow(
      ResourceNotFoundException.builder.message("Table name could not be found").build()
    )

    val client = new DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val ex = intercept[Exception] {
      client.scanItems[MockNestedRequest]("testTable", "mockAttribute" === "mockAttributeValue").unsafeRunSync()
    }
    ex.getMessage should be("Table name could not be found")
  }

}
