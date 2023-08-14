# DynamoDB Client

The client constructor takes a `DynamoDbAsyncClient` object. The default `apply` method passes one in with sensible defaults, but you can pass your own in if necessary.

```scala
val dynamoDbAsyncClient: DynamoDbAsyncClient = ???
val clientWithCustom = new DADynamoDBClient(dynamoDbAsyncClient)

val clientWithDefault = DADynamoDBClient()
```

The client exposes three methods:

```scala
def getItems[T <: Product, K <: Product](keys: List[K], tableName: String)(implicit returnFormat: DynamoFormat[T], keyFormat: DynamoFormat[K]): F[List[T]]

def updateAttributeValues(dynamoDbRequest: DynamoDbRequest): F[Int]

def putItems[T <: Product](tableName: String, items: List[T])(implicit format: DynamoFormat[T]): F[BatchWriteItemResponse]
```

The `updateAttributeValues` method takes a dynamoDbRequest Case Class:

```scala
  case class DynamoDbRequest(tableName: String,
                             primaryKeyAndItsValue: Map[String, AttributeValue],
                             attributeNamesAndValuesToUpdate: Map[String, Option[AttributeValue]])
```
consisting of:

- tableName - the DynamoDB table you want to make the edits in
- primaryKeyAndItsValue - the primary key and the value to filter it down
  - you can use the .builder and pass the value to the 'type' method ('.s', '.b', '.n' etc)
  - *Warning* adding a value that isn't already present in the table will cause DynamoDb to add another row so watch out for misspellings
- attributeNamesAndValuesToUpdate - the key = attribute name you want the value of; the value = new value you want to take place of the current one
  - if you are using the .getAttributeValues method, you can set the value to None
 
The `getItems` method takes a table name and a list of case classes of type `K`

The case class for the primary key should match the primary key of the dynamo table. 

If the table has a primary key of id of type string, the case class would be `PrimaryKey(id: String)`
If there is a composite key of id of type String and index of type number, the case class would be `PrimaryKey(id: String, index: Long)`

The method will return a list of items of type `T`

@@@ index

* [Zio](zio.md)
* [Fs2](fs2.md)

@@@
