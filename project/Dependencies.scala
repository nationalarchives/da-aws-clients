import sbt._

object Dependencies {
  private val awsSdkVersion = "2.20.69"
  private val circeVersion = "0.14.6"

  lazy val awsCrt = "software.amazon.awssdk.crt" % "aws-crt" % "0.27.7"
  lazy val catsCore = "org.typelevel" %% "cats-core" % "2.10.0"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.2"
  lazy val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  lazy val circeParser = "io.circe" %% "circe-parser" % circeVersion
  lazy val circe = "io.circe" %% "circe-core" % circeVersion
  lazy val dynamoDB = "software.amazon.awssdk" % "dynamodb" % awsSdkVersion
  lazy val eventBridgeSdk = "software.amazon.awssdk" % "eventbridge" % awsSdkVersion
  lazy val mockito = "org.mockito" %% "mockito-scala" % "1.17.22"
  lazy val reactorTest = "io.projectreactor" % "reactor-test" % "3.5.10"
  lazy val s3Sdk = "software.amazon.awssdk" % "s3" % awsSdkVersion
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.17"
  lazy val scanamo = "org.scanamo" %% "scanamo" % "1.0.0-M30"
  lazy val snsSdk = "software.amazon.awssdk" % "sns" % awsSdkVersion
  lazy val sfnSdk = "software.amazon.awssdk" % "sfn" % awsSdkVersion
  lazy val sqsSdk = "software.amazon.awssdk" % "sqs" % awsSdkVersion
  lazy val secretsManagerSdk = "software.amazon.awssdk" % "secretsmanager" % awsSdkVersion
  lazy val transferManager = "software.amazon.awssdk" % "s3-transfer-manager" % awsSdkVersion
}
