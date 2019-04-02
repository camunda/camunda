# Install

This page guides you through the initial installation of your Zeebe broker. In case you are looking for more detailed information on how to set up and operate Zeebe, make sure to check out the [Operations Guide](/operations/README.html) as well.

There are different ways to install Zeebe:

* [Download a distribution](#download-a-distribution)
* [Using Docker](#using-docker)

## Prerequisites

* Operating System:
  * Linux
  * Windows/MacOS (development only, not supported for production)
* Java Virtual Machine:
  * Oracle Hotspot v1.8
  * Open JDK v1.8

## Download a distribution

You can always download the latest Zeebe release from the [Github release page](https://github.com/zeebe-io/zeebe/releases).

Once you have downloaded a distribution, extract it into a folder of your choice. To extract the Zeebe distribution and start the broker, **Linux users** can type:

```bash
tar -xzf zeebe-distribution-X.Y.Z.tar.gz -C zeebe/
./bin/broker
```

**Windows users** can download the `.zip`package and extract it using their favorite unzip tool. They can then open the extracted folder, navigate to the `bin` folder and start the broker by double-clicking on the `broker.bat` file.

Once the Zeebe broker has started, it should produce the following output:

```bash
10:49:52.264 [] [main] INFO  io.zeebe.broker.system - Using configuration file zeebe-broker-X.Y.Z/conf/zeebe.cfg.toml
10:49:52.342 [] [main] INFO  io.zeebe.broker.system - Scheduler configuration: Threads{cpu-bound: 2, io-bound: 2}.
10:49:52.383 [] [main] INFO  io.zeebe.broker.system - Version: X.Y.Z
10:49:52.430 [] [main] INFO  io.zeebe.broker.clustering - Starting standalone broker.
10:49:52.435 [service-controller] [0.0.0.0:26500-zb-actors-1] INFO  io.zeebe.broker.transport - Bound managementApi.server to /0.0.0.0:26502
10:49:52.460 [service-controller] [0.0.0.0:26500-zb-actors-1] INFO  io.zeebe.transport - Bound clientApi.server to /0.0.0.0:26501
10:49:52.460 [service-controller] [0.0.0.0:26500-zb-actors-1] INFO  io.zeebe.transport - Bound replicationApi.server to /0.0.0.0:26503
```

## Using Docker

You can run Zeebe with Docker:

```bash
docker run --name zeebe -p 26500:26500 camunda/zeebe:latest
```

### Exposed Ports

- `26500`: Gateway API
- `26501`: Client API
- `26502`: Management API for broker to broker communcation
- `26503`: Replication API for broker to broker replication
- `26504`: Subscription API for message correlation

### Volumes

The default data volume is under `/usr/local/zeebe/bin/data`. It contains
all data which should be persisted.

### Configuration

The Zeebe configuration is located at `/usr/local/zeebe/conf/zeebe.cfg.toml`.
The logging configuration is located at `/usr/local/zeebe/conf/log4j2.xml`.

The configuration of the docker image can also be changed by using environment
variables.

Available environment variables:

 - `ZEEBE_LOG_LEVEL`: Sets the log level of the Zeebe Logger (default: `info`).
 - `ZEEBE_HOST`: Sets the host address to bind to instead of the IP of the container.
 - `BOOTSTRAP`: Sets the replication factor of the `internal-system` partition.
 - `ZEEBE_CONTACT_POINTS`: Sets the contact points of other brokers in a cluster setup.
 - `DEPLOY_ON_KUBERNETES`: If set to `true`, it applies some configuration changes in order to run Zeebe
 in a Kubernetes environment.

### Mac and Windows users

**Note**: On systems which use a VM to run Docker containers like Mac and
Windows, the VM needs at least 4GB of memory, otherwise Zeebe might fail to start
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
not have 4GB of memory, you can create a new one with the following command:

```
docker-machine create --driver virtualbox --virtualbox-memory 4000 zeebe
```

Verify that the Docker Machine is running correctly:

```
docker-machine ls
```
```
NAME        ACTIVE   DRIVER       STATE     URL                         SWARM   DOCKER        ERRORS
zeebe     *        virtualbox   Running   tcp://192.168.99.100:2376           v17.03.1-ce
```

Configure your terminal:

```
eval $(docker-machine env zeebe)
```

Then run Zeebe:

```
docker run --rm -p 26500:26500 camunda/zeebe:latest
```

To get the ip of Zeebe:
```
docker-machine ip zeebe
```
```
192.168.99.100
```

Verify that you can connect to Zeebe:
```
telnet 192.168.99.100 26500
```
