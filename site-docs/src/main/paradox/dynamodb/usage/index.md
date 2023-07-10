# DynamoDB Client

The client constructor takes a `DynamoDbAsyncClient` object. The default `apply` method passes one in with sensible defaults, but you can pass your own in if necessary.

```scala
val dynamoDbAsyncClient: DynamoDbAsyncClient = ???
val clientWithCustom = new DADynamoDBClient(dynamoDbAsyncClient)

val clientWithDefault = DADynamoDBClient()
```

The client exposes two methods:

```scala
def getAttributeValue(dynamoDbRequest: DynamoDbRequest): F[AttributeValue]

def updateAttributeValue(dynamoDbRequest: DynamoDbRequest): F[Int]
```

The method takes a dynamoDbRequest Case Class:

```scala
  case class DynamoDbRequest(tableName: String,
                             primaryKeyAndItsValue: Map[String, AttributeValue],
                             attributeName: String,
                             updateAttributeValue: Option[AttributeValue]=None)
```
consisting of:
- tableName - the DynamoDB table you want to make the edits in
- primaryKeyAndItsValue - the primary key and the value to filter it down
  - you can use the .builder and pass the value to the 'type' method ('.s', '.b', '.n' etc)
  - *Warning* adding a value that isn't already present in the table will cause DynamoDb to add another row so watch out for misspellings
- attributeName - the attribute name you want the value of
- attributeValueToUpdate - the new value you want to take place of the current one
  - should only be used for the ".updateAttributeValue" method; is None by default

@@@ index

* [Zio](zio.md)
* [Fs2](fs2.md)

@@@
