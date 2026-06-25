/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning;

/**
 * Reports a topic partition's elected Raft leader to the metadata-group leader, so leadership is
 * recorded in replicated state (from which topic readiness is derived). Called by {@link
 * PartitionLifecycle} when this node becomes the leader of a topic partition.
 *
 * <p>The implementation routes the report to the metadata leader and retries until it is durably
 * acknowledged. It lives where a routed client is available (the gateway, reusing its {@code
 * BrokerClient}); the broker side depends only on this callback, mirroring how {@code
 * BrokerRegistrar} reaches the metadata leader.
 */
@FunctionalInterface
public interface PartitionLeaderReporter {

  /** No-op reporter for partitions that are not topic-registry groups (e.g. the default group). */
  PartitionLeaderReporter NOOP = (topic, partitionId, leaderNode, term) -> {};

  void reportLeadership(String topic, int partitionId, int leaderNode, long term);
}
