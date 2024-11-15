package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import io.circe.generic.auto.*
import org.scalatest.matchers.should.Matchers.*
import software.amazon.awssdk.services.ssm.SsmAsyncClient
import software.amazon.awssdk.services.ssm.model.{GetParameterRequest, GetParameterResponse, Parameter, ParameterNotFoundException}

import java.util.concurrent.CompletableFuture
import scala.collection.mutable.ListBuffer

class DASSMClientTest extends AnyFlatSpec:
  case class Result(value: String)

  case class TestParameter(name: String, encrypted: Boolean, value: String)
  case class Errors(getParameter: Boolean = false)

  def createClient(initial: ListBuffer[TestParameter], errors: Option[Errors] = None): SsmAsyncClient = new SsmAsyncClient {
    override def serviceName(): String = "test"

    override def close(): Unit = ()

    override def getParameter(getParameterRequest: GetParameterRequest): CompletableFuture[GetParameterResponse] =
      if errors.exists(_.getParameter) then throw new Exception("Error getting parameter") else
      CompletableFuture.completedFuture {
        initial.find(i => i.name == getParameterRequest.name)
          .map { parameter =>
            if parameter.encrypted != getParameterRequest.withDecryption() then
              throw new Exception("Error decoding encrypted parameter")
            GetParameterResponse.builder.parameter(Parameter.builder.value(parameter.value).build).build
          }.getOrElse(throw ParameterNotFoundException.builder.message(s"Parameter ${getParameterRequest.name} not found").build)
      }
  }

  "DASSMClient" should "return a decoded unencrypted parameter" in {
    val ssmAsyncClient = createClient(ListBuffer(TestParameter("testName", false, """{"value": "testValue"}""")))

    val client = DASSMClient[IO](ssmAsyncClient)

    val result = client.getParameter[Result]("testName").unsafeRunSync()

    result.value should equal("testValue")
  }

  "DASSMClient" should "return a decoded encrypted parameter" in {
    val ssmAsyncClient = createClient(ListBuffer(TestParameter("testName", true, """{"value": "testValue"}""")))

    val client = DASSMClient[IO](ssmAsyncClient)

    val result = client.getParameter[Result]("testName", withDecryption = true).unsafeRunSync()

    result.value should equal("testValue")
  }

  "DASSMClient" should "return an error if the parameter is encrypted and withDecryption is false" in {
    val ssmAsyncClient = createClient(ListBuffer(TestParameter("testName", true, """{"value": "testValue"}""")))

    val client = DASSMClient[IO](ssmAsyncClient)

    val ex = intercept[Exception] {
      client.getParameter[Result]("testName").unsafeRunSync()
    }

    ex.getMessage should equal("Error decoding encrypted parameter")
  }

  "DASSMClient" should "return an error if the parameter is not found" in {
    val ssmAsyncClient = createClient(ListBuffer(TestParameter("testName", true, """{"value": "testValue"}""")))

    val client = DASSMClient[IO](ssmAsyncClient)

    val ex = intercept[ParameterNotFoundException] {
      client.getParameter[Result]("invalidTestName").unsafeRunSync()
    }

    ex.getMessage should equal("Parameter invalidTestName not found")
  }

  "DASSMClient" should "return an error if there is an error from the SDK" in {
    val ssmAsyncClient = createClient(ListBuffer[TestParameter](), errors = Option(Errors(getParameter = true)))

    val client = DASSMClient[IO](ssmAsyncClient)

    val ex = intercept[Exception] {
      client.getParameter[Result]("testName").unsafeRunSync()
    }

    ex.getMessage should equal("Error getting parameter")
  }