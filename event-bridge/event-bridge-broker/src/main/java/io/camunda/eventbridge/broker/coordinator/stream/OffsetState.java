/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.coordinator.stream;

import java.util.Map;

/**
 * Replicated committed-offset state for the coordinator. Backed by the coordinator partition's
 * {@code ZeebeDb}, so it is rebuilt identically on every replica via stream replay and survives
 * leader failover (replacing the previous local-file {@code OffsetStore}).
 */
public interface OffsetState {

  /**
   * Commits a position for {@code (groupId, partitionId)} monotonically (never moves backwards) and
   * returns the resulting committed position.
   */
  long commit(String groupId, int partitionId, long position);

  /** Returns the committed position for {@code (groupId, partitionId)}, or {@code -1} if none. */
  long getOffset(String groupId, int partitionId);

  /**
   * Returns all committed offsets for a group as {@code partitionId → position} (possibly empty).
   */
  Map<Integer, Long> getOffsets(String groupId);
}
