package uk.gov.nationalarchives

import cats.effect.{Async, Resource}
import cats.effect.std.{Dispatcher, Queue}
import com.amazon.sqs.javamessaging.{ProviderConfiguration, SQSConnectionFactory, SQSSession}
import com.amazon.sqs.javamessaging.message.SQSTextMessage
import io.circe.Decoder
import cats.implicits.*
import io.circe.parser.decode
import jakarta.jms.{Message, MessageListener}
import software.amazon.awssdk.http.apache.{ApacheHttpClient, ProxyConfiguration}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import uk.gov.nationalarchives.DASQSStreamClient.StreamMessageResponse

import java.net.URI

trait DASQSStreamClient[F[_]]:
  def receiveMessageStream[T](queueUrl: String)(using Decoder[T]): Resource[F, Queue[F, StreamMessageResponse[T]]]

object DASQSStreamClient:

  case class StreamMessageResponse[T](sqsTextMessage: SQSTextMessage, message: T)

  def sqsClient(potentialProxyConfig: Option[ProxyConfiguration] = None): SqsClient =  {
    val httpClient = potentialProxyConfig
      .map(proxyConfig => ApacheHttpClient.builder.proxyConfiguration(proxyConfig).build)
      .getOrElse(ApacheHttpClient.builder.build)
    SqsClient.builder
      .httpClient(httpClient)
      .region(Region.EU_WEST_2)
      .build
  }

  def apply[F[_]: Async](): DASQSStreamClient[F] = DASQSStreamClient[F](sqsClient())

  def apply[F[_]: Async](httpsProxy: URI): DASQSStreamClient[F] = {
    val proxy = ProxyConfiguration
      .builder()
      .scheme(httpsProxy.getScheme)
      .endpoint(httpsProxy)
      .build
    DASQSStreamClient[F](sqsClient(Option(proxy)))
  }

  def apply[F[_]: Async](sqsClient: SqsClient): DASQSStreamClient[F] = new DASQSStreamClient[F]:
    override def receiveMessageStream[T](queueUrl: String)(using Decoder[T]): Resource[F, Queue[F, StreamMessageResponse[T]]] =
      for {
        dispatcher <- Dispatcher.parallel[F]
        queue <- Resource.eval(Queue.unbounded[F, StreamMessageResponse[T]])
        _ <- Resource.make {
          Async[F].delay {
            val connectionFactory = new SQSConnectionFactory(new ProviderConfiguration(), sqsClient)
            val connection = connectionFactory.createConnection()
            val session = connection.createSession(false, SQSSession.UNORDERED_ACKNOWLEDGE)
            val jmsQueue = session.createQueue(queueUrl)
            val consumer = session.createConsumer(jmsQueue)
            val messageListener = new MessageListener {
              override def onMessage(message: Message): Unit = {
                dispatcher.unsafeRunAndForget {
                  message match
                    case textMessage: SQSTextMessage =>
                      val bodyString = textMessage.getText
                      for {
                        messageBody <- Async[F].fromEither(decode[T](bodyString))
                        _ <- queue.offer(StreamMessageResponse(textMessage, messageBody))
                      } yield ()
                    case _ => Async[F].unit
                }
              }
            }
            consumer.setMessageListener(messageListener)
            connection.start()
            connection
          }
        }(connection => Async[F].delay(connection.close()))
      } yield queue

