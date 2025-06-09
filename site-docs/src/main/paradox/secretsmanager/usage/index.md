# Secrets Manager Client

The client exposes six methods
```scala
def generateRandomPassword(passwordLength: Int = 15, excludeCharacters: String = "\'\"\\"): F[GetRandomPasswordResponse]
def describeSecret(): F[DescribeSecretResponse]
def getSecretValue[T](stage: Stage = Current)(implicit decoder: Decoder[T]): F[T]
def getSecretValue[T](versionId: String, stage: Stage)(implicit decoder: Decoder[T]): F[T]
def putSecretValue[T](secret: T, stage: Stage = Current, clientRequestToken: Option[String] = None)(implicit encoder: Encoder[T]): F[PutSecretValueResponse]
def updateSecretVersionStage(potentialMoveToVersionId: Option[String], potentialRemoveFromVersionId: Option[String], stage: Stage = Current): F[UpdateSecretVersionStageResponse]
```

@@@ index

* [Zio](zio.md)
* [Fs2](fs2.md)

@@@
