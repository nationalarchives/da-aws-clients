# DynamoDB Client

The client constructor takes a `DynamoDbAsyncClient` object. The default `apply` method passes one in with sensible defaults, but you can pass your own in if necessary.

```scala
val dynamoDbAsyncClient: DynamoDbAsyncClient = ???
val clientWithCustom = new DADynamoDBClient(dynamoDbAsyncClient)

val clientWithDefault = DADynamoDBClient()
```

The client exposes six methods:

```scala
def writeItem(dynamoDbWriteRequest: DADynamoDbWriteItemRequest): F[Int]

def writeItems[T <: Product](tableName: String, items: List[T])(using format: DynamoFormat[T]): F[BatchWriteItemResponse]

def getItems[T <: Product, K <: Product](keys: List[K], tableName: String)(using returnFormat: DynamoFormat[T], keyFormat: DynamoFormat[K]): F[List[T]]

def updateAttributeValues(dynamoDbRequest: DynamoDbRequest): F[Int]

def queryItems[U <: Product](tableName: String, gsiName: String, requestCondition: RequestCondition)(implicit returnTypeFormat: DynamoFormat[U]): F[List[U]]

def deleteItems[T](tableName: String, primaryKeyAttributes: List[T])(using DynamoFormat[T]): F[List[BatchWriteItemResponse]]
```

1. The `writeItem` method takes a DADynamoDbWriteItemRequest Case Class
    ```scala
     case class DADynamoDbWriteItemRequest(tableName: String,
                                           attributeNamesAndValuesToWrite: Map[String, AttributeValue],
                                           conditionalExpression: Option[String]=None)
    ```
   consisting of:

- tableName - the DynamoDB table you want to make the edits in
- attributeNamesAndValuesToWrite - the key = attribute name you want to write; the value = value belonging that the name
- conditionalExpression - there is an option to add a conditional expression e.g. only write to the table when this condition is met
  - this should be in the form of a query
  - for rules and more about conditions, check the [AWS docs](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.OperatorsAndFunctions.html#Expressions.OperatorsAndFunctions.Syntax)


2. The `writeItems` method takes a table name of type `String` and a list of case classes of type `T`
    1. If, for example, the table has an attribute called "name", of type String, the case class would be `Items(name: String)`

   Even though the list that this method takes can be of any length, if the list contains more than 25 items, requests will be batched into groups of 25

3. The `getItems` method takes a table name and a list of case classes of type `K` (the case class for the partition key should match the partition key of the dynamo table.)
    1. If, for example, the table has a partition key called "id", of type String, the case class would be `PartitionKey(id: String)`
    2. If, for example, there is a composite key of "id", of type String and "index" of type number, the case class would be `PartitionKey(id: String, index: Long)`
    3. If, for example, there is a possibility that an attribute does not exist, the attribute should be an Option; the case class would be `PartitionKey(id: String, name: Option[String])`

   The method will return a list of items of type `T` (same type as the `items` passed into the `.writeItems` method)


4. The `updateAttributeValues` method takes a dynamoDbRequest Case Class:

    ```scala
      case class DynamoDbRequest(tableName: String,
                                 partitionKeyAndItsValue: Map[String, AttributeValue],
                                 attributeNamesAndValuesToUpdate: Map[String, Option[AttributeValue]])
    ```
   consisting of:

- tableName - the DynamoDB table you want to make the edits in
- partitionKeyAndItsValue - the partition key and the value to filter it down
    - you can use the .builder and pass the value to the 'type' method ('.s', '.b', '.n' etc)
    - *Warning* adding a value that isn't already present in the table will cause DynamoDb to add another row so watch out for misspellings
- attributeNamesAndValuesToUpdate - the key = attribute name you want the value of; the value = new value you want to take place of the current one
    - if you are using the .getAttributeValues method, you can set the value to None


5. The `queryItems` method takes a table name of type `String`, a global secondary index name and a Scanamo filter query. The query is converted to a Scanamo `RequestCondition` using implicits in the companion object.

6. The `deleteItems` method takes a table name of type `String` and a list of objects of type `T` These objects represent a primary key in Dynamo and the implicit `DynamoFormat[T]` is used to convert the case classes.

@@@ index

* [Zio](zio.md)
* [Fs2](fs2.md)

@@@
