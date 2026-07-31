/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.logstreams.storage.LogStorage.CommitListener;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.PostCommitTask;
import io.camunda.zeebe.stream.api.ProcessingResponse;
import io.camunda.zeebe.stream.impl.metrics.ProcessingMetrics;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a processing result's side effects as soon as the processing result's entries are committed
 * by Raft.
 *
 * <p>Must run on the Processing State Machine's actor because side effects are not necessarily
 * thread-safe.
 */
final class SideEffectRunner implements CommitListener {
  private static final long RETRY_DELAY_MILLIS = 1;
  private static final Logger LOG = Loggers.PROCESSOR_LOGGER;
  private static final String ERROR_MESSAGE_EXECUTE_SIDE_EFFECT_ABORTED =
      "Expected to execute side effects for processing result at position '{}' successfully, but exception was thrown.";

  private final int partitionId;
  private final ActorControl actor;
  private final ProcessingMetrics processingMetrics;
  private final CommandResponseWriter responseWriter;
  private final Queue<SideEffects> sideEffects;
  private final BooleanSupplier abortCondition;
  private long highestCommittedPosition = StreamProcessor.UNSET_POSITION;
  private boolean executingSideEffects;

  SideEffectRunner(
      final int partitionId,
      final ActorControl actor,
      final ProcessingMetrics processingMetrics,
      final CommandResponseWriter responseWriter,
      final BooleanSupplier abortCondition) {
    this.actor = actor;
    this.processingMetrics = processingMetrics;
    this.responseWriter = responseWriter;
    this.abortCondition = abortCondition;
    this.partitionId = partitionId;
    sideEffects = new ArrayDeque<>();
  }

  /**
   * Executes side effects that have been registered for positions that have been committed. May be
   * called concurrently.
   */
  @Override
  public void onCommit(final long highestPosition) {
    actor.run(
        () -> {
          highestCommittedPosition = Math.max(highestCommittedPosition, highestPosition);
          executeNextSideEffects();
        });
  }

  /**
   * Registers new side effects to run when it's {@link SideEffects#position} is committed. Must be
   * called sequentially and ordered by {@link SideEffects#position}.
   */
  void addSideEffects(
      final long position,
      final Collection<ProcessingResponse> responses,
      final List<PostCommitTask> postCommitTasks,
      final Runnable completionCallback) {
    sideEffects.add(
        new SideEffects(
            position, responses, new AggregatePostCommitTask(postCommitTasks), completionCallback));
    executeNextSideEffects();
  }

  private void executeNextSideEffects() {
    final var nextSideEffects = sideEffects.peek();
    if (executingSideEffects
        || nextSideEffects == null
        || nextSideEffects.position() > highestCommittedPosition) {
      return;
    }

    executingSideEffects = true;
    actor.submit(() -> executeSideEffectsWithRetry(nextSideEffects));
  }

  private void executeSideEffectsWithRetry(final SideEffects sideEffects) {
    try {
      if (executeSideEffects(sideEffects) || abortCondition.getAsBoolean()) {
        completeSideEffects(sideEffects);
      } else {
        actor.schedule(RETRY_DELAY_MILLIS, () -> executeSideEffectsWithRetry(sideEffects));
      }
    } catch (final Exception exception) {
      LOG.error(ERROR_MESSAGE_EXECUTE_SIDE_EFFECT_ABORTED, sideEffects.position(), exception);
      completeSideEffects(sideEffects);
    }
  }

  private void completeSideEffects(final SideEffects completedSideEffects) {
    sideEffects.remove();
    executingSideEffects = false;
    try {
      completedSideEffects.completionCallback().run();
    } finally {
      executeNextSideEffects();
    }
  }

  private boolean executeSideEffects(final SideEffects sideEffects) {
    sendResponses(sideEffects.responses());
    try (final var timer = processingMetrics.startBatchProcessingPostCommitTasksTimer()) {
      return sideEffects.postCommitTask().flush();
    }
  }

  private void sendResponses(final Collection<ProcessingResponse> responses) {
    for (final var processingResponse : responses) {
      final var responseValue = processingResponse.responseValue();
      final var recordMetadata = responseValue.recordMetadata();
      responseWriter
          .intent(recordMetadata.getIntent())
          .key(responseValue.key())
          .recordType(recordMetadata.getRecordType())
          .rejectionReason(BufferUtil.wrapString(recordMetadata.getRejectionReason()))
          .rejectionType(recordMetadata.getRejectionType())
          .partitionId(partitionId)
          .valueType(recordMetadata.getValueType())
          .valueWriter(responseValue.recordValue())
          .tryWriteResponse(processingResponse.requestStreamId(), processingResponse.requestId());
    }
  }

  record SideEffects(
      long position,
      Collection<ProcessingResponse> responses,
      PostCommitTask postCommitTask,
      Runnable completionCallback) {}

  /**
   * A holder for many {@link PostCommitTask} that runs all provided tasks together.
   *
   * @implNote Static to ensure that the aggregate does not capture the processing result. This
   *     allows early garbage collection of the written records. The task list is an immutable
   *     snapshot taken by the processing result when it is built.
   */
  private static class AggregatePostCommitTask implements PostCommitTask {
    private final List<PostCommitTask> postCommitTasks;

    private AggregatePostCommitTask(final List<PostCommitTask> postCommitTasks) {
      this.postCommitTasks = postCommitTasks;
    }

    @Override
    public boolean flush() {
      boolean aggregatedResult = true;
      for (final PostCommitTask task : postCommitTasks) {
        try {
          aggregatedResult = aggregatedResult && task.flush();
        } catch (final Exception e) {
          throw new RuntimeException(e);
        }
      }
      return aggregatedResult;
    }
  }
}
