akka-jdbc-post
====
This is the supporting example project for my [blog post on Akka and JDBC](http://noisycode.com/blog/2014/07/27/akka-and-jdbc-to-services/) that follows the promise + circuit breaker approach.

The [Vagrantfile](http://vagrantup.com) provided will boot the PostgreSQL instance required by the tests, e.g.

    $ vagrant up
    $ sbt test

