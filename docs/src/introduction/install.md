# Install

This page guides you through the initial installation of the Zeebe broker and Zeebe Modeler for development purposes.

If you're looking for more detailed information on how to set up and operate Zeebe, make sure to check out the [Operations Guide](/operations/) as well.

There are two different ways to install the Zeebe broker:
* [Using Docker](#using-docker) (Recommended)
* [Download a distribution](#download-a-distribution)

You'll likely also need the Zeebe Modeler for your project:
* [Download the Zeebe Modeler](#install-the-zeebe-modeler)

## Using Docker

The easiest way to try Zeebe is using Docker. Using Docker provides you with a consistent environment, and we recommend it for development.

### Prerequisites

* Operating System:
  * Linux
  * Windows/MacOS (development only, not supported for production)
* Docker

### Docker configurations for docker-compose

The absolutely easiest way to try Zeebe is using the official docker-compose repository. This allows you to start complex configurations with a single command, and understand the details of how they are configured when you are ready to delve to that level.

Docker configurations for starting a single Zeebe broker using `docker-compose`, optionally with Operate and Simple Monitor, are available in the [zeebe-docker-compose](https://github.com/zeebe-io/zeebe-docker-compose/blob/master/README.md) repository. Further instructions for using these configurations are in [the README.md in that repository](https://github.com/zeebe-io/zeebe-docker-compose/blob/master/README.md).

### Using Docker without docker-compose

You can run Zeebe with Docker:

```bash
docker run --name zeebe -p 26500-26502:26500-26502 camunda/zeebe:latest
```

### Exposed Ports

- `26500`: Gateway API
- `26501`: Command API (gateway-to-broker)
- `26502`: Internal API (broker-to-broker)

### Volumes

The default data volume is under `/usr/local/zeebe/data`. It contains
all data which should be persisted.

### Configuration

The Zeebe configuration is located at `/usr/local/zeebe/config/application.yaml`.
The logging configuration is located at `/usr/local/zeebe/config/log4j2.xml`.

The configuration of the docker image can also be changed by using environment
variables. The configuration template files also contains information on the environment
variables to use for each configuration setting.

Available environment variables:

 - `ZEEBE_LOG_LEVEL`: Sets the log level of the Zeebe Logger (default: `info`).
 - `ZEEBE_BROKER_NETWORK_HOST`: Sets the host address to bind to instead of the IP of the container.
 - `ZEEBE_BROKER_CLUSTER_INITIALCONTACTPOINTS`: Sets the contact points of other brokers in a cluster setup.

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

If you are using Docker with the default Moby VM, you can adjust the amount of memory available to the VM through the Docker preferences. Right-click on the Docker icon in the System Tray to access preferences.

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

## Download a distribution

You can always download the latest Zeebe release from the [Github release page](https://github.com/zeebe-io/zeebe/releases).

### Prerequisites

* Operating System:
  * Linux
  * Windows/MacOS (development only, not supported for production)
* Java Virtual Machine:
  * Oracle Hotspot 11
  * Open JDK 11

Once you have downloaded a distribution, extract it into a folder of your choice. To extract the Zeebe distribution and start the broker, **Linux users** can type:

```bash
tar -xzf zeebe-distribution-X.Y.Z.tar.gz -C zeebe/
./bin/broker
```

**Windows users** can download the `.zip`package and extract it using their favorite unzip tool. They can then open the extracted folder, navigate to the `bin` folder and start the broker by double-clicking on the `broker.bat` file.

Once the Zeebe broker has started, it should produce the following output:

```
bash
23:39:13.246 [] [main] INFO  io.zeebe.broker.system - Scheduler configuration: Threads{cpu-bound: 2, io-bound: 2}.
23:39:13.270 [] [main] INFO  io.zeebe.broker.system - Version: X.Y.Z
23:39:13.273 [] [main] INFO  io.zeebe.broker.system - Starting broker with configuration {
```

## Install the Zeebe Modeler

The Zeebe Modeler is an open-source desktop BPMN modeling application created specifically for Zeebe.

[You can download the most recent Zeebe Modeler release here.](https://github.com/zeebe-io/zeebe-modeler/releases)
