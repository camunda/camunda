---

title: "Broker"
weight: 20

menu:
  main:
    identifier: "broker"
    parent: "get-started"

---

# Setup

## Prerequisites

* Java 8

## Start the Broker Distribution

1. Download the latest broker distribution from the [releases page](https://github.com/camunda-tngp/camunda-tngp/releases) or the [Nexus snapshot repository](https://app.camunda.com/nexus/content/repositories/camunda-tngp-snapshots/org/camunda/tngp/tngp-distribution/).
1. Extract its contents. We call it `${BROKER_HOME}` in the following.
1. To start up the broker, run the script `${BROKER_HOME}/bin/broker` (Linux, Mac) or `${BROKER_HOME}/bin/broker.bat` (Windows).

Known limitations on Windows:

* The batch script always uses the `java` executable in the path. It does not respect the `JAVA_HOME` environment variable.
* The shell script works fine with Cygwin, but not in an MSYS shell (e.g. git bash). Ask us on the issue tracker how to patch the script correctly for this case.

## Configure the Broker Distribution

The file `${BROKER_HOME}/conf/tngp.conf.toml` contains the broker's configuration. The default configuration should be good to go.

### `[task-queues]`

Defines a set of `[task-queue]` elements.

### `[task-queue]`

Properties:

* `id`: A numeric id of the task queue. Can be used in the BPMN XML to assign BPMN tasks to a queue.
* `name`: A human-readable name that describes the tasks managed in the queue.

### `[network.clientApi]`

Properties:

* `port`: Determines the port on which the broker listens to client request.