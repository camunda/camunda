# Troubleshooting a distributed application

Debugging/monitoring a distributed application comes with a new set of challenges, especially one such as Zeebe where timing is sensitive, which prevents most usages of the debugger.

In order to have visibility into what's going at runtime, there are some techniques we can employ that will simplify the process of debugging deployed applications.

- [Debugging](#debugging)
  - [Debugger](#debugger)
  - [Logging](#logging)
  - [Thread dump](#thread-dump)
  - [Heap dump](#heap-dump)
  - [Remote JMX](#remote-jmx)
- [Profiling](#profiling)
  - [async-profile](#async-profiler)
  - [JMX](#jmx-profiling)
  - [Metrics](#metrics)

# Debugging

## Debugger

You can remotely debug your application by updating the `JAVA_TOOL_OPTIONS` used to launch it. Add the following to it to enable the remote debugging agent:

```yaml
-Xdebug
-Xnoagent
-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=4242
```

When starting the pod, the process will start and wait until the debugger connects to it on port 4242. To access the debugger locally, forward the port using
`kubectl`:

```sh
kubectl port-forward <MY-POD> 4242:4242
```

### IntelliJ

In IntelliJ, add a run configuration from the `Remote` template, to `localhost:4242`.

## Logging

The poor man's debugger, logging becomes our bread-and-butter in a debugger-less world. As logs in GCE are automatically aggregated by Stackdriver (it will ingest all logs sent to `STDOUT`), we don't have to worry much about storage space. This does not mean we should log everything, as noise can easily overwhelm and hide legitimate issues/information.

When adding logging, always think about what we're logging, and for what situation: is it helpful? What context is required for this log message to be actionable?

An example of poor logging/error reporting is the handling of unreachable nodes in Atomix: a single `java.net.ConnectionException` is thrown, with no information regarding which node was not reachable, what was the expected operation, etc.

For error and warn level logs, this is even more important, as these are expected to appear in production, and should be considered user facing. Therefore, they should be clear, precise, and actionable.

> Note that actionable can be providing just enough context for the user to report it to a Zeebe dev, who can then (from the context), act on it.

### Changing log level dynamically in a running cluster

Zeebe brokers expose a spring endpoint for configuring loggers dynamically. 
See [spring docs](https://docs.spring.io/spring-boot/docs/current/actuator-api/html/#loggers).

To change the log level of a broker in our benchmark clusters, first port-forward to the broker.

`kubectl port-forward zeebe-0 9600:9600`

Then execute the following in your local machine. 
Change `io.atomix` to the required logger and set the "configuredLevel" to the required level.

```
curl 'http://localhost:9600/actuator/loggers/io.atomix' -i -X POST -H 'Content-Type: application/json' -d '{"configuredLevel":"debug"}'
```

Alternatively, you can execute the above command directly on the broker pod if `curl` command is available.

## Thread dump

You can obtain a thread dump from the command line by finding the PID of your Java process, e.g. `pgrep java`.

> If `pgrep` is not available, you can install it by running `apt update && apt install procps`.

Once you know the PID, e.g. 12, then simply send a `SIGQUIT` signal to it (scary but safe!), like so:

```sh
kill -3 12
```

The JVM will spit a thread dump to `STDOUT`, which you can access through the Google Cloud Console.

If you want to, say, analyze which threads are keeping the CPU busy, you can find the resource usage per thread by running:

```sh
top -H -p `pgrep java`
```

Or using a fancier tool like `htop` and running:

```sh
htop -s PERCENT_CPU -p `pgrep java`
```

The PIDs of each thread listed there are decimal representations of the `nid` listed in the JVM thread dump, so from there you can map back which thread is which.

## Heap dump

Normally in our benchmarks we use docker images with only the JRE, which means you will not have any java debug tools in
the pod installed. In order to do a heap dump you would need to install the jdk to use jmap for example.

To do that run the following:
```sh
    # add stretch backports to get java 11
    echo 'deb http://ftp.debian.org/debian stretch-backports main' | tee /etc/apt/sources.list.d/stretch-backports.list

    # update - to get backports as well
    echo "update"
    apt-get update

    # install jdk 11 from backport
    echo "install jdk"
    apt-get -t stretch-backports install -y openjdk-11-jdk openjdk-11-dbg
```

After you installed the JDK to your pod you should be able to use jmap to create an heap dump.

## Remote JMX

In order to use JMX remotely, the application must unfortunately be started with the following flags:

```sh
-Dcom.sun.management.jmxremote
-Djava.rmi.server.hostname=127.0.0.1
-Dcom.sun.management.jmxremote.port=9010
-Dcom.sun.management.jmxremote.rmi.port=9010
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
-Dcom.sun.management.jmxremote.local.only=false
```

> You can modify the port from `9010` to any other unused port in the container.

This will open a remote, unauthenticated, plaintext JMX connection - do not use this configuration in production!

Since we typically don't start the JVM as the container entry point, you can add these to your `JAVA_TOOL_OPTIONS`, e.g.:

```sh
JAVA_TOOL_OPTIONS=-XX:+PrintFlagsFinal -XX:+HeapDumpOnOutOfMemoryError -XX:MinRAMPercentage=60.0 -XX:MaxRAMPercentage=90.0 -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.port=9010 -Dcom.sun.management.jmxremote.rmi.port=9010 -Djava.rmi.server.hostname=127.0.0.1
```

# Profiling

## async-profiler

Run the profile script `profile.sh` in the following way to profile a node with async profiler.

> You can replace `broker-0` with whatever pod name you want to profile below.

```sh
kubectl exec -it broker-0 bash -- < profile.sh
```

To get the resulting flamegraph you have to copy it from the pod.
Checkout the name of the generated file via.

```sh
kubectl exec broker-0 ls /tmp/profiler/
```

Then you can copy it via:

```sh
kubectl cp broker-0:/tmp/profiler/flamegraph-2019-03-27_12-42-33.svg .
```

## JMX Profiling

To profile with `JConsole` or `VisualVM`, or any tool which uses JMX, [enable remote JMX](#remote_jmx).

Once done, you can port-forward the desired port's JMX port to your localhost, and use `JConsole` or `VisualVM` to monitor your JVM, get a thread dump, etc.

```sh
kubectl port-forward broker-0 9010:9010
```

> If using VisualVM, make sure to not require an SSL connection!

## Metrics

Metrics provide valuable insights regarding the health of the application for operators in a production deployment, but they can also give us insights into how the
application is behaving. They provide a cheap, cost-effective way of getting a point-in-time snapshot of the application, and how all the moving parts correlate.

> TODO: describe useful metrics/patterns and correlation, specifically related to Zeebe, that we use when debugging
