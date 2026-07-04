/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.eventbridge.streaming.aggregate.SegmentSink;
import io.camunda.eventbridge.streaming.shuffle.CellDelta;
import io.camunda.eventbridge.streaming.shuffle.SegmentCell;
import io.camunda.eventbridge.streaming.window.Windowed;
import java.util.function.Consumer;

/**
 * The per-meter {@link SegmentSink} that encodes a sealed cell delta into a domain-neutral {@link
 * SegmentCell} and forwards it to the shuffle-sink node downstream. It encodes the grouping key and
 * the accumulator with the meter's own codecs and tags the entry with the meter's {@code aggId}
 * (the shuffle {@code streamId}); routing, batching and transport are the sink node's job.
 *
 * <p>The downstream target is the owning node's {@code ProcessorContext.forward}, which only exists
 * once that node is wired, so it is {@link #bind bound} at the node's {@code init} — before any
 * record flows.
 *
 * @param <ACC> the meter's accumulator type
 */
public final class ForwardingSegmentSink<ACC> implements SegmentSink<DimensionKey, ACC> {

  private final int aggId;
  private final RecordValue<DimensionKey> keyCodec;
  private final RecordValue<ACC> accCodec;
  private Consumer<SegmentCell> downstream = cell -> {};

  public ForwardingSegmentSink(
      final int aggId, final RecordValue<DimensionKey> keyCodec, final RecordValue<ACC> accCodec) {
    this.aggId = aggId;
    this.keyCodec = keyCodec;
    this.accCodec = accCodec;
  }

  /**
   * Wires the downstream target — the owning node's {@code forward} — at that node's {@code init}.
   */
  public void bind(final Consumer<SegmentCell> downstream) {
    this.downstream = downstream;
  }

  @Override
  public void emit(
      final Windowed<DimensionKey> cell,
      final int sourcePartition,
      final long segment,
      final ACC delta) {
    downstream.accept(
        new SegmentCell(
            sourcePartition,
            segment,
            new CellDelta(
                aggId, cell.windowStart(), keyCodec.toBytes(cell.key()), accCodec.toBytes(delta))));
  }
}
