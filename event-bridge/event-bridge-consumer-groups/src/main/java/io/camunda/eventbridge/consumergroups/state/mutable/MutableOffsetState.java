/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.mutable;

import io.camunda.eventbridge.consumergroups.state.immutable.OffsetState;

/**
 * Write view of the replicated committed-offset state. Only the {@code OffsetCommittedApplier} uses
 * it; {@link #putOffset} is a raw write — the monotonic (never-rewind) decision lives in the
 * applier, not here.
 */
public interface MutableOffsetState extends OffsetState {

  /**
   * Stores the committed position for {@code (groupId, topic, partitionId)} (and updates the
   * mirror).
   */
  void putOffset(String groupId, String topic, int partitionId, long position);

  /** Rebuilds the mirror from durable state before processing starts (no concurrent access yet). */
  void seedMirror();
}
