/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.assignor;

import io.camunda.eventbridge.protocol.topic.TopicPartition;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartitionAssignment {

  private final Map<String, List<TopicPartition>> assignments;

  public PartitionAssignment(final Map<String, List<TopicPartition>> raw) {
    final var lists = new HashMap<String, List<TopicPartition>>();
    raw.forEach((consumer, partitions) -> lists.put(consumer, List.copyOf(partitions)));
    assignments = Collections.unmodifiableMap(lists);
  }

  public List<TopicPartition> forConsumer(final String memberId) {
    return assignments.getOrDefault(memberId, List.of());
  }

  public Map<String, List<TopicPartition>> assignments() {
    return assignments;
  }

  /** The assign/revoke delta and the full target for one member during reconciliation. */
  public record ReconciliationResult(
      List<TopicPartition> revoke, List<TopicPartition> assign, List<TopicPartition> assignment) {}
}
