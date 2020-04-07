# Scripts

This folder contains several scripts we wrote in order to test or debug things.

## Profile.sh

**Usage:**

```bash
kubectl exec -it zeebe-0 bash -- < profile.sh
```

This script will download and install java and the `async-profiler`. It will determine the PID of
the running `StandaloneBroker` and profile it via the `async-profiler`. The output of that would
be a flame graph.

This graph can then be copied for example like this:

```
kubectl cp zeebe-0:/tmp/profiler/flamegraph-2019-03-27_12-42-33.svg .
```

## PartitionDistribution.sh

**Usage:**

```bash
./partitionDistribution.sh {nodes} {partitionCount} {replicationFactor}
```

This script will calculate the distribution of partitions in a cluster.

_Example Output:_

```bash
$ ./partitionDistribution.sh 3 3 3
Distribution:
P\N|	N 0|	N 1|	N 2
P 0|	L  |	F  |	F  
P 1|	F  |	L  |	F  
P 2|	F  |	F  |	L  

Partitions per Node:
N 0: 3
N 1: 3
N 2: 3
```

## Partition.sh

**Usage:**

```bash
./partition.sh [--heal] <targetPodName>
```

This script will cause a partition or network interrupt for the given pod.

