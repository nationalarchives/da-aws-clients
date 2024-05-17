# Use with Fs2

## Setup
You will need these dependencies:

@@dependency[sbt,Maven,Gradle] {
group="uk.gov.nationalarchives" artifact="da-dynamodb-client_2.13" version=$version$
}

## Examples

```scala
import cats.effect._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import uk.gov.nationalarchives.DADynamoDBClient
import uk.gov.nationalarchives.DADynamoDBClient.DynamoDbRequest
import org.scanamo.generic.auto._ //You can provide your own instance of DynamoFormat[T]
import org.scanamo.syntax._ //Used to construct the filters
import uk.gov.nationalarchives.DADynamoDBClient._ //Implicits to convert the query to a `RequestCondition`

val fs2Client = DADynamoDBClient[IO]()

case class PartitionKey(id: String)

case class GetItemsResponse(attributeName: String, attributeName2: String)

case class WriteItemsNestedRequest(attributeName2: String)

case class WriteItemsRequest(attributeName: String, writeItemsNestedRequest: WriteItemsNestedRequest)

case class DynamoTable(batchId: String, parentPath: String)

def getItemsExample(tableName: String, partitionKeyValue1: String, partitionKeyValue2: String): IO[List[GetItemsResponse]] = {
  val partitionKey1 = PartitionKey(partitionKeyValue1)
  val partitionKey2 = PartitionKey(partitionKeyValue2)
  val partitionKeys = List(partitionKey1, partitionKey2)
  fs2Client.getItems[GetItemsResponse, PartitionKey](partitionKeys, tableName)
}

def writeItemExample(tableName: String, attributeName: String, attributeName2: String, newAttributeValue: String,
                     newAttributeValue2: String): IO[Int] = {

  val dynamoDbWriteItemRequest = DADynamoDbWriteItemRequest(
    tableName,
    Map(
      attributeName -> Some(AttributeValue.builder().s(newAttributeValue).build()),
      attributeName2 -> Some(AttributeValue.builder().s(newAttributeValue2).build())
    )
    Some("attribute_not_exists(attributeName)")
  )
  fs2Client.writeItem(dynamoDbWriteItemRequest)
}

def writeItemsExample(tableName: String): IO[BatchWriteItemResponse] = {
  val WriteItemsRequest = List(WriteItemsRequest("attributeValue", WriteItemsNestedRequest("attributeValue2")))
  fs2Client.writeItems(tableName, WriteItemsRequest)
}

def updateAttributeValuesSetUpExample(tableName: String, partitionKeyName: String, partitionKeyValue: String, attributeName: String,
                                      attributeName2: String, newAttributeValue: String, newAttributeValue2: String): IO[Int] = {
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
    )
  )
  fs2Client.updateAttributeValues(dynamoDbRequest)
}

def queryItemsExample(tableName: String): Unit = {
  val andEqualsFilter = fs2Client.queryItems[DynamoTable](
    tableName,
    gsiName,
    "batchId" === "testBatchId" and "parentPath" === "/a/parent/path"
  )

  val lessThanFilter = fs2Client.queryItems[DynamoTable](
    tableName,
    gsiName,
    "numericAttribute" < 5
  )

  val lessThanEqualsOrFilter = fs2Client.queryItems[DynamoTable](
    tableName,
    gsiName,
    "numericAttribute" < 5 or "parentPath" === "/a/parent/path"
  )
}
```
