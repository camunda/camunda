# Spring Zeebe -> Camunda Spring SDK

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.camunda/spring-zeebe/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.camunda/spring-zeebe)
[![Project Stats](https://www.openhub.net/p/spring-zeebe/widgets/project_thin_badge.gif)](https://www.openhub.net/p/spring-zeebe)

Spring Zeebe will slowly evolve towards Camunda Spring SDK.

This project allows you to leverage Zeebe within your Spring or Spring Boot environment.

## Table of Contents

**Getting Started**

- [Version compatibility](#version-compatibility)
- [Examples](#examples)
- [Quickstart](#quickstart)
- [Add Spring Boot Starter to your project](#add-spring-boot-starter-to-your-project)
- [Configuring Camunda 8 SaaS connection](#configuring-camunda-8-saas-connection)
- [Connect to Zeebe](#connect-to-zeebe)
- [Deploy process models](#deploy-process-models)
- [Implement job worker](#implement-job-worker)
- [Run Connectors](#run-connectors)

**Documentation**

- [Job worker configuration options](#job-worker-configuration-options)
- [Additional configuration options](#additional-configuration-options)
- [Observing metrics](#observing-metrics)

# Getting started

## Version compatibility

| Spring Zeebe version |  JDK  | Camunda version | Bundled Spring Boot version | Compatible Spring Boot versions |
|----------------------|-------|-----------------|-----------------------------|---------------------------------|
| >= 8.4.0             | >= 17 | 8.4.0           | 3.2.0                       | >= 2.7.x, 3.x.x                 |
| >= 8.3.4             | >= 17 | 8.3.4           | 3.2.0                       | >= 2.7.x, 3.x.x                 |
| >= 8.3.0             | >= 17 | 8.3.1           | 2.7.7                       | >= 2.7.x, 3.x.x                 |
| >= 8.3.0             | >= 8  | 8.3.1           | 2.7.7                       | >= 2.7.x                        |
| >= 8.2.4             | >= 17 | 8.2.4           | 2.7.7                       | >= 2.7.x, 3.x.x                 |
| >= 8.2.4             | >= 8  | 8.2.4           | 2.7.7                       | >= 2.7.x                        |
| >= 8.1.15            | >= 17 | 8.1.x           | 2.7.7                       | >= 2.7.6, 3.x.x                 |
| >= 8.1.15            | >= 8  | 8.1.x           | 2.7.7                       | >= 2.7.6                        |
| <= 8.1.14            | >= 8  | 8.1.x           | 2.7.5                       | = 2.7.x                         |

## Examples

Full examples, including test cases, are available here: [Twitter review example](https://github.com/camunda-community-hub/camunda-cloud-examples/tree/main/twitter-review-java-springboot), [process solution template](https://github.com/camunda-community-hub/camunda-8-process-solution-template). Further, you might want to have a look into the [example/](example/) folder.

## Quickstart

Create a new Spring Boot project (e.g. using [Spring initializr](https://start.spring.io/)), open a pre-existing one you already have, or fork our [Camunda 8 Process Solution Template](https://github.com/camunda-community-hub/camunda-8-process-solution-template).

## Add Spring Boot Starter to your project

Add the following Maven dependency to your Spring Boot Starter project:

```xml
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>spring-boot-starter-camunda-sdk</artifactId>
  <version>8.5.0</version>
</dependency>
```

Although Spring Zeebe has a transitive dependency to the [Zeebe Java client](https://docs.camunda.io/docs/apis-clients/java-client/), you could also add a direct dependency if you need to specify the concrete version in your `pom.xml` (even this is rarely necessary):

```xml
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>zeebe-client-java</artifactId>
  <version>8.5.0</version>
</dependency>
```

Note that if you are using [@Variables](https://github.com/camunda-community-hub/spring-zeebe#using-variable), compiler flag `-parameters` is required for Spring-Zeebe versions higher than 8.3.1.

If using Maven:

```xml
<build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <compilerArgs>
            <arg>-parameters</arg>
          </compilerArgs>
        </configuration>
      </plugin>
    </plugins>
  </build>
```

If using Gradle:

```xml
tasks.withType(JavaCompile) {
    options.compilerArgs << '-parameters'
}
```

If using Intellij:

```agsl
Settings > Build, Execution, Deployment > Compiler > Java Compiler
```

## Configuring Camunda 8 SaaS connection

Connections to Camunda SaaS can be configured by creating the following entries in your `src/main/resources/application.properties`:

```properties
zeebe.client.cloud.clusterId=xxx
zeebe.client.cloud.clientId=xxx
zeebe.client.cloud.clientSecret=xxx
zeebe.client.cloud.region=bru-2
```

You can also configure the connection to a Self-Managed Zeebe broker:

```properties
zeebe.client.broker.gateway-address=127.0.0.1:26500
zeebe.client.security.plaintext=true
```

You can enforce the right connection mode, for example if multiple contradicting properties are set:

```properties
zeebe.client.connection-mode=CLOUD
zeebe.client.connection-mode=ADDRESS
```

You can specify credentials in the following way:

```properties
common.clientId=xxx
common.clientSecret=xxx
```

## Connect to Zeebe

You can inject the ZeebeClient and work with it, e.g. to create new workflow instances:

```java
@Autowired
private ZeebeClient client;
```

## Deploy process models

Use the `@Deployment` annotation:

```java
@SpringBootApplication
@EnableZeebeClient
@Deployment(resources = "classpath:demoProcess.bpmn")
public class MySpringBootApplication {
```

This annotation internally uses [the Spring resource loader](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#resources-resourceloader) mechanism which is pretty powerful and can for example also deploy multiple files at once:

```java
@Deployment(resources = {"classpath:demoProcess.bpmn" , "classpath:demoProcess2.bpmn"})
```

or define wildcard patterns:

```java
@Deployment(resources = "classpath*:/bpmn/**/*.bpmn")
```

## Implement job worker

```java
@JobWorker(type = "foo")
public void handleJobFoo(final ActivatedJob job) {
  // do whatever you need to do
}
```

See documentation below for a more in-depth discussion on parameters and configuration options of job workers.

## Run Connectors

Spring Zeebe project previously included the Runtime for Camunda 8 Connectors. It has been moved to a separate [Connectors](https://github.com/camunda/connectors) project.
To run Connectors, you can now use the following dependency in your project:

```xml
<dependency>
  <groupId>io.camunda.connector</groupId>
  <artifactId>spring-boot-starter-camunda-connectors</artifactId>
  <version>${connectors.version}</version>
</dependency>
```

To configure the Connector Runtime use the properties explained here:
[Camunda Connector Runtime](https://github.com/camunda/connectors/blob/main/connector-runtime/README.md)

If you have previously used the pure Spring Zeebe project to run Connectors, you should migrate to the new dependency.

You can find the latest version of Connectors on [this page](https://github.com/camunda/connectors/releases).
Consult the [Connector SDK](https://github.com/camunda/connectors/tree/main/connector-sdk/core#connector-core) for details on Connectors in general.

# Documentation

## Job worker configuration options

### Job type

You can configure the job type via the `JobWorker` annotation:

```java
@JobWorker(type = "foo")
public void handleJobFoo() {
  // handles jobs of type 'foo'
}
```

If you don't specify the `type` the **method name** is used as default:

```java
@JobWorker
public void foo() {
    // handles jobs of type 'foo'
}
```

As a third possibility, you can set a default job type:

```properties
zeebe.client.worker.default-type=foo
```

This is used for all workers that do **not** set a task type via the annoation.

### Define variables to fetch

You can specify that you only want to fetch some variables (instead of all) when executing a job, which can decrease load and improve performance:

```java
@JobWorker(type = "foo", fetchVariables={"variable1", "variable2"})
public void handleJobFoo(final JobClient client, final ActivatedJob job) {
  String variable1 = (String)job.getVariablesAsMap().get("variable1");
  System.out.println(variable1);
  // ...
}
```

### Using `@Variable`

By using the `@Variable` annotation there is a shortcut to make variable retrieval simpler, including the type cast:

```java
@JobWorker(type = "foo")
public void handleJobFoo(final JobClient client, final ActivatedJob job, @Variable String variable1) {
  System.out.println(variable1);
  // ...
}
```

With `@Variable` or `fetchVariables` you limit which variables are loaded from the workflow engine. You can also override this and force that all variables are loaded anyway:

```java
@JobWorker(type = "foo", fetchAllVariables = true)
public void handleJobFoo(final JobClient client, final ActivatedJob job, @Variable String variable1) {
}
```

### Using `@VariablesAsType`

You can also use your own class into which the process variables are mapped to (comparable to `getVariablesAsType()` in the Java Client API). Therefore use the `@VariablesAsType` annotation. In the below example, `MyProcessVariables` refers to your own class:

```java
@JobWorker(type = "foo")
public ProcessVariables handleFoo(@VariablesAsType MyProcessVariables variables){
  // do whatever you need to do
  variables.getMyAttributeX();
  variables.setMyAttributeY(42);

  // return variables object if something has changed, so the changes are submitted to Zeebe
  return variables;
}
```

### Fetch variables via Job

You can access variables of a process via the ActivatedJob object, which is passed into the method if it is a parameter:

```java
@JobWorker(type = "foo")
public void handleJobFoo(final ActivatedJob job) {
  String variable1 = (String)job.getVariablesAsMap().get("variable1");
  sysout(variable1);
  // ...
}
```

### Auto-completing jobs

By default, the `autoComplete` attribute is set to `true` for any job worker.

**Note that the described default behavior of auto-completion was introduced with 8.1 and was different before, see https://github.com/camunda-community-hub/spring-zeebe/issues/239 for details.**

In this case, the Spring integration will take care about job completion for you:

```java
@JobWorker(type = "foo")
public void handleJobFoo(final ActivatedJob job) {
  // do whatever you need to do
  // no need to call client.newCompleteCommand()...
}
```

Which is the same as:

```java
@JobWorker(type = "foo", autoComplete = true)
public void handleJobFoo(final ActivatedJob job) {
  // ...
}
```

Note that the code within the handler method needs to be synchronously executed, as the completion will be triggered right after the method has finished.

When using `autoComplete` you can:

* Return a `Map`, `String`, `InputStream`, or `Object`, which then will be added to the process variables
* Throw a `ZeebeBpmnError` which results in a BPMN error being sent to Zeebe
* Throw any other `Exception` that leads in a failure handed over to Zeebe

```java
@JobWorker(type = "foo")
public Map<String, Object> handleJobFoo(final ActivatedJob job) {
  // some work
  if (successful) {
    // some data is returned to be stored as process variable
    return variablesMap;
  } else {
   // problem shall be indicated to the process:
   throw new ZeebeBpmnError("DOESNT_WORK", "This does not work because...");
  }
}
```

### Programmatically completing jobs

Your job worker code can also complete the job itself. This gives you more control about when exactly you want to complete the job (e.g. allowing the completion to be moved to reactive callbacks):

```java
@JobWorker(type = "foo", autoComplete = false)
public void handleJobFoo(final JobClient client, final ActivatedJob job) {
  // do whatever you need to do
  client.newCompleteCommand(job.getKey())
     .send()
     .exceptionally( throwable -> { throw new RuntimeException("Could not complete job " + job, throwable); });
}
```

Ideally, you **don't** use blocking behavior like `send().join()`, as this is a blocking call to wait for the issues command to be executed on the workflow engine. While this is very straightforward to use and produces easy-to-read code, blocking code is limited in terms of scalability.

That's why the worker above showed a different pattern (using `exceptionally`), often you might also want to use the `whenComplete` callback:

```java
send().whenComplete((result, exception) -> {})
```

This registers a callback to be executed if the command on the workflow engine was executed or resulted in an exception. This allows for parallelism.
This is discussed in more detail in [this blog post about writing good workers for Camunda Cloud](https://blog.bernd-ruecker.com/writing-good-workers-for-camunda-cloud-61d322cad862).

Note that when completing jobs programmatically, you must specify `autoComplete = false`.  Otherwise, there is a race condition between your programmatic job completion and the Spring integration job completion, this can lead to unpredictable results.

### `@CustomHeaders`

You can use the `@CustomHeaders` annotation for a parameter to retrieve [custom headers](https://docs.camunda.io/docs/components/concepts/job-workers/) for a job:

```java
@JobWorker(type = "foo")
public void handleFoo(@CustomHeaders Map<String, String> headers){
  // do whatever you need to do
}
```

Of course, you can combine annotations, for example `@VariablesAsType` and `@CustomHeaders`

```java
@JobWorker
public ProcessVariables foo(@VariablesAsType ProcessVariables variables, @CustomHeaders Map<String, String> headers){
  // do whatever you need to do
  return variables;
}
```

### Throwing `ZeebeBpmnError`s

Whenever your code hits a problem that should lead to a <a href="https://docs.camunda.io/docs/reference/bpmn-processes/error-events/error-events/">BPMN error</a> being raised, you can simply throw a ZeebeBpmnError providing the error code used in BPMN:

```java
@JobWorker(type = "foo")
public void handleJobFoo() {
  // some work
  if (!successful) {
   // problem shall be indicated to the process:
   throw new ZeebeBpmnError("DOESNT_WORK", "This does not work because...");
  }
}
```

## Additional Configuration Options

### Disabling ZeebeClient

If you don't want to use a ZeebeClient for certain scenarios, you can switch it off by setting:

```properties
zeebe.client.enabled=false
```

### Configuring Self-managed Zeebe Connection

```properties
zeebe.client.broker.gateway-address=127.0.0.1:26500
zeebe.client.security.plaintext=true
```

### Configure different cloud environments

If you don't connect to the Camunda SaaS production environment you might have to also adjust these properties:

```properties
zeebe.client.cloud.base-url=zeebe.camunda.io
zeebe.client.cloud.port=443
zeebe.client.cloud.auth-url=https://login.cloud.camunda.io/oauth/token
```

As an alternative you can use the [Zeebe Client environment variables](https://docs.camunda.io/docs/components/clients/java-client/index/#bootstrapping).

### Default task type

If you build a worker that only serves one thing, it might also be handy to define the worker job type globally - and not in the annotation:

```properties
zeebe.client.worker.defaultType=foo
```

### Configure jobs in flight and thread pool

Number of jobs that are polled from the broker to be worked on in this client and thread pool size to handle the jobs:

```properties
zeebe.client.worker.max-jobs-active=32
zeebe.client.worker.threads=1
```

For a full set of configuration options please see [ZeebeClientConfigurationProperties.java](spring-boot-starter-camunda/src/main/java/io/camunda/zeebe/spring/client/properties/ZeebeClientConfigurationProperties.java)

Note that we generally do not advise to use a thread pool for workers, but rather implement asynchronous code, see [Writing Good Workers](https://docs.camunda.io/docs/components/best-practices/development/writing-good-workers/).

### ObjectMapper customization

If you need to customize the ObjectMapper that the Zeebe client uses to work with variables, you can declare a bean with type `io.camunda.zeebe.client.api.JsonMapper` like this:

```java
@Configuration
class MyConfiguration {
  @Bean
  public JsonMapper jsonMapper() {
    ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
    new ZeebeObjectMapper(objectMapper);
  }
}
```

### Disable worker

You can disable workers via the `enabled` parameter of the `@JobWorker` annotation :

```java
class SomeClass {
  @JobWorker(type = "foo", enabled = false)
  public void handleJobFoo() {
    // worker's code - now disabled
  }
}
```

You can also override this setting via your `application.properties` file:

```properties
zeebe.client.worker.override.foo.enabled=false
```

This is especially useful, if you have a bigger code base including many workers, but want to start only some of them. Typical use cases are

* Testing: You only want one specific worker to run at a time
* Load Balancing: You want to control which workers run on which instance of cluster nodes
* Migration: There are two applications, and you want to migrate a worker from one to another. With this switch, you can simply disable workers via configuration in the old application once they are available within the new.

### Overriding `JobWorker` values via configuration file

You can override the `JobWorker` annotation's values, as you could see in the example above where the `enabled` property is overridden:

```properties
zeebe.client.worker.override.foo.enabled=false
```

In this case, `foo` is the type of the worker that we want to customize.

You can override all supported configuration options for a worker, e.g.:

```properties
zeebe.client.worker.override.foo.timeout=10000
```

You could also provide a custom class that can customize the `JobWorker` configuration values by implementing the `io.camunda.zeebe.spring.client.annotation.customizer.ZeebeWorkerValueCustomizer` interface.

### Enable job streaming

> Please read aboutt this feature in the [docs](https://docs.camunda.io/docs/apis-tools/java-client/job-worker/#job-streaming) upfront.

To enable job streaming on the zeebe client, you can configure it:

```properties
zeebe.client.default-job-worker-stream-enabled=true
```

### Control tenant usage

When using multi-tenancy, the zeebe client will connect to the `<default>` tenant. To control this, you can configure:

```properties
zeebe.client.default-job-worker-tenant-ids=myTenant
```

Additionally, you can set tenant ids on job worker level by using the annotation:

```java
@JobWorker(tenantIds="myOtherTenant")
```

You can override this property as well:

```properties
zeebe.client.worker.override.tenant-ids=myThirdTenant
```

## Observing metrics

Spring-zeebe-starter will provide some out-of-the-box metrics, that can be leveraged via [Spring Actuator](https://docs.spring.io/spring-boot/docs/current/actuator-api/htmlsingle/). Whenever actuator is on the classpath, you can access the following metrics:

* `camunda.job.invocations`: Number of invocations of job workers (tagging the job type)
* `camunda.connector.inbound.invocations`: Number of invocations of any inbound connectors (tagging the connector type)
* `camunda.connector.outbound.invocations`: Number of invocations of any outbound connectors (tagging the connector type)

For all of those metrics, the following actions are recorded:

* `activated`: The job/connector was activated and started to process an item
* `completed`: The processing was completed successfully
* `failed`: The processing failed with some exception
* `bpmn-error`: The processing completed by throwing an BpmnError (which means there was no technical problem)

In a default setup, you can enable metrics to be served via http:

```properties
management.endpoints.web.exposure.include=metrics
```

And then access them via http://localhost:8080/actuator/metrics/.

# Community Code of Conduct

This project adheres to the Contributor Covenant [Code of Conduct](/.github/CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please [report unacceptable behavior](https://camunda.com/events/code-conduct/reporting-violations/) as soon as possible.
