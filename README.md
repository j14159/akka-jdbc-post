akka-jdbc-post
====
This is the supporting example project for my [blog post on Akka and JDBC](http://noisycode.com/blog/2014/07/27/akka-and-jdbc-to-services/) that follows the promise + circuit breaker approach.

## Install Java, Docker, PostgreSQL, Activator

```
brew install typesafe-activator postgresql docker docker-compose
brew cask install java docker
```
## Start Docker.app

Launch Docker.app from /Applications .

## Start PostgreSQL container

```
docker-compose up -d
```

## Create schemas

```
psql -U akkajdbc -h 127.0.0.1 -p 15432 < schema.sql
```

Passward is 'akkajdbc'.

## Run test

```
activator test
```


