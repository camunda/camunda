/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.immutable;

import io.camunda.eventbridge.protocol.topic.TopicPartition;
import java.util.Map;

/**
 * Read view of the replicated committed-offset state for the coordinator — the immutable half of
 * the engine-style state split. Backed by the coordinator partition's {@code ZeebeDb}, so it is
 * rebuilt identically on every replica via stream replay and survives leader failover.
 */
public interface OffsetState {

  /**
   * Returns the committed position for {@code (groupId, topic, partitionId)}, or {@code -1} if
   * none.
   */
  long getOffset(String groupId, String topic, int partitionId);

  /**
   * Thread-safe committed offsets for a group ({@code (topic, partition) → position}), read off the
   * processing actor (e.g. by the coordinator's heartbeat handler).
   */
  Map<TopicPartition, Long> offsetsSnapshot(String groupId);
}
