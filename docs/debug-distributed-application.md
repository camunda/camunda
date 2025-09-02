# Troubleshooting a distributed application

Debugging/monitoring a distributed application comes with a new set of challenges, especially one such as Zeebe where timing is sensitive, which prevents most usages of the debugger.

In order to have visibility into what's going at runtime, there are some techniques we can employ that will simplify the process of debugging deployed applications.

- [Debugging](#debugging)
  - [Debugger](#debugger)
  - [Logging](#logging)
  - [Ephemeral Container](#ephemeral-container-for-a-debugging-shell)
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

## Ephemeral container for a debugging shell

In case you need to make use of debug tooling on the actual pod it's recommended
to make use of [ephemeral containers](https://kubernetes.io/docs/concepts/workloads/pods/ephemeral-containers/#uses-for-ephemeral-containers).

To get a shell with a jdk on a node you can use the following command:

> [!Note]
>
> You need to replace `<broker-0>` with the pod name you want to profile below.

```sh
kubectl debug -it -c debugger --profile=restricted --image=eclipse-temurin:21.0.3_9-jdk-alpine --target=zeebe <broker-0> -- /bin/bash
```

## Thread dump

You can obtain a thread dump from the command line by finding the PID of your Java process, e.g. `jps` or `pgrep java` (if jps is not available).

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

Normally we have Java tools available in our benchmarks. This means we can directly use `jmap`.

```shell
$ k exec -it <pod> -- jps
7 StandaloneBroker
328 Jps
k exec <POD> -- jmap -dump:live,format=b,file=data/heap.dump 7
```

Afterward, this file needs to copied to the local disk.

```shell
kubectl cp <pod>:/usr/local/camunda/data/heap.dump /tmp/heap.dump
```

### Alternative

If we use docker images with only the JRE, which means you will not have any java debug tools in
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

To make our life easier we are providing a script to execute profiling inside a container, you can find it [here](../zeebe/load-tests/docs/scripts/executeProfiling.sh)

Simply run the script with the respective pod name:

```shell
./executeProfiling <POD>
```

This will run the [async-profiler](https://github.com/async-profiler/async-profiler) inside the container and download the resulting flame graph.

### Alternative

To run async profiling you would first have to download the async profiler preferably via an
[ephemeral container](#ephemeral-container-for-a-debugging-shell) shell.

For profiling to work, the profiler lib `libasyncProfiler.so` needs to be accessible via the file system of the zeebe process though.
In order to put it there you need to determine the pid of the zeebe jvm, in this example it is `8`.

```sh
root@pod:# jps
8 StandaloneBroker
910 Jps
```

You can then set up the async-profiler with these commands:

> You need to replace `<pid>` with the process id of the java process you want to profile
>
> ```sh
> ln -s /proc/<pid>/root /zeebe-root && \
> mkdir /async-profiler && curl -sSL https://github.com/jvm-profiling-tools/async-profiler/releases/download/v2.8.3/async-profiler-2.8.3-linux-x64.tar.gz | tar xzv -C /async-profiler --strip-components 1 && \
> mkdir -p /zeebe-root/async-profiler/build && cp /async-profiler/build/libasyncProfiler.so /zeebe-root/async-profiler/build/libasyncProfiler.so
> ```

Run the profile script `profiler.sh` in the following way to profile a node with the async profiler for 30 seconds.
For more options see [Profiler Options](https://github.com/jvm-profiling-tools/async-profiler#profiler-options).

> You need to replace `<pid>` with the process id of the java process you want to profile
>
> ```sh
> /async-profiler/profiler.sh -d 30 -f /tmp/flamegraph.html <pid>
> ```

To get the resulting flamegraph you have to copy it from the pod via:

> You need to replace `<broker-0>` with the pod name you want to profile below.
>
> ```sh
> kubectl cp <broker-0>:/tmp/flamegraph.html .
> ```

## JMX Profiling

To profile with `JConsole` or `VisualVM`, or any tool which uses JMX, [enable remote JMX](#remote-jmx).

Once done, you can port-forward the desired port's JMX port to your localhost, and use `JConsole` or `VisualVM` to monitor your JVM, get a thread dump, etc.

```sh
kubectl port-forward broker-0 9010:9010
```

> If using VisualVM, make sure to not require an SSL connection!

## Metrics

Metrics provide valuable insights regarding the health of the application for operators in a production deployment, but they can also give us insights into how the
application is behaving. They provide a cheap, cost-effective way of getting a point-in-time snapshot of the application, and how all the moving parts correlate.

> TODO: describe useful metrics/patterns and correlation, specifically related to Zeebe, that we use when debugging

## JFR

To run JFR inside a container we can make use of the following command:

```shell
kubectl exec -it <pod-name> -- jcmd 1 JFR.start duration=100s filename=/usr/local/camunda/data/flight-$(date +%d%m%y-%H%M).jfr
```

If the flight recording is done, you can copy the recording (via kubectl cp) and open it with Intellij (or [JMC](https://www.oracle.com/java/technologies/javase/products-jmc9-downloads.html)).

### Alternative

If `jcmd` is in the container not available we can make use of a debug container

```shell
kubectl debug -it -c debugger --profile=restricted --image=eclipse-temurin:21.0.3_9-jdk-alpine --target=<container> <POD> -- sh
```

With this debug container we can now run `jcmd` to get a Java flight recording:

```shell
jcmd 7 JFR.start duration=100s filename=flight.jfr
```

After the recording is done, and written we can download it from the actual container:

```shell
# Check existence of recording
$ kubectl exec -it <pod-name> -- ls -la flight.jfr
-rw-r--r--    1 camunda  camunda    1452909 Jun 28 11:32 flight.jfr
# Copy file
$ kubectl cp <pod-name>:/usr/local/operate/flight.jfr /tmp/flight.jfr
```

