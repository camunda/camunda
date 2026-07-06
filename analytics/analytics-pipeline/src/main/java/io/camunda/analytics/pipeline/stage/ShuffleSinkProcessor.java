/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.eventbridge.streaming.processor.Processor;
import io.camunda.eventbridge.streaming.shuffle.CellDelta;
import io.camunda.eventbridge.streaming.shuffle.SegmentCell;
import java.util.Arrays;

/**
 * The Stage-1 shuffle sink node — the "sink connector" of the topology: it takes each {@link
 * SegmentCell} a cube-meter aggregate node seals and forwards, routes it to a facts partition by
 * {@code hash(streamId, key)} so every writer's contribution to a cell converges on one reducer,
 * and hands it to the shared {@link EnvelopePublisher}, which batches a segment's cells (across all
 * meters) into that segment's envelopes and publishes them to the facts topic.
 *
 * <p>A terminal node ({@code Out = Void}); it holds the only transport in Stage 1. {@code flush()}
 * publishes the batched envelopes for produce-before-commit.
 */
public final class ShuffleSinkProcessor implements Processor<SegmentCell, Void> {

  private final EnvelopePublisher publisher;
  private final int factsPartitions;

  public ShuffleSinkProcessor(final EnvelopePublisher publisher, final int factsPartitions) {
    this.publisher = publisher;
    this.factsPartitions = factsPartitions;
  }

  @Override
  public void process(final SegmentCell sealed) {
    final CellDelta cell = sealed.cell();
    // Facts partitions are 1-indexed; route a cell to one partition so its writers converge there.
    final int factsPartition =
        1 + Math.floorMod(31 * cell.streamId() + Arrays.hashCode(cell.key()), factsPartitions);
    publisher.add(sealed.producerPartition(), sealed.segment(), factsPartition, cell);
  }

  @Override
  public void flush() {
    publisher.flush();
  }
}
