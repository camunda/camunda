# Logging

- [Configuration](#configuration)
  - [Defaults](#defaults)
- [Usage](#usage)
  - [Levels](#levels)
  - [Structured logging](#structured-logging)

Logging in C8 is done through [SLF4J](https://www.slf4j.org/). SLF4J is a _facade_ - that is, it's
simply an API to perform logging, delegating the actual implementation to one or multiple
implementations.

> [!Note]
> This means that without an implementation, SLF4J will simply do nothing. For a typical project,
> you would include the `slf4j-api` dependency, and then the `slf4j-simple` dependency for tests.
> For production use cases, common implementations are logback and log4j.
>
> C8 uses [Log4j2](https://logging.apache.org/log4j/2.x/index.html). As of now, we cannot use
> logback due to licensing issues.

We encourage you to read the [SLF4J Hello World](https://www.slf4j.org/manual.html#hello_world) and
[typical usage](https://www.slf4j.org/manual.html#typical_usage) sections of their manual before
continuing.

> [!Note]
> The SLF4J manual in general is quite short, and it's recommended you read it; that said, the API
> is intuitive enough that you will likely grasp it by learning through usage.

## Configuration

Configuration of Log4j2 is done via a `log4j2.xml` file, located in a module's resource bundle,
i.e. `src/main/resources`.

For the single C8 application, the configuration file is located in the `dist` folder (aka
`camunda-zeebe` module), at [dist/src/main/resources/log4j2.xml](../../dist/src/main/resources/log4j2.xml).
When looking at the assembled distribution artifact, you will find it at `config/log4j2.xml`
because we copy it when building the artifact.
The application resolves it because we add the `config/` folder to the classpath at launch in all the
shell scripts found in the distribution's `bin` folder.

> [!Note]
> You can read more about
> [the various ways to configure log4j2 here](https://logging.apache.org/log4j/2.x/manual/configuration.html).

The configuration bundled with the application is the default configuration, and **we should
consider it to be used by most users, so change it carefully**.

We use the configuration file primarily to define the default logging levels, the logging outputs,
and the output formats.

### Defaults

As a rule of thumb, 3rd-party libraries default log level should be `WARN`, and the log level for
our own application should be `INFO`. This is because anyone running the application will want to
know what it is doing, but would only care about 3rd party logs if something goes wrong.

One notable exception is Spring, for which we set the log level to `INFO`. This is because we use
Spring as our application server, and information about its operation (e.g. the embedded Tomcat
server starting, the security chain configuration, etc.) is relevant to the user.

As for outputs, C8 will log both to standard out (aka console) and to file by default. Logging to
STDOUT is how most Kubernetes implementations - or in general, most cloud providers - capture and
aggregate logs. The file output is then mostly for bare metal systems, where logs are expected to be
found on the server itself, and are periodically logged over, allowing the user to specify a
retention window.

> [!Note]
> You can disable the file logging via `CAMUNDA_LOG_FILE_APPENDER_ENABLED=false` environment
> variable. This is done in the configuration file by using so-called
> [Arbiters](https://logging.apache.org/log4j/2.x/manual/configuration.html#arbiters).

Finally, while we support all Log4j2 output formats (aka layouts), C8 is configured out of the box
to use a basic logging pattern called `Console`. This targets primarily development and testing
setups. For production use cases, it's recommended to use the `StackdriverLayout`, a JSON layout
tailor-made for Google Cloud's monitoring platform, Stackdriver (now called Google Cloud
Operations). The output format can be controlled at startup via environment variables; refer to the
distribution's [log4j2.xml](../../dist/src/main/config/log4j2.xml) for more.

> [!Note]
> If you want to read about how to use logs at runtime, check out the following links:
> - [Camunda Docs: Log Levels](https://docs.camunda.io/docs/next/self-managed/operational-guides/monitoring/log-levels/)
> - [Camunda Docs: Setting the log level](https://docs.camunda.io/docs/next/self-managed/zeebe-deployment/configuration/#logging)
> - [Camunda Runbook](https://github.com/camunda/runbook)

## Usage

`org.slf4j.Logger` instances are thread-safe, and it's encouraged to reuse them in order to group
log statements logically.

Since configuration is applied based on logger name prefix, the common pattern is to use the class
name as the logger name. This will uniquely identify your logger by using the [fully qualified name
of the class](https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html#jls-6.7).

By doing this, you can configure log level, appenders, output format, etc., not only for a specific
class (e.g. for tracing), but for a complete package, module, namespace, etc., simply by matching
prefixes. Using the class name thus makes it very intuitive to find the location of the log and
understand its context.

For example, take the `zeebe-util` module, where all classes live under the `io.camunda.zeebe.util`
package. By configuring the log level to `DEBUG` for `io.camunda.zeebe.util`, you would see debug
level logging (and higher) for all classes under the module. Now, say we want to trace allocations
from the `io.camunda.zeebe.util.allocation` classes, we could also configure a logger with this name
to `TRACE`. If we only cared to trace
[DirectBufferAllocator](../../zeebe/util/src/main/java/io/camunda/zeebe/util/allocation/DirectBufferAllocator.java),
then we could simply set `io.camunda.zeebe.util.allocation.DirectBufferAllocator` to trace.

> [!Note]
> Doing so would also trace inner classes, as their fully qualified class names are prefixed by the
> enclosing class' FQCN. However, in many such cases, this is still what you want to do.
>
> [!Note]
> Remember, you can change the log level dynamically at runtime, making the above feature very
> powerful to diagnose problems on a live system.

## Levels

SLF4J supports the following log levels, ordered from lowest to highest: `TRACE`, `DEBUG`, `INFO`, `WARN`, and `ERROR`.

Each level is meant for a specific type of logging, and allows us to control how much information we want to capture at any given time.
Picking the right log level for a statement is crucial, for three reasons: noise-to-signal ratio, costs, and support load.

Since logging is one of our main tools to diagnose issues, it's critical to keep a good noise-to-signal ratio, otherwise we end up wasting more time trying to find the relevant logs among piles of
unnecessary statements.

Additionally, logging can become quite expensive: enriching, network transfer, aggregation,  storage, etc., all of this incurs a cost. Say you end up in an aggressive busy loop, and you're logging something
every 5 milliseconds - you'll have aggregated gigabytes of data in an hour. But is it all relevant?

Finally, unnecessary errors or warnings often result in customers being unsure about the health of their running system, resulting in support issues that
engineers have to spend time, which may be entirely avoided if the right log level had been picked.

You can use this shorthand table to guide yourself in picking the right log level:

|  Level  |   Target persona    |                                                                                                 Description                                                                                                  |                                                                         Guiding Questions                                                                         |
|---------|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ERROR` | Users & C8 Engineer | Reserved for errors which require human intervention. This includes errors which may not resolvable by the user.                                                                                             | Is this an error which a human needs to look at, or will it go away on its on?                                                                                    |
| `WARN`  | Users & C8 Engineer | Statements which may indicate that parts of the system are not working, and would require human intervention if they persist, but may resolve by themselves.                                                 | Is this an error from which the software can automatically recover? Is it expected to sometimes happen (e.g. network failures)?                                   |
| `INFO`  | Users & C8 Engineer | Information about the system which is useful for the user. Note that the user varies from component to component: for a client, this is the end user. For a broker, this is the person operating the broker. |                                                                                                                                                                   |
| `DEBUG` | C8 Engineer         | Information which can provide helpful context when debugging for a C8 engineer, but not really for a normal user.                                                                                            | Is this really useful for the user, or is it for me to diagnose issues? Is it just adding context                                                                 |
| `TRACE` | C8 Engineer         | Same as debug, but used if it would lead to too much noise (e.g. tracing method calls).                                                                                                                      | Is this tracing all the steps execution of a specific operation? Is this debug information, but called in a tight loop many times over, producing a lot of noise? |

### ERROR

The `ERROR` level is the highest error level. When setting a logger to it, that means _only_ error level statements will be logged, **and nothing else**.

Since this level is reserved for statements which should prompt a human to look at them, and should be reserved for statements which cannot be ignored.
As such, they inherently incur a high cost, and should be used sparingly: logging something at error level which is _not_ really an error may end up causing
unnecessary headaches for customers (e.g. "is my system healthy? Did I misconfigure it?"), which can lead to new support issues and wasted time and resources on our side.

Good examples:

- Failure to deserialize persisted data on disk. This is not recoverable, since it likely means there is some data corruption or a bug.
- Checksum mismatch when reading committed data on disk. This means we have data corruption, and this is not recoverable, and a human must decide what to do.
- Invalid configuration which prevents the application from starting. This requires a human to fix in order to recover, so it should be triggering some alerts.

Bad examples:

- Sending a request to another node times out; this is not an error level issue itself, since networks are unreliable and _will_ fail at some point.
- Writing data to disk fails. I/O in general is unreliable, and will fail at scale, so this is also expected to happen sometime. One failure doesn't indicate an actual problem.

#### Attaching an exception

By convention, they are most often associated with an exception, providing a stack trace to help diagnose the issue. In SLF4J, this can be done by passing the exception as the last argument; you do not need to have it show up in the message.

Here's an example of a **bad usage**:

```java
final Exception myException = ...;
LOGGER.error("An error occurred while trying to process a new batch operation with ID {}. Error: {}",
  id, myException);
```

And here's a good way to pass the exception:

```java
final Exception myException = ...;
LOGGER.error("An error occurred while trying to process a new batch operation with ID {}",
  id, myException);
```

> [!Note]
> You can attach exceptions to log statements at any level, which can still be useful for debugging. But by convention,
> almost all error logs should carry an exception where applicable.

### WARN

Warnings are the second highest, meaning setting this as the level for a logger will log both `WARN` and `ERROR` statements.

Warnings are typically used to indicate that something is wrong, but does not require immediate intervention. This may mean that
it may either recover by itself, or simply that intervention can wait.

For example, I/O errors are inevitable at scale. They typically will recover, but if they don't, then it means something may be wrong and
require a human to look into. Say a client is trying to send a request to a server - maybe it times out, or maybe the server is temporarily unavailable.
In many cases, this is retry-able, and if it doesn't happen often, then there's really no need to look into it. However, if it keeps happening, then it could
be due to misconfiguration or the likes, which does require someone to fix. So in this case, the rule of thumb is, a warning should be treated as an error
only if it is repeatedly, consistently logged.

You can also use warnings to indicate misuse of the software which doesn't prevent its usage, but should be fixed eventually by the user. This is often used for things
like deprecation notices (e.g. using an outdated configuration option), setting dangerous configuration options (e.g. disabling Raft flush for performance), etc.

### INFO

`INFO` level statements are meant to inform the user (which varies per component) about state changes in the application. It is **not** meant for
information used by you, the engineer, to debug potential issues. Setting a logger to `INFO` level will also cause it to print out `WARN` and `ERROR` messages.

> [!Note]
> User here can change based on the component. For brokers, this is typically the operator/SRE of the broker deployment.
> For clients, this will be the application developer integrating the client. Think about who will be seeing the log statement, essentially.

Keep in mind that `INFO` is our default log level, so those statements will show up during the normal, healthy usage of the application. As such, they should
not be too noisy (remember, logs are expensive!), and only really represent useful state changes.

Good examples of `INFO` level messages:

- A broker becomes leader for a partition. This is useful to know in general, and doesn't happen too often.
- The REST API is exposed over port 80 and is using TLS. This is useful to know in general, especially for actual usage of the application.
- The application has finished starting up, and is ready to receive traffic.

Bad examples of `INFO` level messages:

- The application took 1 minute to start. This is not really useful for the user, but rather for us as a performance metric.
- The broker applied X migrations which took Y seconds during its leader transition. Again, this is information for us to diagnose what happened later, but not interesting for the user.

### DEBUG

`DEBUG` statements are meant to provide information for developers when something went wrong. They are not meant for users,
but really for engineers to diagnose problems after the fact. Since this is typically our first line of defense against bugs, you want to balance
having the right info with making it easy to find, so avoid having too much noise with debug statements. Anything which would be called very
often should be delegated to `TRACE`.

Good examples:

- Logging which migrations were run and which were skipped during a leader transition.
- Logging additional context information once the application has finished starting up. This is typically not useful in a normal run, but may be useful initially when testing different parameters.

Bad examples:

- Printing every time a specific method is called.
- Printing the current value of a field in a tight loop that is executed hundreds of times in a second. This will cause so much noise, and should instead be reserved for `TRACE`.

### TRACE

`TRACE` is the lowest log level, and setting that to a logger will cause _all_ statements to be printed. It is the most granular level you can get, and as such,
the noisiest - use very sparingly.

Example of tracing are usually reserved for debug statements which will trigger very often, and as such would be very noisy unless you're specifically debugging a class or operation.
For example, logging every time we push a job from the engine would log hundreds if not thousands of times per second. However, you may want to temporarily see that in a system if you suspect
something is going wrong and need more context, so you could add a trace statement with some info about the job, and dynamically enable it on the failing system.

#### Integration

`ERROR` level statement in SaaS will be aggregated into
[Google's error reporting tool](https://cloud.google.com/error-reporting/docs/viewing-errors), which is a great way
for us to discover new errors and track them, so make use of it!

## Structured logging

We always add the full [MDC](https://www.slf4j.org/api/org/slf4j/MDC.html) to every log statement. This lets you attach key-value pair either on a
thread (and thus all statements produced by this thread), or directly on a per-statement basis.

The easiest way to make use of it is using the fluent log builders:

```java
LOGGER
  .atTrace()
  .addKeyValue("piKey", processInstanceKey)
  .addKeyValue("count", count)
  .log("Exceeded maximum monitoring count, will skip this process instance");
```

You can also attach it to a thread directly. The following is a functionally equivalent example as the one above:

```java
MDC.putAll(Map.of("piKey", processInstanceKey, "count", count));
LOGGER.trace("Exceeded maximum monitoring count, will skip this process instance");
```

Though sometimes you only want to context to be short-lived:

```java
try (final var piKey = MDC.putCloseable("piKey", processInstanceKey);
     final var count = MDC.putCloseable("count", count)) {
  LOGGER.trace("Exceeded maximum monitoring count, will skip this process instance");
}
```

When combined with the `StackdriverLayout`, or just some JSON output, this can be very powerful as some systems will
let you filter log statements via their custom fields.

For example, in Zeebe, we often use the `partitionId` as a field attached to logs, allowing us to easily filter for
all logs for a given partition, which helps cut down the noise-to-signal ratio.
