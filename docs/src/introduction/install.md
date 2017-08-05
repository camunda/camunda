# Install

This page guides you through the initial install of your Zeebe broker. In case you are looking for more detailed information on how to setup and operate Zeebe in production, make sure to check out the [Operations Guide](/operations/README.html) as well.

There are different ways to install Zeebe:

* Download a distribution
* Use Docker
* Use a Linux Distribution package manager _\(coming soon\)_

## Prerequisites

* Operating System:
  * Windows \(development only, not supported for production\)
  * Linux \(TODO: more details on distros and Filesystems\)
* Java Virtual Machine:
  * Oracle Hotspot v1.8+

## Download a distribution

You can always download the latest Zeebe release from the Github release page.

Once you have downloaded a distribution, extract it into a folder of your choice. In order to extract the Zeebe distribution and start the broker, **Linux users** can type:

```bash
$ tar -xzf Zeebe-VERSION.tar.gz -C Zeebe/
$ cd Zeebe/bin
$ ./broker
```

**Windows users** should probably download the `.zip`package and extract it using their favorite unzip tool. They can then open the extracted folder, navigate into the `bin`folder and start the broker by double-clicking on the `broker.bat` file.

Once the Zeebe broker has started, it should produce the following output:

```bash
TODO.
```

## Using Docker

You can run Zeebe with Docker:

```bash
docker run camunda-Zeebe/camunda-Zeebe
```

TODO:

* Ports
* Volumes
* Config

### Mac and Windows users (using docker-machine)

Create a VM with 2GB RAM using Docker Machine:

```
docker-machine create --driver virtualbox --virtualbox-memory 2000 zeebe
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
