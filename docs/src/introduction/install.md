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
docker run camunda/zeebe:0.1.0
```

TODO:

* Ports
* Volumes
* Config

## Linux Distribution Packages

Coming Soon!
