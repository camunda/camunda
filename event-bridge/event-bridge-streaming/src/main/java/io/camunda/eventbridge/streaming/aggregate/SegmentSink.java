/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import io.camunda.eventbridge.streaming.window.Windowed;

/**
 * Receives the immutable deltas {@link SegmentSealingAggregation} emits when it seals a segment —
 * one per changed {@code (key, window)} cell, tagged with the source partition and the sealed
 * segment index. The delta is the cell's accumulator folded from empty over just that segment's
 * positions, so downstream can merge each exactly once (deduped by the {@code (sourcePartition,
 * segment)} coordinate). The library defines the contract only; a consumer supplies the transport
 * (e.g. encoding the delta into a shuffle record and publishing it), so the aggregation stays
 * domain- and transport-agnostic.
 *
 * @param <K> the base grouping key type
 * @param <ACC> the accumulator (delta) type
 */
public interface SegmentSink<K, ACC> {

  /** Emits one sealed cell delta for {@code sourcePartition}'s segment {@code segment}. */
  void emit(Windowed<K> cell, int sourcePartition, long segment, ACC delta);

  /** Makes everything emitted so far durable; called after a segment is fully sealed. */
  default void flush() {}
}
