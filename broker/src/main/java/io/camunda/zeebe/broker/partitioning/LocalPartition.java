package io.camunda.zeebe.broker.partitioning;

/**
 * Representation of a locally available partition. Calls to this object will never be rerouted to
 * other brokers. But calls may fail, e.g. if a write operation is attempted, but the current broker
 * is not leader
 */
public interface LocalPartition extends Partition {}
