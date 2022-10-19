/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.pipeline;

import io.camunda.zeebe.logstreams.impl.pipeline.Sequencer.SequencedRecordBatch;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.camunda.zeebe.scheduler.Actor;

/** Appends to log storage and applies backpressure based on how fast the log storage can commit. */
public class Appender extends Actor {
  private final AppendListener appendListener = new BackpressureApplier();
  private final Serializer serializer;
  private final Sequencer sequencer;
  private final LogStorage logStorage;

  public Appender(
      final Serializer serializer, final Sequencer sequencer, final LogStorage logStorage) {
    this.serializer = serializer;
    this.sequencer = sequencer;
    this.logStorage = logStorage;
  }

  @Override
  protected void onActorStarted() {
    final var condition = actor.onCondition("sequencer", this::pullBatch);
    sequencer.registerConsumer(condition);
  }

  private void pullBatch() {
    final var batch = sequencer.poll();
    if (batch == null) {
      return;
    }
    final var buffer = serializer.serializeBatch(batch);
    logStorage.append(batch.lowestPosition(), batch.highestPosition(), buffer, appendListener);

    actor.run(this::pullBatch);
  }

  private int calculateBatchSize(final SequencedRecordBatch batch) {
    // LogEntryDescriptor.headerLength()
    return 0;
  }

  private static final class BackpressureApplier implements AppendListener {}
}
