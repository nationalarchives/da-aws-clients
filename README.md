# Digital Archiving AWS Clients

A set of AWS clients which provide methods useful across Digital Archiving.
The methods are written using generic types with the Cats type classes.
This allows any calling code to use any effects which implement these classes, most commonly ZIO or Cats Effect.

The full documentation [is here](https://nationalarchives.github.io/da-aws-clients/)

## Repository secrets
The secrets this repository uses were originally copied from parameter store to GitHub in the `nationalarchives/da-terraform-github-repositories` repository.

That repo only contained the secrets for this repo, so I've moved them to 
https://github.com/nationalarchives/dr2-terraform-github-repositories/ 