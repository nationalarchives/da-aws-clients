package uk.gov.nationalarchives

import cats.effect.Async
import cats.implicits._
import org.scanamo.DynamoReadError._
import org.scanamo._
import org.scanamo.query.{Condition => ScanamoCondition, _}
import org.scanamo.request.RequestCondition
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import uk.gov.nationalarchives.DADynamoDBClient._

import java.util
import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters._
import scala.language.{implicitConversions, postfixOps}

/** An DynamoDbAsync client. It is written generically so can be used for any effect which has an Async instance.
  * Requires an implicit instance of cats Async which is used to convert CompletableFuture to F
  *
  * @param dynamoDBClient
  *   An AWS DynamoDb Async client
  * @tparam F
  *   Type of the effect
  */
class DADynamoDBClient[F[_]: Async](dynamoDBClient: DynamoDbAsyncClient) {

  /** Writes the list of items of type T to Dynamo
    * @param tableName
    *   The name of the table
    * @param items
    *   A list of items to write to Dynamo
    * @param format
    *   An implicit scanamo formatter for type T
    * @tparam T
    *   The type of the items to be written to Dynamo
    * @return
    *   The BatchWriteItemResponse wrapped with F[_]
    */
  def writeItems[T <: Product](tableName: String, items: List[T])(implicit
      format: DynamoFormat[T]
  ): F[BatchWriteItemResponse] = {
    val valuesToWrite: List[WriteRequest] = items
      .map(format.write)
      .map(_.toAttributeValue.m())
      .map { itemMap =>
        val putRequest = PutRequest.builder().item(itemMap).build
        WriteRequest.builder().putRequest(putRequest).build
      }
    val req = BatchWriteItemRequest.builder().requestItems(Map(tableName -> valuesToWrite.asJava).asJava).build()
    dynamoDBClient.batchWriteItem(req).liftF
  }

  implicit class CompletableFutureUtils[T](completableFuture: CompletableFuture[T]) {
    def liftF: F[T] = Async[F].fromCompletableFuture(Async[F].pure(completableFuture))
  }

  private def validateAndConvertAttributeValuesList[T](
      attributeValuesList: util.List[util.Map[String, AttributeValue]]
  )(implicit
      format: DynamoFormat[T]
  ): F[List[T]] = {
    attributeValuesList.asScala.toList.map { res =>
      DynamoValue.fromMap {
        res.asScala.toMap.map { case (name, av) =>
          name -> DynamoValue.fromAttributeValue(av)
        }
      }
    } map { dynamoValue =>
      format.read(dynamoValue).left.map(err => new Exception(err.show))
    } map Async[F].fromEither
  }.sequence

  /** Returns a list of items of type T which match the list of primary keys
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
  def getItems[T <: Product, K <: Product](primaryKeys: List[K], tableName: String)(implicit
      returnFormat: DynamoFormat[T],
      keyFormat: DynamoFormat[K]
  ): F[List[T]] = {
    val primaryKeysAsAttributeValues = primaryKeys
      .map(keyFormat.write)
      .map(_.toAttributeValue.m())
      .asJava

    val keysAndAttributes = KeysAndAttributes.builder
      .keys(primaryKeysAsAttributeValues)
      .build
    val batchGetItemRequest = BatchGetItemRequest.builder
      .requestItems(Map(tableName -> keysAndAttributes).asJava)
      .build

    for {
      batchResponse <- dynamoDBClient.batchGetItem(batchGetItemRequest).liftF
      result <- validateAndConvertAttributeValuesList[T](batchResponse.responses.get(tableName))
    } yield result
  }

  /** Updates an attribute in DynamoDb
    *
    * @param dynamoDbRequest
    *   a case class with table name, primary key and value, the name of the attribute to update and the value you want
    *   to update it with
    * @return
    *   The http status code from the updateAttributeValues call wrapped with F[_]
    */
  def updateAttributeValues(dynamoDbRequest: DADynamoDbRequest): F[Int] = {
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

    dynamoDBClient
      .updateItem(updateAttributeValueRequest)
      .liftF
      .map(_.sdkHttpResponse().statusCode())
  }

  /** @param tableName
    *   The name of the table
    * @param gsiName
    *   The name of the global secondary index
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
  def queryItems[U <: Product](tableName: String, gsiName: String, requestCondition: RequestCondition)(implicit
      returnTypeFormat: DynamoFormat[U]
  ): F[List[U]] = {
    val expressionAttributeValues =
      requestCondition.dynamoValues.flatMap(_.toExpressionAttributeValues).getOrElse(util.Collections.emptyMap())

    val queryRequest = QueryRequest.builder
      .tableName(tableName)
      .indexName(gsiName)
      .keyConditionExpression(requestCondition.expression)
      .expressionAttributeNames(requestCondition.attributeNames.asJava)
      .expressionAttributeValues(expressionAttributeValues)
      .build
    dynamoDBClient
      .query(queryRequest)
      .liftF
      .flatMap(res => validateAndConvertAttributeValuesList(res.items()))
  }
}
object DADynamoDBClient {

  implicit def conditionToRequestCondition[C: ConditionExpression](condition: C): RequestCondition = ScanamoCondition(
    condition
  ).apply.runEmptyA.value
  implicit def queryToRequestCondition(query: Query[_]): RequestCondition = query.apply
  implicit def keyEqualsToRequestCondition[T: UniqueKeyCondition, U: UniqueKeyCondition](
      andEqualsCondition: AndEqualsCondition[T, U]
  )(implicit qkc: QueryableKeyCondition[AndEqualsCondition[T, U]]): RequestCondition = {
    Query[AndEqualsCondition[T, U]](andEqualsCondition)
  }
  private val httpClient: SdkAsyncHttpClient = NettyNioAsyncHttpClient.builder().build()
  private val dynamoDBClient: DynamoDbAsyncClient = DynamoDbAsyncClient
    .builder()
    .httpClient(httpClient)
    .region(Region.EU_WEST_2)
    .credentialsProvider(DefaultCredentialsProvider.create())
    .build()

  case class DADynamoDbRequest(
      tableName: String,
      primaryKeyAndItsValue: Map[String, AttributeValue],
      attributeNamesAndValuesToUpdate: Map[String, Option[AttributeValue]]
  )

  def apply[F[_]: Async]() = new DADynamoDBClient[F](dynamoDBClient)
}
