package uk.gov.nationalarchives

import cats.effect.Async
import cats.implicits._
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
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
  def getAttributeValues(dynamoDbRequest: DynamoDbRequest): F[Map[String, AttributeValue]] = {
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
        dynamoDbRequest.attributeNamesAndValuesToUpdate.map { case (attributeName, _) =>
          attributeNamesAndValues(attributeName)
        } // verify that all requested attributes were returned
        attributeNamesAndValues.filter { case (name, _) =>
          dynamoDbRequest.attributeNamesAndValuesToUpdate.keys.toList.contains(name)
        }
      }
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
