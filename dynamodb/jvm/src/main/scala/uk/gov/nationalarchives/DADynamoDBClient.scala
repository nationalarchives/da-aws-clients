package uk.gov.nationalarchives

import cats.effect.Async
import cats.implicits.*
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.{DynamoDbAsyncClient, DynamoDbClient}
import software.amazon.awssdk.services.dynamodb.model.{KeysAndAttributes, *}
import uk.gov.nationalarchives.DADynamoDBClient.*
import dynosaur.*
import software.amazon.awssdk.http.apache.ApacheHttpClient

import java.util
import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*
import scala.language.{implicitConversions, postfixOps}

/** An DynamoDbAsync client. It is written generically so can be used for any effect which has an Async instance.
  * Requires an implicit instance of cats Async which is used to convert CompletableFuture to F
  *
  * @tparam F
  *   Type of the effect
  */
trait DADynamoDBClient[F[_]: Async]:

  /** Takes a list of primary key attributes of type T and deletes the corresponding items from Dynamo. Unprocessed
    * items will be retried
    *
    * @param tableName
    *   The name of the table
    * @param primaryKeyAttributes
    *   A list of primary keys to delete from Dynamo (list can be any length; requests will be batched into groups of
    *   25)
    * @param format
    *   An implicit scanamo formatter for type T
    * @tparam T
    *   The type of the primary keys to be deleted from Dynamo
    * @return
    *   The List of BatchWriteItemResponse wrapped with F[_]
    */
  def deleteItems[T](tableName: String, primaryKeyAttributes: List[T])(using
      Schema[T]
  ): F[List[BatchWriteItemResponse]]

  /** Writes a single item to DynamoDb
    *
    * @param dynamoDbWriteRequest
    *   a case class with table name, the attributes and values you want to write and the (optional) conditional
    *   expression you want to apply
    * @return
    *   The http status code from the writeItem call wrapped with F[_]
    */
  def writeItem(dynamoDbWriteRequest: DADynamoDbWriteItemRequest): F[Int]

  /** Writes the list of items of type T to Dynamo. Unprocessed items will be retried
    *
    * @param tableName
    *   The name of the table
    * @param items
    *   A list of items to write to Dynamo (list can be any length; requests will be batched into groups of 25)
    * @param format
    *   An implicit scanamo formatter for type T
    * @tparam T
    *   The type of the items to be written to Dynamo
    * @return
    *   The List of BatchWriteItemResponse wrapped with F[_]
    */
  def writeItems[T](tableName: String, items: List[T])(using
      format: Schema[T]
  ): F[List[BatchWriteItemResponse]]

  /** @param tableName
    *   The name of the table
    * @param potentialGsiName
    *   The optional name of the global secondary index
    * @param requestCondition
    *   This is not passed in directly. You construct either a Query[_] or a ConditionExpression instance. This is then
    *   converted to RequestCondition by the implicits in the companion object.
    * @param returnTypeFormat
    *   An implicit scanamo formatter for return type U
    * @tparam U
    *   The return type for the function
    * @return
    *   A list of type U wrapped in the F effect
    */
  def queryItems[U](tableName: String, requestCondition: RequestCondition, potentialGsiName: Option[String] = None)(
      using returnTypeFormat: Schema[U]
  ): F[List[U]]

  /** Returns a list of items of type T which match the list of primary keys
    *
    * @param primaryKeys
    *   A list of primary keys of type K. The case class of type K should match the table primary key.
    * @param tableName
    *   The name of the table
    * @param returnFormat
    *   An implicit scanamo formatter for type T
    * @param keyFormat
    *   An implicit scanamo formatter for type K
    * @tparam T
    *   The type of the return case class
    * @tparam K
    *   The type of the primary key
    * @return
    *   A list of case classes of type T which the values populated from Dynamo, wrapped with F[_]
    */
  def getItems[T, K](primaryKeys: List[K], tableName: String)(using
      returnFormat: Schema[T],
      keyFormat: Schema[K]
  ): F[List[T]]

  /** Updates an attribute in DynamoDb
    *
    * @param dynamoDbRequest
    *   a case class with table name, primary key and value, the name of the attribute to update and the value you want
    *   to update it with
    * @return
    *   The http status code from the updateAttributeValues call wrapped with F[_]
    */
  def updateAttributeValues(dynamoDbRequest: DADynamoDbRequest): F[Int]

