/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.pipeline;

import io.camunda.zeebe.logstreams.ImmutableRecordBatch;
import io.camunda.zeebe.logstreams.ImmutableRecordBatchEntry;
import io.camunda.zeebe.scheduler.ActorCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.ReentrantLock;

/** Accepts records from multiple writers, sequences them and assigns positions to each record */
public final class Sequencer {
  private long position;
  private ActorCondition consumer;
  private final Queue<SequencedRecordBatch> queue = new LinkedBlockingDeque<>();

  final ReentrantLock lock = new ReentrantLock();

  public Sequencer(final long initialPosition) {
    this.position = initialPosition;
  }

  public long offerBatch(final ImmutableRecordBatch batch, final long sourceRecordPosition) {
    lock.lock();
    try {
      final var startingPosition = position;
      final var sequencedEntries = new ArrayList<SequencedBatchEntry>();

      for (final var record : batch) {
        sequencedEntries.add(new SequencedBatchEntry(position++, record));
      }

      final var sequencedBatch =
          new SequencedRecordBatch(
              startingPosition, position, sourceRecordPosition, sequencedEntries);
      queue.offer(sequencedBatch);
      consumer.signal();
      return position;
    } finally {
      lock.unlock();
    }
  }

  public SequencedRecordBatch poll() {
    return queue.poll();
  }

  public void registerConsumer(final ActorCondition condition) {
    this.consumer = condition;
  }

  record SequencedRecordBatch(
      long lowestPosition,
      long highestPosition,
      long sourceEventPosition,
      List<SequencedBatchEntry> entries) {}

  record SequencedBatchEntry(long position, ImmutableRecordBatchEntry entry) {}
}
