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

val fs2Client = DADynamoDBClient[IO]()
case class PrimaryKey(id: String)
case class GetItemsResponse(attributeName: String, attributeName2: String)
case class PutItemsNestedRequest(attributeName2: String)
case class PutItemsRequest(attributeName: String, putItemsNestedRequest: PutItemsNestedRequest)

def getItemsExample(tableName: String, primaryKeyValue: String): IO[List[GetItemsResponse]] = {
  val primaryKey = PrimaryKey(primaryKeyValue) 
  fs2Client.getItems[GetItemsResponse, PrimaryKey](dynamoDbRequest)
}

def putItemsExample(tableName: String): IO[BatchWriteItemResponse] = {
  val putItemsRequest = List(PutItemsRequest("attributeValue", PutItemsNestedRequest("attributeValue2")))
  fs2Client.putItems(tableName, putItemsRequest)
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
```
