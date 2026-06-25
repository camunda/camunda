/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.offset;

import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.consumergroups.state.immutable.OffsetState;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import io.camunda.zeebe.db.ZeebeDb;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The coordinator's off-actor read view of the committed offsets — the event-bridge counterpart of
 * the engine's {@code StateQueryService}. It does not touch column families itself: on first use it
 * builds a {@link DbOffsetState} on its <em>own</em> {@link ZeebeDb} context and delegates, so the
 * coordinator reads committed offsets off the stream-processing actor without sharing the
 * processor's flyweights. Offsets are unbounded (every group × every owned partition), so they are
 * read on demand rather than mirrored.
 *
 * <p>Must be used from a single actor (the {@link
 * io.camunda.eventbridge.consumergroups.membership.ConsumerGroupCoordinator}); the backing state is
 * created lazily so its context and flyweights belong to that reader thread.
 */
public final class OffsetQueryService {

  private final ZeebeDb<EventBridgeColumnFamilies> zeebeDb;
  private OffsetState state;

  public OffsetQueryService(final ZeebeDb<EventBridgeColumnFamilies> zeebeDb) {
    this.zeebeDb = zeebeDb;
  }

  /** A group's committed offsets ({@code (topic, partition) → position}), read from state. */
  public Map<TopicPartition, Long> committedOffsets(final String group) {
    return state().committedOffsets(group);
  }

  /**
   * The committed offsets for just the given partitions, point-read from state. A partition with no
   * committed offset maps to {@code -1}. An empty filter falls back to every offset for the group.
   */
  public Map<TopicPartition, Long> committedOffsets(
      final String group, final List<TopicPartition> filter) {
    if (filter.isEmpty()) {
      return committedOffsets(group);
    }
    final var state = state();
    final var offsets = new LinkedHashMap<TopicPartition, Long>();
    for (final var partition : filter) {
      offsets.put(partition, state.getOffset(group, partition.topic(), partition.partition()));
    }
    return offsets;
  }

  private OffsetState state() {
    if (state == null) {
      state = new DbOffsetState(zeebeDb, zeebeDb.createContext());
    }
    return state;
  }
}
