package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.{Encoder, Json}
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor4}
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient
import software.amazon.awssdk.services.eventbridge.model.{PutEventsRequest, PutEventsResponse}
import uk.gov.nationalarchives.DAEventBridgeClientTest._

import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters._

class DAEventBridgeClientTest extends AnyFlatSpec with TableDrivenPropertyChecks with MockitoSugar {

  private val putEventsResponse = CompletableFuture.completedFuture(PutEventsResponse.builder().build())

  val detailTable: TableFor4[String, String, TestDetail, String] = Table(
    ("source", "detailType", "detail", "expectedResponse"),
    ("sourceOne", "detailTypeOne", TestDetailOne("attributeOne"), "{\"attributeOne\":\"attributeOne\"}"),
    (
      "sourceTwo",
      "detailTypeTwo",
      TestDetailTwo("attributeOne", 1),
      "{\"attributeOne\":\"attributeOne\",\"attributeTwo\":1}"
    ),
    (
      "sourceThree",
      "detailTypeThree",
      TestDetailThree("attributeOne", 1, attributeThree = false),
      "{\"attributeOne\":\"attributeOne\",\"attributeTwo\":1,\"attributeThree\":false}"
    )
  )

  forAll(detailTable) { (source, detailType, detail, expectedResponse) =>
    "publishEventToEventBridge" should s"publish the correct values for source $source, detailType $detailType and detail" in {
      val asyncEventBridge = mock[EventBridgeAsyncClient]
      val eventRequestCaptor: ArgumentCaptor[PutEventsRequest] = ArgumentCaptor.forClass(classOf[PutEventsRequest])
      when(asyncEventBridge.putEvents(eventRequestCaptor.capture())).thenReturn(putEventsResponse)
      val client = new DAEventBridgeClient[IO](asyncEventBridge)

      client.publishEventToEventBridge(source, detailType, detail).unsafeRunSync()

      val entry = eventRequestCaptor.getValue.entries().asScala.head
      entry.source() should equal(source)
      entry.detailType() should equal(detailType)
      entry.detail() should equal(expectedResponse)
    }
  }

  "publishEventToEventBridge" should s"return an error if the AWS API call fails" in {
    val asyncEventBridge = mock[EventBridgeAsyncClient]
    when(asyncEventBridge.putEvents(any[PutEventsRequest]))
      .thenReturn(CompletableFuture.failedFuture(new Exception("Error contacting EventBridge")))
    val client = new DAEventBridgeClient[IO](asyncEventBridge)

    val message = intercept[Exception] {
      client.publishEventToEventBridge[TestDetail]("source", "detailType", TestDetailOne("test")).unsafeRunSync()
    }.getMessage
    message should equal("Error contacting EventBridge")
  }
}
object DAEventBridgeClientTest {
  trait TestDetail

  implicit val enc: Encoder[TestDetail] = {
    case TestDetailOne(attributeOne) => Json.obj(("attributeOne", Json.fromString(attributeOne)))
    case TestDetailThree(attributeOne, attributeTwo, attributeThree) =>
      Json.obj(
        ("attributeOne", Json.fromString(attributeOne)),
        ("attributeTwo", Json.fromInt(attributeTwo)),
        ("attributeThree", Json.fromBoolean(attributeThree))
      )
    case TestDetailTwo(attributeOne, attributeTwo) =>
      Json.obj(("attributeOne", Json.fromString(attributeOne)), ("attributeTwo", Json.fromInt(attributeTwo)))
  }

  case class TestDetailOne(attributeOne: String) extends TestDetail

  case class TestDetailTwo(attributeOne: String, attributeTwo: Int) extends TestDetail

  case class TestDetailThree(attributeOne: String, attributeTwo: Int, attributeThree: Boolean) extends TestDetail

}
