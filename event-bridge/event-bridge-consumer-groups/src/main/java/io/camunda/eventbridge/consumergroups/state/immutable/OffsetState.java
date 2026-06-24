/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.immutable;

/**
 * Read view of the replicated committed-offset state for the coordinator — the immutable half of
 * the engine-style state split. Used on the stream-processing actor (by the offset
 * processor/applier); off-actor reads go through {@code OffsetQueryService}, not here.
 */
public interface OffsetState {

  /**
   * Returns the committed position for {@code (groupId, topic, partitionId)}, or {@code -1} if
   * none.
   */
  long getOffset(String groupId, String topic, int partitionId);
}
