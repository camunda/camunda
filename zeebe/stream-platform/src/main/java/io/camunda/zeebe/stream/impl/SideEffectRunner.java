/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.logstreams.storage.LogStorage.CommittedPositionListener;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a processing result's side effects as soon as the processing result's entries are committed
 * by Raft.
 *
 * <p>Must run on the Processing State Machine's actor because side effects are not necessarily
 * thread-safe.
 */
final class SideEffectRunner implements CommittedPositionListener {
  private static final Logger LOG = LoggerFactory.getLogger(SideEffectRunner.class);

  private final int partitionId;
  private final ActorControl actor;
  private final ProcessingMetrics processingMetrics;
  private final CommandResponseWriter responseWriter;
  private final Queue<SideEffects> sideEffects;
  private long highestCommittedPosition = StreamProcessor.UNSET_POSITION;
  private boolean executingSideEffects;

  SideEffectRunner(
      final int partitionId,
      final ActorControl actor,
      final ProcessingMetrics processingMetrics,
      final CommandResponseWriter responseWriter) {
    this.actor = actor;
    this.processingMetrics = processingMetrics;
    this.responseWriter = responseWriter;
    this.partitionId = partitionId;
    sideEffects = new ArrayDeque<>();
  }

  /**
   * Executes side effects that have been registered for positions that have been committed. May be
   * called concurrently.
   */
  @Override
  public void onCommittedPosition(final long highestPosition) {
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
            position,
            responses,
            new AggregatePostCommitTask(position, postCommitTasks),
            completionCallback));
    processingMetrics.setPendingSideEffects(sideEffects.size());
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
    actor.submit(() -> executeSideEffects(nextSideEffects));
  }

  private void executeSideEffects(final SideEffects sideEffects) {
    sendResponses(sideEffects.responses());
    try (final var timer = processingMetrics.startBatchProcessingPostCommitTasksTimer()) {
      sideEffects.postCommitTask().flush();
    }
    completeSideEffects(sideEffects);
  }

  private void completeSideEffects(final SideEffects completedSideEffects) {
    sideEffects.remove();
    processingMetrics.setPendingSideEffects(sideEffects.size());
    executingSideEffects = false;
    completedSideEffects.completionCallback().run();
    executeNextSideEffects();
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
  private static final class AggregatePostCommitTask implements PostCommitTask {
    private final long position;
    private final List<PostCommitTask> postCommitTasks;

    private AggregatePostCommitTask(
        final long position, final List<PostCommitTask> postCommitTasks) {
      this.position = position;
      this.postCommitTasks = postCommitTasks;
    }

    @Override
    public void flush() {
      for (final PostCommitTask task : postCommitTasks) {
        try {
          task.flush();
        } catch (final Exception e) {
          LOG.error(
              "Expected to execute side effects for processing result at position '{}' successfully, but exception was thrown.",
              position,
              e);
        }
      }
    }
  }
}
