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

val fs2Client = DADynamoDBClient[IO]()

def getItem(tableName: String, primaryKeyName: String, primaryKeyValue: String, itemName: String): IO[AttributeValue] = {
  val primaryKeyAttribute = AttributeValue
    .builder()
    .s(primaryKeyValue) // '.s' for String type; methods for other types can be found here https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/dynamodb/model/AttributeValue.html#method-detail
    .build()
  
  val dynamoDbRequest = DynamoDbRequest(
    tableName,
    Map(primaryKeyName -> primaryKeyAttribute),
    itemName
  )
  fs2Client.getItem(dynamoDbRequest)
}

def updateItem(tableName: String, primaryKeyName: String, primaryKeyValue: String, itemName: String, newItemValue: String): IO[Int] = {
  val primaryKeyAttribute = AttributeValue
    .builder()
    .s(primaryKeyValue) // '.s' for String type; methods for other types can be found here https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/dynamodb/model/AttributeValue.html#method-detail
    .build()

  val dynamoDbRequest = DynamoDbRequest(
    tableName,
    Map(primaryKeyName -> primaryKeyAttribute),
    itemName,
    Some(AttributeValue.builder().s(newItemValue).build())
  )
  fs2Client.updateItem(dynamoDbRequest)
}
```
