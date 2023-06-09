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

val zioClient = DADynamoDBClient[Task]()
def getAttributeValueSetUpExample(tableName: String, primaryKeyName: String, primaryKeyValue: String, attributeName: String, attributeName2: String): IO[Map[String, AttributeValue]] = {
  val primaryKeyAttribute = AttributeValue
    .builder()
    .s(primaryKeyValue) // '.s' for String type; methods for other types can be found here https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/dynamodb/model/AttributeValue.html#method-detail
    .build()

  val dynamoDbRequest = DynamoDbRequest(
    tableName,
    Map(primaryKeyName -> primaryKeyAttribute),
    Map("attributeName" -> None, "attributeName2" -> None)
  )
  zioClient.getAttributeValues(dynamoDbRequest)
}

def updateAttributeValueSetUpExample(tableName: String, primaryKeyName: String, primaryKeyValue: String, attributeName: String,
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
  zioClient.updateAttributeValues(dynamoDbRequest)
}

```

