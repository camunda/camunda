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
import io.camunda.zeebe.logstreams.impl.flowcontrol.AppendInFlight;
import io.camunda.zeebe.logstreams.impl.flowcontrol.AppenderFlowControl;
import io.camunda.zeebe.logstreams.impl.serializer.SequencedBatchSerializer;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

/** Consume the write buffer and append the blocks to the distributedlog. */
final class LogStorageAppender extends Actor implements HealthMonitorable, AppendErrorHandler {
  public static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;
  private final String name;
  private final AppenderFlowControl flowControl;
  private final Sequencer sequencer;
  private final SequencedBatchSerializer serializer = new SequencedBatchSerializer();
  private final LogStorage logStorage;
  private final Set<FailureListener> failureListeners = new HashSet<>();
  private final ActorFuture<Void> closeFuture;
  private final int partitionId;

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
    final var inflightAppend = flowControl.tryAcquire();
    if (inflightAppend == null) {
      actor.submit(this::tryWriteBatch);
      return;
    }
    writeBatch(inflightAppend);
  }

  private void writeBatch(final AppendInFlight append) {
    final var sequencedBatch = sequencer.tryRead();
    if (sequencedBatch == null) {
      append.discard();
      return;
    }

    final var lowestPosition = sequencedBatch.firstPosition();
    final var highestPosition =
        sequencedBatch.firstPosition() + sequencedBatch.entries().size() - 1;
    final var serialized = serializer.serializeBatch(sequencedBatch);
    append.start(highestPosition);
    logStorage.append(lowestPosition, highestPosition, serialized, append);
    actor.submit(this::tryWriteBatch);
  }

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
}
