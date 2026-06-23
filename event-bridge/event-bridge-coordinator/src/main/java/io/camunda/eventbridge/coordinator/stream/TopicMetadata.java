/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.stream;

/**
 * The desired configuration of a topic as held in the coordinator's replicated registry (keyed by
 * topic name). Encoded to a compact string for {@link DbTopicState}.
 *
 * @param partitionCount number of partitions in the topic's Raft group
 * @param replicationFactor number of replicas per partition
 * @param status lifecycle status of the topic
 */
public record TopicMetadata(int partitionCount, int replicationFactor, TopicStatus status) {

  /** Lifecycle of a topic as it is provisioned, served, and torn down. */
  public enum TopicStatus {
    /** Registered; its Raft group is being provisioned across members. */
    CREATING,
    /** Provisioned and serving publish/poll. */
    ACTIVE,
    /** Marked for removal; its Raft group is being torn down. */
    DELETING
  }

  String encode() {
    return partitionCount + ";" + replicationFactor + ";" + status.name();
  }

  static TopicMetadata decode(final String encoded) {
    final var parts = encoded.split(";", 3);
    return new TopicMetadata(
        Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), TopicStatus.valueOf(parts[2]));
  }
}
