package uk.gov.nationalarchives

import cats.effect.Async
import cats.implicits._
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import uk.gov.nationalarchives.DADynamoDBClient.DynamoDbRequest

import scala.jdk.CollectionConverters.{MapHasAsJava, MapHasAsScala}
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

  /** Gets an item from dynamoDB
    * @param dynamoDbRequest
    *   a case class with table name, primary key and value as well as the name of the item
    * @return
    *   The response from the get item call wrapped with F[_]
    */
  def getItem(dynamoDbRequest: DynamoDbRequest): F[AttributeValue] = {
    val getItemRequest = GetItemRequest
      .builder()
      .tableName(dynamoDbRequest.tableName)
      .key(dynamoDbRequest.primaryKeyAndItsValue asJava)
      .build()

    Async[F]
      .fromCompletableFuture {
        Async[F].pure(dynamoDBClient.getItem(getItemRequest))
      }
      .map { getItemResponse =>
        val attributeNamesAndValues: Map[String, AttributeValue] = (getItemResponse.item() asScala).toMap
        attributeNamesAndValues(dynamoDbRequest.itemName)
      }
  }

  /** Updates an item in DynamoDb
    *
    * @param dynamoDbRequest
    *   a case class with table name, primary key and value, the name of the item to update and the value you want to
    *   update it with
    * @return
    *   The http status code from the updateItem call wrapped with F[_]
    */
  def updateItem(dynamoDbRequest: DynamoDbRequest): F[Int] = {
    val updateItemRequest = UpdateItemRequest
      .builder()
      .tableName(dynamoDbRequest.tableName)
      .key(dynamoDbRequest.primaryKeyAndItsValue asJava)
      .attributeUpdates(
        Map(
          dynamoDbRequest.itemName ->
            AttributeValueUpdate
              .builder()
              .action(AttributeAction.PUT)
              .value(dynamoDbRequest.itemValueToUpdate.get)
              .build()
        ) asJava
      )
      .build()

    Async[F]
      .fromCompletableFuture {
        Async[F].pure(dynamoDBClient.updateItem(updateItemRequest))
      }
      .map(_.sdkHttpResponse().statusCode())
  }
}
object DADynamoDBClient {
  private val dynamoDBClient: DynamoDbAsyncClient = DynamoDbAsyncClient
    .builder()
    .region(Region.EU_WEST_2)
    .credentialsProvider(DefaultCredentialsProvider.create())
    .build()

  case class DynamoDbRequest(
      tableName: String,
      primaryKeyAndItsValue: Map[String, AttributeValue],
      itemName: String,
      itemValueToUpdate: Option[AttributeValue] = None
  )

  def apply[F[_]: Async]() = new DADynamoDBClient[F](dynamoDBClient)
}
