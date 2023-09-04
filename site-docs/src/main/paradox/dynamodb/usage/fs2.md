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

case class PrimaryKey(id: String)

case class GetItemsResponse(attributeName: String, attributeName2: String)

case class WriteItemsNestedRequest(attributeName2: String)

case class WriteItemsRequest(attributeName: String, writeItemsNestedRequest: WriteItemsNestedRequest)

case class DynamoTable(batchId: String, parentPath: String)

def getItemsExample(tableName: String, primaryKeyValue1: String, primaryKeyValue2: String): IO[List[GetItemsResponse]] = {
  val primaryKey1 = PrimaryKey(primaryKeyValue1)
  val primaryKey2 = PrimaryKey(primaryKeyValue2)
  val primaryKeys = List(primaryKey1, primaryKey2)
  fs2Client.getItems[GetItemsResponse, PrimaryKey](primaryKeys, tableName)
}

def writeItemsExample(tableName: String): IO[BatchWriteItemResponse] = {
  val WriteItemsRequest = List(WriteItemsRequest("attributeValue", WriteItemsNestedRequest("attributeValue2")))
  fs2Client.writeItems(tableName, WriteItemsRequest)
}

def updateAttributeValuesSetUpExample(tableName: String, primaryKeyName: String, primaryKeyValue: String, attributeName: String,
                                      attributeName2: String, newAttributeValue: String, newAttributeValue2: String): IO[Int] = {
  val primaryKeyAttribute = AttributeValue
    .builder()
    .s(primaryKeyValue) // '.s' for String type; methods for other types can be found here https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/dynamodb/model/AttributeValue.html#method-detail
    .build()

  val dynamoDbRequest = DynamoDbRequest(
    tableName,
    Map(primaryKeyName -> primaryKeyAttribute),
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
