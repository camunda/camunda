/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.shuffle.CellDelta;
import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.eventbridge.streaming.aggregate.SegmentSink;
import io.camunda.eventbridge.streaming.window.Windowed;
import java.util.Arrays;

/**
 * The per-meter {@link SegmentSink} that turns a sealed cell delta into a {@link CellDelta} and
 * routes it to a facts partition, handing it to the shard's shared {@link EnvelopePublisher}. It
 * encodes the grouping key and the accumulator with the meter's own codecs, tags the entry with the
 * meter's {@code aggId}, and routes by {@code hash(aggId, keyBytes)} so every writer's contribution
 * to a cell converges on one reducer. One sink per compiled meter; they share the publisher so a
 * segment's cells across meters batch into that segment's envelopes.
 *
 * @param <ACC> the meter's accumulator type
 */
public final class CubeShuffleSink<ACC> implements SegmentSink<DimensionKey, ACC> {

  private final int aggId;
  private final RecordValue<DimensionKey> keyCodec;
  private final RecordValue<ACC> accCodec;
  private final EnvelopePublisher publisher;
  private final int factsPartitions;

  public CubeShuffleSink(
      final int aggId,
      final RecordValue<DimensionKey> keyCodec,
      final RecordValue<ACC> accCodec,
      final EnvelopePublisher publisher,
      final int factsPartitions) {
    this.aggId = aggId;
    this.keyCodec = keyCodec;
    this.accCodec = accCodec;
    this.publisher = publisher;
    this.factsPartitions = factsPartitions;
  }

  @Override
  public void emit(
      final Windowed<DimensionKey> cell,
      final int sourcePartition,
      final long segment,
      final ACC delta) {
    final byte[] keyBytes = keyCodec.toBytes(cell.key());
    final byte[] accBytes = accCodec.toBytes(delta);
    // Facts partitions are 1-indexed; route a cell to one partition so its writers converge there.
    final int factsPartition =
        1 + Math.floorMod(31 * aggId + Arrays.hashCode(keyBytes), factsPartitions);
    publisher.add(
        sourcePartition,
        segment,
        factsPartition,
        new CellDelta(aggId, cell.windowStart(), keyBytes, accBytes));
  }

  @Override
  public void flush() {
    publisher.flush();
  }
}