object DADynamoDBClient:

  case class RequestCondition(
                               expression: String,
                               attributeNames: Map[String, String],
                               attributeValues: Map[String, AttributeValue]
                             )

  case class DADynamoDbRequest(
      tableName: String,
      primaryKeyAndItsValue: Map[String, AttributeValue],
      attributeNamesAndValuesToUpdate: Map[String, Option[AttributeValue]]
  )

  case class DADynamoDbWriteItemRequest(
      tableName: String,
      attributeNamesAndValuesToWrite: Map[String, AttributeValue],
      conditionalExpression: Option[String] = None
  )

  private lazy val dynamoDBClient: DynamoDbClient = DynamoDbClient
    .builder()
    .httpClient(ApacheHttpClient.builder().build())
    .region(Region.EU_WEST_2)
    .credentialsProvider(DefaultCredentialsProvider.create())
    .build()

  def apply[F[_]: Async](dynamoDBClient: DynamoDbClient = dynamoDBClient): DADynamoDBClient[F] =
    new DADynamoDBClient[F] {
      extension [T](completableFuture: CompletableFuture[T])
        private def liftF: F[T] = Async[F].fromFuture(Async[F].pure(completableFuture.asScala))

      override def deleteItems[T](tableName: String, primaryKeyAttributes: List[T])(using
          Schema[T]
      ): F[List[BatchWriteItemResponse]] =
        writeOrDeleteItems(
          tableName,
          primaryKeyAttributes,
          primaryKeyAttributesMap => {
            val deleteRequest = DeleteRequest.builder().key(primaryKeyAttributesMap).build
            WriteRequest.builder().deleteRequest(deleteRequest).build
          }
        )

      override def writeItem(dynamoDbWriteRequest: DADynamoDbWriteItemRequest): F[Int] =
        val putItemRequestBuilder = PutItemRequest
          .builder()
          .tableName(dynamoDbWriteRequest.tableName)
          .item(dynamoDbWriteRequest.attributeNamesAndValuesToWrite.asJava)

        val putItemRequest: PutItemRequest =
          dynamoDbWriteRequest.conditionalExpression
            .map(putItemRequestBuilder.conditionExpression)
            .getOrElse(putItemRequestBuilder)
            .build

        Async[F].blocking(dynamoDBClient.putItem(putItemRequest))
          .map(_.sdkHttpResponse().statusCode())

      override def writeItems[T](tableName: String, items: List[T])(using
          format: Schema[T]
      ): F[List[BatchWriteItemResponse]] =
        writeOrDeleteItems(
          tableName,
          items,
          itemMap => {
            val putRequest = PutRequest.builder().item(itemMap).build
            WriteRequest.builder().putRequest(putRequest).build
          }
        )

      override def getItems[T, K](primaryKeys: List[K], tableName: String)(using
          returnFormat: Schema[T],
          keyFormat: Schema[K]
      ): F[List[T]] =
        val primaryKeysAsAttributeValues = primaryKeys
          .map(keyFormat.write)
          .flatMap(_.toOption)
          .map(_.value.m())
          .asJava

        val keysAndAttributes = KeysAndAttributes.builder
          .keys(primaryKeysAsAttributeValues)
          .build
        val batchGetItemRequest = BatchGetItemRequest.builder
          .requestItems(Map(tableName -> keysAndAttributes).asJava)
          .build

        for
          batchResponse <- Async[F].blocking(dynamoDBClient.batchGetItem(batchGetItemRequest))
          result <- validateAndConvertAttributeValuesList[T](batchResponse.responses.get(tableName))
        yield result

      override def updateAttributeValues(dynamoDbRequest: DADynamoDbRequest): F[Int] =
        val attributeValueUpdates =
          dynamoDbRequest.attributeNamesAndValuesToUpdate.map { case (name, attributeValue) =>
            name -> AttributeValueUpdate
              .builder()
              .action(AttributeAction.PUT)
              .value(attributeValue.get)
              .build()
          } asJava

        val updateAttributeValueRequest = UpdateItemRequest
          .builder()
          .tableName(dynamoDbRequest.tableName)
          .key(dynamoDbRequest.primaryKeyAndItsValue asJava)
          .attributeUpdates(attributeValueUpdates)
          .build()

        Async[F].blocking {
          dynamoDBClient
            .updateItem(updateAttributeValueRequest)
        }.map(_.sdkHttpResponse().statusCode())

      def queryItems[U](tableName: String, requestCondition: RequestCondition, potentialGsiName: Option[String] = None)(
          using returnTypeFormat: Schema[U]
      ): F[List[U]] =

        val builder = QueryRequest.builder
          .tableName(tableName)
          .keyConditionExpression(requestCondition.expression)
          .expressionAttributeNames(requestCondition.attributeNames.asJava)
          .expressionAttributeValues(requestCondition.attributeValues.asJava)

        val queryRequest = potentialGsiName.map(gsi => builder.indexName(gsi)).getOrElse(builder).build
        Async[F].blocking {
            dynamoDBClient
              .query(queryRequest)
          }.flatMap(res => validateAndConvertAttributeValuesList(res.items()))


      private def writeOrDeleteItems[T](
          tableName: String,
          items: List[T],
          mapToWriteRequest: util.Map[String, AttributeValue] => WriteRequest
      )(using
          format: Schema[T]
      ) = {
        items
          .grouped(25)
          .toList
          .map { batchedItems =>
            val valuesToWrite: List[WriteRequest] = batchedItems
              .map(format.write)
              .flatMap(_.toOption)
              .map(_.value.m())
              .map(mapToWriteRequest)
            Async[F].tailRecM(valuesToWrite) { reqs =>
              val req = BatchWriteItemRequest.builder().requestItems(Map(tableName -> reqs.asJava).asJava).build()
              Async[F].blocking(dynamoDBClient.batchWriteItem(req)).map {
                case resUnprocessed
                    if resUnprocessed.hasUnprocessedItems && resUnprocessed.unprocessedItems.containsKey(tableName) =>
                  Left(resUnprocessed.unprocessedItems.get(tableName).asScala.toList)
                case res => Right(res)
              }
            }
          }
          .sequence
      }

      private def validateAndConvertAttributeValuesList[T](
          attributeValuesList: util.List[util.Map[String, AttributeValue]]
      )(using format: Schema[T]): F[List[T]] = {
        attributeValuesList.asScala.toList.map(DynamoValue.attributeMap)
          .map { dynamoValue =>
          format.read(dynamoValue).left.map(err => new RuntimeException(err.message))
        } map Async[F].fromEither
      }.sequence
    }
