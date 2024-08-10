# Scripts

This folder contains several scripts we wrote in order to test or debug things.

## Profile.sh

**Usage:**
Run executeProfiling.sh with a pod name. It will run in your current namespace and copy two scripts to the given pod.

The first script `installProfiler.sh` will install all necessary dependencies to run the async profiler. The second script
`runProfiler.sh` will execute the profiler. The split was done, to be able to skip the installation phase.

If you run `executeProfiling` it makes sure that both scripts are executed on the given pod and copies at the end the resulting
flamegraph to your current working directory.

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

