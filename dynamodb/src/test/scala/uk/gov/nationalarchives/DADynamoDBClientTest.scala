package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.NonImplicitAssertions
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.*
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}
import org.scalatestplus.mockito.MockitoSugar
import org.scanamo.generic.auto.*
import org.scanamo.query.ConditionExpression.*
import org.scanamo.query.{ConditionExpression, QueryableKeyCondition, UniqueKeyCondition}
import org.scanamo.request.RequestCondition
import org.scanamo.syntax.*
import org.scanamo.{DynamoFormat, DynamoReadError, DynamoValue}
import software.amazon.awssdk.http.SdkHttpResponse
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.*
import uk.gov.nationalarchives.DADynamoDBClient.{*, given}

import java.util
import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters.*
import scala.language.postfixOps
import scala.util.Try

class DADynamoDBClientTest
    extends AnyFlatSpec
    with MockitoSugar
    with TableDrivenPropertyChecks
    with NonImplicitAssertions {
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

      val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)

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

    val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)

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

    val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)

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

    val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)

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

    val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)

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

    val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)

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

    val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)

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

    val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)

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

    val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)

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

  given Typeclass[MockResponse] = new Typeclass[MockResponse] {
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

  val writeAndDeleteTable
      : TableFor2[String, (DADynamoDBClient[IO], String, List[MockResponse]) => IO[List[BatchWriteItemResponse]]] =
    Table(
      ("functionName", "function"),
      (
        "deleteItems",
        (client: DADynamoDBClient[IO], tableName: String, items: List[MockResponse]) =>
          client.deleteItems(tableName, items)
      ),
      (
        "writeItems",
        (client: DADynamoDBClient[IO], tableName: String, items: List[MockResponse]) =>
          client.writeItems(tableName, items)
      )
    )

  val writeAndDeleteItemsTable: TableFor2[List[MockResponse], FieldName] = Table(
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

  forAll(writeAndDeleteTable) { (functionName, writeOrDeleteFunction) =>
    forAll(writeAndDeleteItemsTable) { (input, expectedAttributeNamesAndValues) =>
      functionName should s"send the correct table name and $expectedAttributeNamesAndValues to the BatchWriteItemRequest" in {
        val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
        val sdkHttpResponse = SdkHttpResponse
          .builder()
          .statusCode(200)
          .build()
        val writeItemResponse = BatchWriteItemResponse.builder
          .unprocessedItems(java.util.Map.of())
        writeItemResponse.sdkHttpResponse(sdkHttpResponse)
        val writeCaptor: ArgumentCaptor[BatchWriteItemRequest] = ArgumentCaptor.forClass(classOf[BatchWriteItemRequest])

        when(mockDynamoDbAsyncClient.batchWriteItem(writeCaptor.capture()))
          .thenReturn(CompletableFuture.completedFuture(writeItemResponse.build()))
        val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)
        val responses = writeOrDeleteFunction(client, "table", input).unsafeRunSync()

        responses.foreach(_.sdkHttpResponse().statusCode() should equal(200))

        writeCaptor.getAllValues.size should equal(1)
        val batchWriteItemRequest = writeCaptor.getAllValues.asScala.head
        val batchWriteItemRequestKeys = batchWriteItemRequest.requestItems().keySet().asScala
        batchWriteItemRequestKeys.size should equal(1)
        batchWriteItemRequestKeys.head should equal("table")
        val batchWriteItemRequestValues =
          batchWriteItemRequest.requestItems().get(batchWriteItemRequestKeys.head).asScala
        batchWriteItemRequestValues.size should equal(input.length)
        val attributeNameAndValues =
          if functionName == "deleteItems" then
            batchWriteItemRequestValues.map(_.deleteRequest().key().toString).mkString(" ")
          else batchWriteItemRequestValues.map(_.putRequest().item().toString).mkString(" ")

        attributeNameAndValues should equal(expectedAttributeNamesAndValues)
      }
    }

    functionName should "call the dynamo client again if there are unprocessed items" in {
      val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
      val input = List(MockSingleAttributeRequest("mockValue"))
      val keyMap = Map("unprocessedKey" -> AttributeValue.builder.s("unprocessedValue").build).asJava
      val unprocessedWriteRequest =
        if functionName == "deleteItems" then
          WriteRequest.builder.deleteRequest(DeleteRequest.builder.key(keyMap).build).build
        else WriteRequest.builder.putRequest(PutRequest.builder.item(keyMap).build).build
      val writeItemResponseWithUnprocessed = BatchWriteItemResponse.builder
        .unprocessedItems(Map("table" -> List(unprocessedWriteRequest).asJava).asJava)
        .build
      val writeCaptor: ArgumentCaptor[BatchWriteItemRequest] = ArgumentCaptor.forClass(classOf[BatchWriteItemRequest])

      when(mockDynamoDbAsyncClient.batchWriteItem(writeCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(writeItemResponseWithUnprocessed))
        .thenReturn(CompletableFuture.completedFuture(BatchWriteItemResponse.builder.build))
      val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)

      val responses = writeOrDeleteFunction(client, "table", input).unsafeRunSync()

      verify(mockDynamoDbAsyncClient, times(2)).batchWriteItem(any[BatchWriteItemRequest])

      val writeRequests = writeCaptor.getAllValues.asScala

      def getValue(batchWriteItemRequest: BatchWriteItemRequest, key: String) = {
        val writeRequest = batchWriteItemRequest.requestItems().asScala.flatMap(_._2.asScala).head
        if functionName == "deleteItems" then writeRequest.deleteRequest().key().get(key).s()
        else writeRequest.putRequest().item.get(key).s()
      }

      getValue(writeRequests.head, "mockAttribute") should equal("mockValue")
      getValue(writeRequests.last, "unprocessedKey") should equal("unprocessedValue")
    }

    functionName should "return an error if the dynamo client returns an error" in {
      val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
      when(mockDynamoDbAsyncClient.batchWriteItem(any[BatchWriteItemRequest]))
        .thenThrow(new RuntimeException("Error writing to dynamo"))

      val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)
      val ex = intercept[Exception] {
        writeOrDeleteFunction(client, "table", List(MockSingleAttributeRequest("mockValue"))).unsafeRunSync()
      }
      ex.getMessage should equal("Error writing to dynamo")
    }
  }

  // For some reason the compiler is ignoring the implicit conversions when these are inside the table
  val equalsAnd: RequestCondition = "testAttribute" === "testValue" and "testAttribute2" === "testValue2"
  val equals: RequestCondition = "testAttribute" === "testValue"
  val equalsOr: RequestCondition = "testAttribute" === "testValue" or "testNumber" < 5
  val gtLtOr: RequestCondition = "testNumber" > 6 or "testNumber2" < 5
  val gtLtAnd: RequestCondition = "testNumber" > 0 and "testNumber2" < 3
  val ltBeginsWith: RequestCondition = "testAttribute" < 0 and ("beginsWithAttribute" beginsWith 3)
  val queryTable: TableFor2[RequestCondition, FieldName] = Table(
    ("query", "expectedQuery"),
    (equals, "testAttribute = AttributeValue(S=testValue)"),
    (equalsAnd, "testAttribute = AttributeValue(S=testValue) AND testAttribute2 = AttributeValue(S=testValue)2"),
    (equalsOr, "(testAttribute = AttributeValue(S=testValue) OR testNumber < AttributeValue(N=5))"),
    (gtLtOr, "(testNumber > AttributeValue(N=6) OR testNumber2 < AttributeValue(N=5))"),
    (gtLtAnd, "(testNumber > AttributeValue(N=0) AND testNumber2 < AttributeValue(N=3))"),
    (ltBeginsWith, "(testAttribute < AttributeValue(N=0) AND begins_with(beginsWithAttribute, AttributeValue(N=3)))")
  )

  forAll(queryTable) { (query, expectedQuery) =>
    "queryItems" should s"send the correct $expectedQuery to the QueryRequest" in {
      val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
      val queryRequestCaptor: ArgumentCaptor[QueryRequest] = ArgumentCaptor.forClass(classOf[QueryRequest])
      val items = util.Collections.emptyMap[String, AttributeValue]()
      val queryResponse = QueryResponse.builder
        .items(items)
        .build()

      val clientQueryResponseInCf: CompletableFuture[QueryResponse] = CompletableFuture.completedFuture(queryResponse)

      when(mockDynamoDbAsyncClient.query(queryRequestCaptor.capture())).thenReturn(clientQueryResponseInCf)

      val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)

      client.queryItems[MockSingleAttributeRequest]("testTable", query, Option("indexName")).unsafeRunSync()

      val queryRequestValue = queryRequestCaptor.getValue

      val attributeNames = queryRequestValue.expressionAttributeNames()
      val keyConditionExpressionKeysReplaced =
        attributeNames.asScala.foldLeft(queryRequestValue.keyConditionExpression()) {
          (keyConditionExpression, attributeNames) =>
            keyConditionExpression.replaceAll(attributeNames._1, attributeNames._2)
        }
      val keyConditionExpressionValuesReplaced =
        queryRequestValue.expressionAttributeValues.asScala.foldLeft(keyConditionExpressionKeysReplaced) {
          (keyConditionExpression, expressionValues) =>
            keyConditionExpression.replaceAll(expressionValues._1, expressionValues._2.toString)
        }
      keyConditionExpressionValuesReplaced should equal(expectedQuery)
      queryRequestValue.indexName() should equal("indexName")
    }
  }

  "queryItems" should "return the correct value if attributeName is valid" in {
    val items = Map(
      "mockAttribute" -> "mockAttributeValue",
      "mockAttribute2" -> "mockAttributeValue2"
    )

    val client = createDynamoClientFromItems(items)
    val queryItemsResponseF: IO[List[MockTwoAttributesRequest]] =
      client.queryItems[MockTwoAttributesRequest]("testTable", "mockAttribute" > 0, Option("indexName"))
    val queryItemsResponse = queryItemsResponseF.unsafeRunSync()

    queryItemsResponse.head.mockAttribute should equal("mockAttributeValue")
    queryItemsResponse.head.mockAttribute2 should equal("mockAttributeValue2")
  }

  "queryItems" should "call the query method twice if there are more items to be fetched" in {
    def items(idx: Int) = Map(
      "mockAttribute" -> AttributeValue.builder.s(s"mockAttributeValue$idx").build
    ).asJava

    val itemsRemaining =
      CompletableFuture.completedFuture(QueryResponse.builder.items(items(0)).lastEvaluatedKey(items(0)).build)
    val noneRemaining = CompletableFuture.completedFuture(QueryResponse.builder.items(items(1)).build)
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val requestCaptor: ArgumentCaptor[QueryRequest] = ArgumentCaptor.forClass(classOf[QueryRequest])
    when(mockDynamoDbAsyncClient.query(requestCaptor.capture)).thenReturn(itemsRemaining, noneRemaining)

    val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)
    val queryItemsResponseF: IO[List[MockSingleAttributeRequest]] =
      client.queryItems[MockSingleAttributeRequest]("testTable", "mockAttribute" > 0, Option("indexName"))
    val queryItemsResponse = queryItemsResponseF.unsafeRunSync()

    queryItemsResponse.length should equal(2)
    queryItemsResponse.map(_.mockAttribute).sorted should equal(List("mockAttributeValue0", "mockAttributeValue1"))

    val requestValues = requestCaptor.getAllValues.asScala
    requestValues.head.exclusiveStartKey().isEmpty should equal(true)
    requestValues.last.exclusiveStartKey().get("mockAttribute").s() should equal("mockAttributeValue0")
  }

  "queryItems" should "return an empty string if the dynamo doesn't return an value for an attribute" in {
    val items = Map(
      "mockAttribute" -> "mockAttributeValue",
      "invalidAttribute" -> "mockAttributeValue"
    )
    val client = createDynamoClientFromItems(items)

    val queryItemsResponse = client
      .queryItems[MockTwoAttributesRequest](
        "testTable",
        "mockAttribute" === "mockAttributeValue" and "asdasd" === "",
        Option("indexName")
      )
      .unsafeRunSync()

    queryItemsResponse.head.mockAttribute should equal("mockAttributeValue")
    queryItemsResponse.head.mockAttribute2 should equal("")

  }

  "queryItems" should "return an error if the value is of an unexpected type" in {
    val items = Map(
      "mockAttribute" -> "1"
    )
    val client = createDynamoClientFromItems(items)

    val ex = intercept[Exception] {
      client
        .queryItems[MockSingleAttributeRequest](
          "testTable",
          "mockAttribute" === "mockAttributeValue",
          Option("indexName")
        )
        .unsafeRunSync()
    }
    ex.getMessage should equal("'mockAttribute': not of type: 'S' was 'DynNum(1)'")
  }

  "queryItems" should "return an error if there are nested field values missing" in {
    val items = Map(
      "invalidAttribute" -> "mockAttributeValue"
    )

    val client = createDynamoClientFromItems(items)

    val ex = intercept[Exception] {
      client
        .queryItems[MockNestedRequest]("testTable", "mockAttribute" === "mockAttributeValue", Option("indexName"))
        .unsafeRunSync()
    }
    ex.getMessage should equal("'mockSingleAttributeResponse': missing")
  }

  "queryItems" should "return an error if the client does" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]

    when(mockDynamoDbAsyncClient.query(any[QueryRequest])).thenThrow(
      ResourceNotFoundException.builder.message("Table name could not be found").build()
    )

    val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val ex = intercept[Exception] {
      client
        .queryItems[MockNestedRequest]("testTable", "mockAttribute" === "mockAttributeValue", Option("indexName"))
        .unsafeRunSync()
    }
    ex.getMessage should be("Table name could not be found")
  }

  List((Some("attribute_not_exists(mockAttribute)"), "attribute_not_exists(mockAttribute)"), (None, null)).foreach {
    (conditionalExpression, expectedConditionalExpression) =>
      "writeItem" should s"pass in the correct values to the PutItemRequest if the conditional expression is $conditionalExpression" in {
        val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
        val sdkHttpResponse = SdkHttpResponse
          .builder()
          .statusCode(200)
          .build()

        val putItemResponseBuilder = PutItemResponse.builder()
        putItemResponseBuilder.sdkHttpResponse(sdkHttpResponse)

        val clientPutItemResponse = putItemResponseBuilder.build()
        val clientPutItemResponseInCf = CompletableFuture.completedFuture(clientPutItemResponse)
        val putItemRequestCaptor: ArgumentCaptor[PutItemRequest] = ArgumentCaptor.forClass(classOf[PutItemRequest])

        when(mockDynamoDbAsyncClient.putItem(putItemRequestCaptor.capture())).thenReturn(clientPutItemResponseInCf)

        val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)

        val dynamoDbWriteItemRequest =
          DADynamoDbWriteItemRequest(
            "mockTableName",
            Map(
              "mockAttribute" -> AttributeValue.builder().s("newMockItemValue").build(),
              "mockAttribute2" -> AttributeValue.builder().s("newMockItemValue2").build()
            ),
            conditionalExpression
          )

        client.writeItem(dynamoDbWriteItemRequest).unsafeRunSync()
        val putItemCaptorValue = putItemRequestCaptor.getValue

        putItemCaptorValue.tableName() should be("mockTableName")
        putItemCaptorValue.item().toString should be(
          "{mockAttribute=AttributeValue(S=newMockItemValue), " +
            "mockAttribute2=AttributeValue(S=newMockItemValue2)}"
        )
        putItemCaptorValue.conditionExpression() should be(expectedConditionalExpression)
      }
  }

  "writeItem" should "return a 200 status code if the request is fine" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val sdkHttpResponse = SdkHttpResponse
      .builder()
      .statusCode(200)
      .build()

    val putItemResponseBuilder = PutItemResponse.builder()
    putItemResponseBuilder.sdkHttpResponse(sdkHttpResponse)

    val clientPutItemResponse = putItemResponseBuilder.build()
    val clientPutItemResponseInCf = CompletableFuture.completedFuture(clientPutItemResponse)

    when(mockDynamoDbAsyncClient.putItem(any[PutItemRequest])).thenReturn(clientPutItemResponseInCf)

    val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val dynamoDbWriteItemRequest =
      DADynamoDbWriteItemRequest(
        "mockTableName",
        Map("mockAttribute" -> AttributeValue.builder().s("newMockItemValue").build())
      )
    val putItemResponseStatusCode = client.writeItem(dynamoDbWriteItemRequest).unsafeRunSync()
    putItemResponseStatusCode should be(200)
  }

  "writeItem" should "return an Exception if there is something wrong with the request/AWS" in {
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]

    when(mockDynamoDbAsyncClient.putItem(any[PutItemRequest])).thenThrow(
      ResourceNotFoundException.builder.message("Table name could not be found").build()
    )

    val client = DADynamoDBClient[IO](mockDynamoDbAsyncClient)

    val dynamoDbWriteItemRequest =
      DADynamoDbWriteItemRequest(
        "mockTableName",
        Map("mockAttribute" -> AttributeValue.builder().s("newMockItemValue").build())
      )
    val writeItemValueEx = intercept[Exception] {
      client.writeItem(dynamoDbWriteItemRequest).unsafeRunSync()
    }
    writeItemValueEx.getMessage should be("Table name could not be found")
  }

  private def createDynamoClientFromItems(itemMap: Map[String, String]): DADynamoDBClient[IO] = {
    val items = itemMap.map { case (k, v) =>
      val builder = AttributeValue.builder()
      val attributeValue = if Try(v.toInt).isSuccess then builder.n(v) else builder.s(v)
      k -> attributeValue.build()
    }.asJava
    val mockDynamoDbAsyncClient = mock[DynamoDbAsyncClient]
    val queryResponse = QueryResponse.builder
      .items(items)
      .build()
    val clientQueryResponseInCf: CompletableFuture[QueryResponse] = CompletableFuture.completedFuture(queryResponse)

    when(mockDynamoDbAsyncClient.query(any[QueryRequest])).thenReturn(clientQueryResponseInCf)
    DADynamoDBClient[IO](mockDynamoDbAsyncClient)
  }
}
