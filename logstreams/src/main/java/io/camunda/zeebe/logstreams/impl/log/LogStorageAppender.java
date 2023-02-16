/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.logstreams.impl.flowcontrol.AppendErrorHandler;
import io.camunda.zeebe.logstreams.impl.flowcontrol.AppenderFlowControl;
import io.camunda.zeebe.logstreams.impl.flowcontrol.InFlightAppend;
import io.camunda.zeebe.logstreams.impl.serializer.SequencedBatchSerializer;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

/** Consume the write buffer and append the blocks to the distributedlog. */
final class LogStorageAppender extends Actor implements HealthMonitorable, AppendErrorHandler {
  public static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;
  private final String name;
  private final AppenderFlowControl flowControl;
  private final Sequencer sequencer;
  private final LogStorage logStorage;
  private final Set<FailureListener> failureListeners = new HashSet<>();
  private final ActorFuture<Void> closeFuture;
  private final int partitionId;

  private final int batchSize = 8 * 1024;
  private final int linger = 0;
  private long lastWriteTimestamp;
  private SequencedBatches batches;

  LogStorageAppender(
      final String name,
      final int partitionId,
      final LogStorage logStorage,
      final Sequencer sequencer) {
    this.name = name;
    this.partitionId = partitionId;
    this.logStorage = logStorage;
    this.sequencer = sequencer;
    flowControl = new AppenderFlowControl(this, partitionId);
    closeFuture = new CompletableActorFuture<>();
    lastWriteTimestamp = System.currentTimeMillis();
    batches = new SequencedBatches();
  }

  @Override
  protected Map<String, String> createContext() {
    final var context = super.createContext();
    context.put(ACTOR_PROP_PARTITION_ID, Integer.toString(partitionId));
    return context;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  protected void onActorStarting() {
    sequencer.registerConsumer(actor.onCondition("sequencer", this::tryWriteBatch));
    actor.submit(this::tryWriteBatch);
  }

  @Override
  protected void onActorClosed() {
    closeFuture.complete(null);
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    if (actor.isClosed()) {
      return closeFuture;
    }
    super.closeAsync();
    return closeFuture;
  }

  @Override
  protected void handleFailure(final Throwable failure) {
    onFailure(failure);
  }

  @Override
  public void onActorFailed() {
    closeFuture.complete(null);
  }

  @Override
  public HealthReport getHealthReport() {
    return actor.isClosed()
        ? HealthReport.unhealthy(this).withMessage("actor is closed")
        : HealthReport.healthy(this);
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    actor.run(() -> failureListeners.add(failureListener));
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    actor.run(() -> failureListeners.remove(failureListener));
  }

  private void tryWriteBatch() {
    while (shouldContinueBatching()) {
      final var batch = sequencer.tryRead();
      batches.addSequencedBatch(batch);
    }

    final var inflightAppend = tryAcquireWriteSlot();
    if (inflightAppend.isPresent()) {
      writeBatch(inflightAppend.get());
//      lastWriteTimestamp = ActorClock.currentTimeMillis();
      batches = new SequencedBatches();
    }

    actor.submit(this::tryWriteBatch);
  }

  private boolean shouldContinueBatching() {
    final var batchesFlushable = batches.isFlushable();
    final var batchesLength = batches.getLength();

    if (batchesFlushable || batchesLength >= batchSize) {
      return false;
    }

    final var nextBatch = sequencer.tryPeek();
    if (nextBatch == null) {
      return false;
    }

    if (batchesLength > 0 && (batchesLength + nextBatch.getLength()) >= batchSize) {
      batches.setFlushable(true);
      return false;
    }

    return true;
  }

  private Optional<InFlightAppend> tryAcquireWriteSlot() {
    final var isFlushable = batches.isFlushable();
    final var length = batches.getLength();

    if (length > 0) {
      if (isFlushable || linger <= 0) {
        return flowControl.tryAcquire();
      }

//      final var currentTime = ActorClock.currentTimeMillis();
//      if ((currentTime - lastWriteTimestamp) >= linger) {
//        return flowControl.tryAcquire();
//      }
    }

    return Optional.empty();
  }

  private void writeBatch(final InFlightAppend append) {
    final var lowestPosition = batches.getLowestPosition();
    final var highestPosition = batches.getHighestPosition();

    append.start(highestPosition);
    logStorage.append(lowestPosition, highestPosition, batches, append);
  }

  //  private void writeBatch(final InFlightAppend append) {
  //    final var sequencedBatch = sequencer.tryRead();
  //    if (sequencedBatch == null) {
  //      append.discard();
  //      return;
  //    }
  //
  //    final var lowestPosition = sequencedBatch.firstPosition();
  //    final var highestPosition =
  //        sequencedBatch.firstPosition() + sequencedBatch.entries().size() - 1;
  //    append.start(highestPosition);
  //    logStorage.append(lowestPosition, highestPosition, sequencedBatch, append);
  //    actor.submit(this::tryWriteBatch);
  //  }

  private void onFailure(final Throwable error) {
    LOG.error("Actor {} failed in phase {}.", name, actor.getLifecyclePhase(), error);
    actor.fail(error);
    final var report = HealthReport.unhealthy(this).withIssue(error);
    failureListeners.forEach(l -> l.onFailure(report));
  }

  @Override
  public void onWriteError(final Throwable error) {
    actor.run(() -> onFailure(error));
  }

  @Override
  public void onCommitError(final Throwable error) {
    actor.run(() -> onFailure(error));
  }

  private static class SequencedBatches implements BufferWriter {

    private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[4 * 1024 * 1024]);
    private long lowestPosition = Long.MAX_VALUE;
    private long highestPosition = Long.MIN_VALUE;
    private int length = 0;
    private boolean flushable = false;

    public void addSequencedBatch(final SequencedBatch batch) {
      final var firstPosition = batch.firstPosition();
      final var lastPosition = firstPosition + batch.entries().size() - 1;

      lowestPosition = Math.min(lowestPosition, firstPosition);
      highestPosition = Math.max(highestPosition, lastPosition);

      batch.write(buffer, length);
      length += batch.getLength();
    }

    public long getLowestPosition() {
      return lowestPosition;
    }

    public long getHighestPosition() {
      return highestPosition;
    }

    public boolean isFlushable() {
      return flushable;
    }

    public void setFlushable(boolean flushable) {
      this.flushable = flushable;
    }

    @Override
    public int getLength() {
      return length;
    }

    @Override
    public void write(final MutableDirectBuffer buffer, final int offset) {
      buffer.putBytes(offset, this.buffer, 0, length);
    }
  }
}
