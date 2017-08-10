# Install

This page guides you through the initial install of your Zeebe broker. In case you are looking for more detailed information on how to setup and operate Zeebe, make sure to check out the [Operations Guide](/operations/README.html) as well.

There are different ways to install Zeebe:

* Download a distribution
* Use Docker
* Use a Linux Distribution package manager _\(coming soon\)_

## Prerequisites

* Operating System:
  * Windows \(development only, not supported for production\)
  * Linux \(TODO: more details on distros and filesystems\)
* Java Virtual Machine:
  * Oracle Hotspot v1.8+
  * Open JDK v1.8+

## Download a distribution

You can always download the latest Zeebe release from the [Github release page](https://github.com/zeebe-io/zeebe/releases).

Once you have downloaded a distribution, extract it into a folder of your choice. In order to extract the Zeebe distribution and start the broker, **Linux users** can type:

```bash
$ tar -xzf zeebe-distribution-0.1.0.tar.gz -C zeebe/
$ cd zeebe/bin
$ ./broker
```

**Windows users** should probably download the `.zip`package and extract it using their favorite unzip tool. They can then open the extracted folder, navigate into the `bin`folder and start the broker by double-clicking on the `broker.bat` file.

Once the Zeebe broker has started, it should produce the following output:

```bash
10:48:51.153 [main] INFO  io.zeebe.broker.system - Using config file conf/zeebe.cfg.toml
10:48:51.224 [main] INFO  io.zeebe.broker.system - Using data directory: data/
10:48:51.335 [service-container-action-0] INFO  io.zeebe.broker.services - Using data/metrics/metrics.zeebe for counters
10:48:51.690 [actor-runner-service-container] INFO  io.zeebe.broker.transport - Bound replicationApi.server to localhost/127.0.0.1:51017
10:48:51.692 [actor-runner-service-container] INFO  io.zeebe.broker.transport - Bound managementApi.server to localhost/127.0.0.1:51016
10:48:51.755 [actor-runner-service-container] INFO  io.zeebe.transport - Bound clientApi.server to /0.0.0.0:51015
```

## Using Docker

You can run Zeebe with Docker:

```bash
docker run -p 51015:51015 camunda/zeebe:0.1.0
```

### Exposed Ports

- `51015`: Client API
- `51016`: Management API for broker to broker communcation
- `51017`: Replication API for broker to broker replication

### Volumes

The default data volume is under `/usr/local/zeebe/bin/data` it contains
all data which should be persisted.

### Configuration

The Zeebe configuration is located under
`/usr/local/zeebe/conf/zeebe.cfg.toml`. The logging configuration is located
under `/usr/local/zeebe/conf/log4j2.xml`.

### Mac and Windows users

**Note**: On systems which use a VM to run Docker containers like Mac and
Windows the VM needs at least 4GB of memory otherwise Zeebe fails to start
with an error similar to:

```
Exception in thread "actor-runner-service-container" java.lang.OutOfMemoryError: Direct buffer memory
        at java.nio.Bits.reserveMemory(Bits.java:694)
        at java.nio.DirectByteBuffer.<init>(DirectByteBuffer.java:123)
        at java.nio.ByteBuffer.allocateDirect(ByteBuffer.java:311)
        at io.zeebe.util.allocation.DirectBufferAllocator.allocate(DirectBufferAllocator.java:28)
        at io.zeebe.util.allocation.BufferAllocators.allocateDirect(BufferAllocators.java:26)
        at io.zeebe.dispatcher.DispatcherBuilder.initAllocatedBuffer(DispatcherBuilder.java:266)
        at io.zeebe.dispatcher.DispatcherBuilder.build(DispatcherBuilder.java:198)
        at io.zeebe.broker.services.DispatcherService.start(DispatcherService.java:61)
        at io.zeebe.servicecontainer.impl.ServiceController$InvokeStartState.doWork(ServiceController.java:269)
        at io.zeebe.servicecontainer.impl.ServiceController.doWork(ServiceController.java:138)
        at io.zeebe.servicecontainer.impl.ServiceContainerImpl.doWork(ServiceContainerImpl.java:110)
        at io.zeebe.util.actor.ActorRunner.tryRunActor(ActorRunner.java:165)
        at io.zeebe.util.actor.ActorRunner.runActor(ActorRunner.java:145)
        at io.zeebe.util.actor.ActorRunner.doWork(ActorRunner.java:114)
        at io.zeebe.util.actor.ActorRunner.run(ActorRunner.java:71)
        at java.lang.Thread.run(Thread.java:748)
```

If you use a Docker setup with `docker-machine` and your `default` VM does
not have 4GB of memory you can create a new one with the following command.

```
docker-machine create --driver virtualbox --virtualbox-memory 4000 zeebe
```

Verify that the Docker Machine is running correctly:

```
$ docker-machine ls
NAME        ACTIVE   DRIVER       STATE     URL                         SWARM   DOCKER        ERRORS
zeebe     *        virtualbox   Running   tcp://192.168.99.100:2376           v17.03.1-ce
```

Configure your terminal:

```
eval $(docker-machine env zeebe)
```

Then run Zeebe:

```
docker run --rm -p 51015:51015 -p 51016:51016 -p 51017:51017 camunda/zeebe:latest
```

To get ip of Zeebe:
```
$ docker-machine ip zeebe
192.168.99.100
```

Verify you can connect to Zeebe:
```
$ telnet 192.168.99.100 51015
```

## Linux Distribution Packages

Coming Soon!
