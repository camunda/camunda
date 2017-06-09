# Logging

The client uses SLF4J for logging. It logs useful things like exception stack traces when a task handler fails execution. Using SLF4J, any SLF4J implementation can be used. The following uses Log4J 2.

## Maven dependencies

```xml
<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-slf4j-impl</artifactId>
  <version>2.8.1</version>
</dependency>

<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-core</artifactId>
  <version>2.8.1</version>
</dependency>
```

## Configuration

Add a file called `log4j2.xml` to the classpath of your application. Add the following contents:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN" strict="true"
    xmlns="http://logging.apache.org/log4j/2.0/config"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://logging.apache.org/log4j/2.0/config https://raw.githubusercontent.com/apache/logging-log4j2/log4j-2.8.1/log4j-core/src/main/resources/Log4j-config.xsd">
  <Appenders>
    <Console name="Console" target="SYSTEM_OUT">
      <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level Java Client: %logger{36} - %msg%n"/>
    </Console>
  </Appenders>
  <Loggers>
    <Root level="info">
      <AppenderRef ref="Console"/>
    </Root>
  </Loggers>
</Configuration>
```

This will log every log message to console.
