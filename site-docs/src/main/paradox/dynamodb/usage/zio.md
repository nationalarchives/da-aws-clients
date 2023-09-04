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

case class PrimaryKey(id: String)

case class GetItemsResponse(attributeName: String, attributeName2: String)

case class WriteItemsNestedRequest(attributeName2: String)

case class WriteItemsRequest(attributeName: String, writeItemsNestedRequest: WriteItemsNestedRequest)

case class DynamoTable(batchId: String, parentPath: String)

def getItemsExample(tableName: String, primaryKeyValue1: String, primaryKeyValue2: String): Task[List[GetItemsResponse]] = {
  val primaryKey1 = PrimaryKey(primaryKeyValue1)
  val primaryKey2 = PrimaryKey(primaryKeyValue2)
  val primaryKeys = List(primaryKey1, primaryKey2)
  fs2Client.getItems[GetItemsResponse, PrimaryKey](primaryKeys, tableName)
}

def writeItemsExample(tableName: String): Task[BatchWriteItemResponse] = {
  val writeItemsRequest = List(WriteItemsRequest("attributeValue", WriteItemsNestedRequest("attributeValue2")))
  fs2Client.writeItems(tableName, writeItemsRequest)
}

def updateAttributeValueSetUpExample(tableName: String, primaryKeyName: String, primaryKeyValue: String, attributeName: String,
                                     attributeName2: String, newAttributeValue: String, newAttributeValue2: String): Task[Int] = {
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

```

