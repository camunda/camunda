# Setting up a Zeebe Cluster

To setup a cluster you need to adjust the `cluster` section
in the Zeebe configuration file. Below is a snipped
of the default Zeebe configuration file, it should be self explanatory.

```toml
[cluster]

# This section contains all cluster related configurations, to setup an zeebe cluster

# Specifies the unique id of this broker node in a cluster.
# The id should be between 0 and number of nodes in the cluster (exclusive).
#
# This setting can also be overridden using the environment variable ZEEBE_NODE_ID.
# nodeId = 0

# Controls the number of partitions, which should exist in the cluster.
#
# This can also be overridden using the environment variable ZEEBE_PARTITIONS_COUNT.
# partitionsCount = 1

# Controls the replication factor, which defines the count of replicas per partition.
# The replication factor cannot be greater than the number of nodes in the cluster.
#
# This can also be overridden using the environment variable ZEEBE_REPLICATION_FACTOR.
# replicationFactor = 1

# Specifies the zeebe cluster size. This value is used to determine which broker
# is responsible for which partition.
#
# This can also be overridden using the environment variable ZEEBE_CLUSTER_SIZE.
# clusterSize = 1

# Allows to specify a list of known other nodes to connect to on startup
# The contact points of the management api must be specified.
# The format is [HOST:PORT]
# Example:
# initialContactPoints = [ "192.168.1.22:26502", "192.168.1.32:26502" ]
#
# This setting can also be overridden using the environment variable ZEEBE_CONTACT_POINTS
# specifying a comma-separated list of contact points.
#
# Default is empty list:
# initialContactPoints = []
```

# Example

In this example we will setup an Zeebe cluster with
five brokers. Each broker needs to get an unique node id.
To scale well, we will bootstrap five partitions
with an replication factor of three. For more information about this,
please take a look into the [Clustering](/basics/clustering.html) section.

The clustering setup will look like this:

![cluster](/operations/example-setup-cluster.png)

## Configuration

The configuration of the first broker could look like this:
```toml
[cluster]
nodeId = 0
partitionsCount = 5
replicationFactor = 3
clusterSize = 5
```

For the other brokers the configuration will slightly change.
```toml
[cluster]
nodeId = NODE_ID
partitionsCount = 5
replicationFactor = 3
clusterSize = 5
initialContactPoints = [ ADDRESS_AND_PORT_OF_NODE_0]
```

Each broker needs an unique node id. The ids should be in range of
zero and `clusterSize - 1`. You need to replace the `NODE_ID` placeholder with an
appropriate value. Furthermore the
brokers needs an initial contact point to start there gossip
conversation. Make sure that you use the address and
**management port** of another broker. You need to replace the
`ADDRESS_AND_PORT_OF_NODE_0` placeholder.

It is not necessary that each broker has the first
node as initial contact point, but it is easier
for the configuration. You could also configure more
brokers as initial contact points, to make sure that
the bootstrapping works without any problems.

## Partitions bootstrapping

On bootstrap, each node will create an partition matrix.

These matrix depends on the partitions count, replication factor and
the cluster size. If you did the configuration right and
used the same values for `partitionsCount`, `replicationFactor`
and `clusterSize` on each node, then all nodes will generate
the same partition matrix.

For the current example the matrix will look like the following:

<table>
<tr>
    <th></th>
    <th>Node 0</th>
    <th>Node 1</th>
    <th>Node 2</th>
    <th>Node 3</th>
    <th>Node 4</th>
</tr>

<!-- Partition 0 -->
<tr>
 <td><b>Partition 0</b></td>
 <td>Leader</td>
 <td>Follower</td>
 <td>Follower</td>
 <td>-</td>
 <td>-</td>
</tr>

<!-- Partition 1 -->
<tr>
 <td><b>Partition 1</b></td>
 <td>-</td>
 <td>Leader</td>
 <td>Follower</td>
 <td>Follower</td>
 <td>-</td>
</tr>

<!-- Partition 2 -->
<tr>
 <td><b>Partition 2</b></td>
 <td>-</td>
 <td>-</td>
 <td>Leader</td>
 <td>Follower</td>
 <td>Follower</td>
</tr>

<!-- Partition 3 -->
<tr>
 <td><b>Partition 3</b></td>
 <td>Follower</td>
 <td>-</td>
 <td>-</td>
 <td>Leader</td>
 <td>Follower</td>
</tr>

<!-- Partition 4 -->
<tr>
 <td><b>Partition 4</b></td>
 <td>Follower</td>
 <td>Follower</td>
 <td>-</td>
 <td>-</td>
 <td>Leader</td>
</tr>

</table>

The matrix ensures that the partitions are well distributed
between the different nodes. Furthermore it guarantees that
each node knows exactly, which partitions he has
to bootstrap and for which he will become leader as first (this
could change later, if he needs to step down for example).
