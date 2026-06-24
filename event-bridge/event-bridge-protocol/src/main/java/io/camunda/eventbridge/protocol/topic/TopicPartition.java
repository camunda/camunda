/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.topic;

/**
 * One partition of one topic — the unit the assignor balances and the consumer owns once a group
 * subscribes to more than one topic. Ordered by topic then partition so assignment is deterministic
 * (the assignor and reconciliation sort by it).
 */
public record TopicPartition(String topic, int partition) implements Comparable<TopicPartition> {

  @Override
  public int compareTo(final TopicPartition other) {
    final var byTopic = topic.compareTo(other.topic);
    return byTopic != 0 ? byTopic : Integer.compare(partition, other.partition);
  }

  @Override
  public String toString() {
    return topic + "-" + partition;
  }
}
