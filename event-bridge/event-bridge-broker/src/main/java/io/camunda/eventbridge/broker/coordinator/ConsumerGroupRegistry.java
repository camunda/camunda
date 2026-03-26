/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.coordinator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory consumer group registry. Tracks live consumers, their assigned partitions, heartbeat
 * timestamps, committed offsets, and the current rebalance epoch.
 *
 * <p>This class is <em>not</em> thread-safe; it is intended to be accessed only from within the
 * coordinator broker's actor thread.
 */
public record ConsumerGroupRegistry(Map<String, ConsumerGroup> groups) {

  /**
   * Returns {@code true} if the consumer is currently active (registered and not evicted) in the
   * given group.
   */
  public boolean isConsumerActive(final String groupId, final String memberId) {
    return Optional.ofNullable(groups.get(groupId))
        .map(g -> g.isActiveConsumer(memberId))
        .orElse(false);
  }

  public void addGroup(final ConsumerGroup group) {
    groups.put(group.getGroupId(), group);
  }

  /** Returns the group for the given ID, or {@code null} if it does not exist. */
  public ConsumerGroup getGroup(final String groupId) {
    return groups.get(groupId);
  }

  public List<ConsumerGroup> getAllGroups() {
    return new ArrayList<>(groups.values());
  }
}
