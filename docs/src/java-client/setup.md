# Setting up the Zeebe Java Client

## Prerequisites

* Java 8

## Usage in a Maven project

In order to use the Java client library, declare the following Maven dependency in your project:

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
Properties clientProperties = new Properties();
clientProperties.put(ClientProperties.BROKER_CONTACTPOINT, "127.0.0.1:51015");

ZeebeClient client = ZeebeClient.create(clientProperties);
```

See the class `io.zeebe.ClientProperties` for a description of all client configuration properties.
