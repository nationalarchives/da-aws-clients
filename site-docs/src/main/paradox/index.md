# AWS Clients

A set of AWS clients which provide methods useful across Digital Archiving.
The methods are written using generic types with the Cats type classes. 
This allows any calling code to use any effects which implement these classes, most commonly ZIO or Cats Effect.

View the [Scaladoc](api/uk/gov/nationalarchives/index.html) 

@@@ index

* [S3 Client Usage](s3/usage/index.md)
* [SQS Client Usage](sqs/usage/index.md)

@@@
