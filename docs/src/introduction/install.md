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
docker run camunda/zeebe:0.1.0
```

TODO:

* Ports
* Volumes
* Config

## Linux Distribution Packages

Coming Soon!
