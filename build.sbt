import Dependencies.*
import sbt.internal.librarymanagement.Publishing.sonaRelease
import sbtrelease.ReleaseStateTransformations.*

lazy val root = (project in file("."))
  .settings(
    name := "da-aws-clients",
    publish / skip := true
  )
  .settings(commonSettings)
  .aggregate(sqs, sns, s3, dynamoDb, eventBridge, sfn, secretsManager, ssm)

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    catsCore,
    catsEffect,
    circe,
    circeGeneric,
    mockito % Test,
    scalaTest % Test
  ),
  dependencyOverrides += "io.netty" % "netty-codec-http" % "5.0.0.Alpha2",
  scalaVersion := "3.7.4",
  version := version.value,
  organization := "uk.gov.nationalarchives",
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/nationalarchives/da-aws-clients"),
      "git@github.com:nationalarchives/da-aws-client"
    )
  ),
  developers := List(
    Developer(
      id = "tna-da-bot",
      name = "TNA Digital Archiving",
      email = "181243999+tna-da-bot@users.noreply.github.com",
      url = url("https://github.com/nationalarchives/da-aws-clients")
    )
  ),
  scalacOptions ++= Seq("-Wunused:imports", "-Werror", "-language:implicitConversions"),
  licenses := List("MIT" -> new URL("https://choosealicense.com/licenses/mit/")),
  homepage := Some(url("https://github.com/nationalarchives/da-aws-clients")),
  useGpgPinentry := true,
  publishTo := {
    val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
    if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
    else localStaging.value
  },
  publishMavenStyle := true,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    releaseStepCommand("publishSigned"),
    releaseStepCommand(sonaRelease),
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
)

lazy val dynamoDb = (project in file("dynamodb"))
  .settings(commonSettings)
  .settings(
    name := "da-dynamodb-client",
    description := "A project containing useful methods for interacting with DynamoDb",
    libraryDependencies ++= Seq(
      dynamoDB,
      scanamo
    )
  )

lazy val eventBridge = (project in file("eventbridge"))
  .settings(commonSettings)
  .settings(
    name := "da-eventbridge-client",
    description := "A project containing useful methods for interacting with EventBridge",
    libraryDependencies ++= Seq(
      eventBridgeSdk
    )
  )

lazy val ssm = (project in file("ssm"))
  .settings(commonSettings)
  .settings(
    name := "da-ssm-client",
    description := "A project containing useful methods for interacting with SSM",
    libraryDependencies ++= Seq(
      ssmSdk,
      circeParser
    )
  )

lazy val s3 = (project in file("s3"))
  .settings(commonSettings)
  .settings(
    name := "da-s3-client",
    description := "A project containing useful methods for interacting with S3",
    libraryDependencies ++= Seq(
      s3Sdk,
      stsSdk,
      transferManager,
      awsCrt,
      reactorTest % Test
    )
  )

lazy val sqs = (project in file("sqs"))
  .settings(commonSettings)
  .settings(
    name := "da-sqs-client",
    description := "A project containing useful methods for interacting with SQS",
    libraryDependencies ++= Seq(
      sqsSdk,
      circeParser
    )
  )

lazy val sns = (project in file("sns"))
  .settings(commonSettings)
  .settings(
    name := "da-sns-client",
    description := "A project containing useful methods for interacting with SNS",
    libraryDependencies ++= Seq(
      snsSdk
    )
  )

lazy val sfn = (project in file("sfn"))
  .settings(commonSettings)
  .settings(
    name := "da-sfn-client",
    description := "A project containing useful methods for interacting with step functions",
    libraryDependencies ++= Seq(
      sfnSdk
    )
  )

lazy val secretsManager = (project in file("secretsmanager"))
  .settings(commonSettings)
  .settings(
    name := "da-secretsmanager-client",
    description := "A project containing useful methods for interacting with secrets manager",
    libraryDependencies ++= Seq(
      secretsManagerSdk,
      circeParser
    )
  )

lazy val docs = (project in file("site-docs"))
  .settings(
    name := "da-aws-docs",
    description := "Markdown files which are published as GitHub pages documentation",
    publish / skip := true
  )
  .enablePlugins(ParadoxSitePlugin, SitePreviewPlugin)
  .settings(
    paradoxProperties += ("version" -> (ThisBuild / version).value.split("-").head),
    paradoxTheme := Some(builtinParadoxTheme("generic"))
  )
