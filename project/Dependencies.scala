import sbt._

object Dependencies {
  private val awsSdkVersion = "2.20.69"
  private val circeVersion = "0.14.5"

  lazy val awsCrt = "software.amazon.awssdk.crt" % "aws-crt" % "0.21.15"
  lazy val catsCore = "org.typelevel" %% "cats-core" % "2.9.0"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.0"
  lazy val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  lazy val circe = "io.circe" %% "circe-core" % circeVersion
  lazy val dynamoDB = "software.amazon.awssdk" % "dynamodb" % awsSdkVersion
  lazy val eventBridgeSdk = "software.amazon.awssdk" % "eventbridge" % awsSdkVersion
  lazy val log4Cats =  "org.typelevel" %% "log4cats-slf4j"   % "2.6.0"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "1.17.12"
  lazy val reactorTest = "io.projectreactor" % "reactor-test" % "3.5.6"
  lazy val s3Sdk = "software.amazon.awssdk" % "s3" % awsSdkVersion
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.15"
  lazy val scanamo = "org.scanamo" %% "scanamo" % "1.0-M13"
  lazy val snsSdk = "software.amazon.awssdk" % "sns" % awsSdkVersion
  lazy val sfnSdk = "software.amazon.awssdk" % "sfn" % awsSdkVersion
  lazy val sqsSdk = "software.amazon.awssdk" % "sqs" % awsSdkVersion
  lazy val transferManager = "software.amazon.awssdk" % "s3-transfer-manager" % awsSdkVersion
}
