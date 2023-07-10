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

  /** Gets an attribute from dynamoDB
    * @param dynamoDbRequest
    *   a case class with table name, primary key and value as well as the name of the attribute
    * @return
    *   The response from the get attribute call wrapped with F[_]
    */
  def getAttributeValue(dynamoDbRequest: DynamoDbRequest): F[AttributeValue] = {
    val getAttributeValueRequest = GetItemRequest
      .builder()
      .tableName(dynamoDbRequest.tableName)
      .key(dynamoDbRequest.primaryKeyAndItsValue asJava)
      .build()

    Async[F]
      .fromCompletableFuture {
        Async[F].pure(dynamoDBClient.getItem(getAttributeValueRequest))
      }
      .map { getAttributeValueResponse =>
        val attributeNamesAndValues: Map[String, AttributeValue] = (getAttributeValueResponse.item() asScala).toMap
        attributeNamesAndValues(dynamoDbRequest.attributeName)
      }
  }

  /** Updates an attribute in DynamoDb
    *
    * @param dynamoDbRequest
    *   a case class with table name, primary key and value, the name of the attribute to update and the value you want to
    *   update it with
    * @return
    *   The http status code from the updateAttributeValue call wrapped with F[_]
    */
  def updateAttributeValue(dynamoDbRequest: DynamoDbRequest): F[Int] = {
    val updateAttributeValueRequest = UpdateItemRequest
      .builder()
      .tableName(dynamoDbRequest.tableName)
      .key(dynamoDbRequest.primaryKeyAndItsValue asJava)
      .attributeUpdates(
        Map(
          dynamoDbRequest.attributeName ->
            AttributeValueUpdate
              .builder()
              .action(AttributeAction.PUT)
              .value(dynamoDbRequest.attributeValueToUpdate.get)
              .build()
        ) asJava
      )
      .build()

    Async[F]
      .fromCompletableFuture {
        Async[F].pure(dynamoDBClient.updateItem(updateAttributeValueRequest))
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
      attributeName: String,
      attributeValueToUpdate: Option[AttributeValue] = None
  )

  def apply[F[_]: Async]() = new DADynamoDBClient[F](dynamoDBClient)
}
