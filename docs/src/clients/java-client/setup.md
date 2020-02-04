# Setting up the Zeebe Java Client

## Prerequisites

* Java 8

## Usage in a Maven project

To use the Java client library, declare the following Maven dependency in your project:

```xml
<dependency>
  <groupId>io.zeebe</groupId>
  <artifactId>zeebe-client-java</artifactId>
  <version>${zeebe.version}</version>
</dependency>
```

The version of the client should always match the broker's version.


## Bootstrapping

In Java code, instantiate the client as follows:

```java
ZeebeClient client = ZeebeClient.newClientBuilder()
  .brokerContactPoint("127.0.0.1:26500")
  .usePlaintext()
  .build();
```

See the class `io.zeebe.client.ZeebeClientBuilder` for a description of all available configuration properties.
