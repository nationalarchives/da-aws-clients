# AWS Clients

A set of AWS clients which provide methods useful across Digital Archiving.
The methods are written using generic types with the Cats type classes. 
This allows any calling code to use any effects which implement these classes, most commonly ZIO or Cats Effect.

View the [Scaladoc](api/uk/gov/nationalarchives/index.html) 

@@@ index

* [DynamoDB Client Usage](dynamodb/usage/index.md)
* [EventBridge Client Usage](eventbridge/usage/index.md)
* [S3 Client Usage](s3/usage/index.md)
* [SFN Client Usage](sfn/usage/index.md)
* [SNS Client Usage](sns/usage/index.md)
* [SQS Client Usage](sqs/usage/index.md)

@@@
