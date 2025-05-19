# Use with Zio

## Setup
You will need these dependencies:

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-dynamodb-client_2.13" version=$version$
group3="dev.zio" artifact3="zio-interop-cats_2.13" version3="23.0.0.5"
}

`zio-interop-cats` is needed to allow us to use the ZIO Task with the cats type classes


## Examples

```scala
import zio.stream.Stream
import zio._
import zio.interop.catz._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import uk.gov.nationalarchives.DADynamoDBClient
import uk.gov.nationalarchives.DADynamoDBClient.DynamoDbRequest
import org.scanamo.generic.auto._
import org.scanamo.syntax._ //Used to construct the filters
import uk.gov.nationalarchives.DADynamoDBClient._ //Implicits to convert the query to a `RequestCondition`

val zioClient = DADynamoDBClient[Task]()

case class PartitionKey(id: String)

case class GetItemsResponse(attributeName: String, attributeName2: String)

case class WriteItemsNestedRequest(attributeName2: String)

case class WriteItemsRequest(attributeName: String, writeItemsNestedRequest: WriteItemsNestedRequest)

case class DynamoTable(batchId: String, parentPath: String)

def getItemsExample(tableName: String, partitionKeyValue1: String, partitionKeyValue2: String): Task[List[GetItemsResponse]] = {
  val partitionKey1 = PartitionKey(partitionKeyValue1)
  val partitionKey2 = PartitionKey(partitionKeyValue2)
  val partitionKeys = List(partitionKey1, partitionKey2)
  zioClient.getItems[GetItemsResponse, PartitionKey](partitionKeys, tableName)
}

def writeItemExample(tableName: String, attributeName: String, attributeName2: String, newAttributeValue: String,
                     newAttributeValue2: String): Task[Int] = {

  val dynamoDbWriteItemRequest = DADynamoDbWriteItemRequest(
    tableName,
    Map(
      attributeName -> Some(AttributeValue.builder().s(newAttributeValue).build()),
      attributeName2 -> Some(AttributeValue.builder().s(newAttributeValue2).build())
    )
      Some("attribute_not_exists(attributeName)")
  )
  zioClient.writeItem(dynamoDbWriteItemRequest)
}

def writeItemsExample(tableName: String): Task[BatchWriteItemResponse] = {
  val writeItemsRequest = List(WriteItemsRequest("attributeValue", WriteItemsNestedRequest("attributeValue2")))
  zioClient.writeItems(tableName, writeItemsRequest)
}

def updateAttributeValueSetUpExample(tableName: String, partitionKeyName: String, partitionKeyValue: String, attributeName: String,
                                     attributeName2: String, newAttributeValue: String, newAttributeValue2: String): Task[Int] = {
  val partitionKeyAttribute = AttributeValue
    .builder()
    .s(partitionKeyValue) // '.s' for String type; methods for other types can be found here https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/dynamodb/model/AttributeValue.html#method-detail
    .build()

  val dynamoDbRequest = DynamoDbRequest(
    tableName,
    Map(partitionKeyName -> partitionKeyAttribute),
    Map(
      attributeName -> Some(AttributeValue.builder().s(newAttributeValue).build()),
      attributeName2 -> Some(AttributeValue.builder().s(newAttributeValue2).build())
    ),
    Some("attribute_not_exists(attributeName)")
  )
  zioClient.updateAttributeValues(dynamoDbRequest)
}

def queryItemsExample(tableName: String, gsiName: String): Unit = {
  val andEqualsFilter = zioClient.queryItems[DynamoTable](
    tableName,
    gsiName,
    "batchId" === "testBatchId" and "parentPath" === "/a/parent/path"
  )

  val lessThanFilter = zioClient.queryItems[DynamoTable](
    tableName,
    gsiName,
    "numericAttribute" < 5
  )

  val lessThanEqualsOrFilter = zioClient.queryItems[DynamoTable](
    tableName,
    gsiName,
    "numericAttribute" < 5 or "parentPath" === "/a/parent/path"
  )
}

def deleteItemsExample(tableName: String): Task[BatchWriteItemResponse] = {
  val partitionKey1 = PartitionKey(partitionKeyValue1)
  val partitionKey2 = PartitionKey(partitionKeyValue2)
  zioClient.deleteItems(tableName, List(partitionKey1, partitionKey2))
}

```

