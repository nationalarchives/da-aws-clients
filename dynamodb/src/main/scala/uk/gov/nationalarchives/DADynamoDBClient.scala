package uk.gov.nationalarchives

import cats.effect.Async
import cats.implicits._
import org.scanamo.DynamoReadError._
import org.scanamo._
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import uk.gov.nationalarchives.DADynamoDBClient.DynamoDbRequest

import scala.jdk.CollectionConverters._
import scala.language.postfixOps

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
    Async[F]
      .fromCompletableFuture {
        Async[F].pure(dynamoDBClient.batchWriteItem(req))
      }
  }

  private def validateAndConvertBatchGetItemResponse[T](batchGetItemResponse: BatchGetItemResponse, tableName: String)(
      implicit format: DynamoFormat[T]
  ): F[List[T]] = {
    batchGetItemResponse.responses.get(tableName).asScala.toList.map { res =>
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
      batchResponse <- Async[F]
        .fromCompletableFuture {
          Async[F].pure(dynamoDBClient.batchGetItem(batchGetItemRequest))
        }
      result <- validateAndConvertBatchGetItemResponse[T](batchResponse, tableName)
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
  def updateAttributeValues(dynamoDbRequest: DynamoDbRequest): F[Int] = {
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

    Async[F]
      .fromCompletableFuture {
        Async[F].pure(dynamoDBClient.updateItem(updateAttributeValueRequest))
      }
      .map(_.sdkHttpResponse().statusCode())
  }
}
object DADynamoDBClient {
  private val httpClient: SdkAsyncHttpClient = NettyNioAsyncHttpClient.builder().build()
  private val dynamoDBClient: DynamoDbAsyncClient = DynamoDbAsyncClient
    .builder()
    .httpClient(httpClient)
    .region(Region.EU_WEST_2)
    .credentialsProvider(DefaultCredentialsProvider.create())
    .build()

  case class DynamoDbRequest(
      tableName: String,
      primaryKeyAndItsValue: Map[String, AttributeValue],
      attributeNamesAndValuesToUpdate: Map[String, Option[AttributeValue]]
  )

  def apply[F[_]: Async]() = new DADynamoDBClient[F](dynamoDBClient)
}
