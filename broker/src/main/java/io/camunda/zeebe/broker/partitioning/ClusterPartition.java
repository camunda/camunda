package io.camunda.zeebe.broker.partitioning;

/**
 * Representation of a cluster-wide view of a partition. Calls against this object may be rerouted
 * to another node on the cluster (e.g. calls to write to the partition will be rerouted to the
 * leader node; calls to query the partition will be rerouted to a node which hosts the partition
 */
public interface ClusterPartition extends Partition {}
