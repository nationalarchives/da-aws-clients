import Dependencies.*
import sbtrelease.ReleaseStateTransformations.*

ThisBuild / scalaVersion     := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "da-aws-clients",
    publish / skip := true
  ).settings(commonSettings)
  .aggregate(sqs, sns, s3, dynamoDb, sfn)


lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    catsCore,
    catsEffect,
    circe,
    circeGeneric,
    mockito % Test,
    scalaTest % Test,
  ),
  scalaVersion := "2.13.10",
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
      id = "tna-digital-archiving-jenkins",
      name = "TNA Digital Archiving",
      email = "digitalpreservation@nationalarchives.gov.uk",
      url = url("https://github.com/nationalarchives/da-aws-clients")
    )
  ),

  licenses := List("MIT" -> new URL("https://choosealicense.com/licenses/mit/")),
  homepage := Some(url("https://github.com/nationalarchives/da-aws-clients")),

  useGpgPinentry := true,
  publishTo := sonatypePublishToBundle.value,
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
    releaseStepCommand("sonatypeBundleRelease"),
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
)

lazy val dynamoDb = (project in file("dynamodb"))
  .settings(commonSettings).settings(
  name:= "da-dynamodb-client",
  description := "A project containing useful methods for interacting with DynamoDb",
  libraryDependencies ++= Seq(
    dynamoDB,
    scanamo
  )
)

lazy val s3 = (project in file("s3"))
  .settings(commonSettings).settings(
  name:= "da-s3-client",
  description := "A project containing useful methods for interacting with S3",
  libraryDependencies ++= Seq(
    s3Sdk,
    transferManager,
    awsCrt,
    reactorTest % Test
  )
)

lazy val sqs = (project in file("sqs"))
  .settings(commonSettings).settings(
  name := "da-sqs-client",
  description := "A project containing useful methods for interacting with SQS",
  libraryDependencies ++= Seq(
    sqsSdk
  )
)

lazy val sns = (project in file("sns"))
  .settings(commonSettings).settings(
  name := "da-sns-client",
  description := "A project containing useful methods for interacting with SNS",
  libraryDependencies ++= Seq(
    snsSdk
  )
)

lazy val sfn = (project in file("sfn"))
  .settings(commonSettings).settings(
    name := "da-sfn-client",
    description := "A project containing useful methods for interacting with step functions",
    libraryDependencies ++= Seq(
      sfnSdk
    )
  )

lazy val docs = (project in file("site-docs"))
  .settings(
    name := "da-aws-docs",
    description := "Markdown files which are published as GitHub pages documentation",
    publish / skip := true,
  ).enablePlugins(ParadoxSitePlugin, ScalaUnidocPlugin, SitePreviewPlugin)
  .settings(
    paradoxProperties += ("version" -> (ThisBuild / version).value.split("-").head),
    paradoxTheme := Some(builtinParadoxTheme("generic")),
    ScalaUnidoc / siteSubdirName := "api",
    addMappingsToSiteDir(ScalaUnidoc / packageDoc / mappings, ScalaUnidoc / siteSubdirName)
  )

scalacOptions ++= Seq("-Wunused:imports", "-Werror")
