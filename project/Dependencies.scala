import sbt._

object Dependencies {
  private val awsSdkVersion = "2.41.34"
  private val circeVersion = "0.14.15"

  private lazy val scalaTestVersion = "3.2.19"

  lazy val awsCrt = "software.amazon.awssdk.crt" % "aws-crt" % "0.42.2"
  lazy val catsCore = "org.typelevel" %% "cats-core" % "2.13.0"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.6.3"
  lazy val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  lazy val circeParser = "io.circe" %% "circe-parser" % circeVersion
  lazy val circe = "io.circe" %% "circe-core" % circeVersion
  lazy val dynamoDB = "software.amazon.awssdk" % "dynamodb" % awsSdkVersion
  lazy val eventBridgeSdk = "software.amazon.awssdk" % "eventbridge" % awsSdkVersion
  lazy val mockito = "org.scalatestplus" %% "mockito-5-10" % "3.2.18.0"
  lazy val reactorTest = "io.projectreactor" % "reactor-test" % "3.8.2"
  lazy val s3Sdk = "software.amazon.awssdk" % "s3" % awsSdkVersion
  lazy val stsSdk = "software.amazon.awssdk" % "sts" % awsSdkVersion
  lazy val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion
  lazy val scanamo = "org.scanamo" %% "scanamo" % "6.0.0"
  lazy val snsSdk = "software.amazon.awssdk" % "sns" % awsSdkVersion
  lazy val sfnSdk = "software.amazon.awssdk" % "sfn" % awsSdkVersion
  lazy val sqsSdk = "software.amazon.awssdk" % "sqs" % awsSdkVersion
  lazy val ssmSdk = "software.amazon.awssdk" % "ssm" % awsSdkVersion
  lazy val secretsManagerSdk = "software.amazon.awssdk" % "secretsmanager" % awsSdkVersion
  lazy val transferManager = "software.amazon.awssdk" % "s3-transfer-manager" % awsSdkVersion
}
