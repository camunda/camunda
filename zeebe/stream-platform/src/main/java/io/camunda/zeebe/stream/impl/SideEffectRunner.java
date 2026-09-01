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
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.PostCommitTask;
import io.camunda.zeebe.stream.api.ProcessingResponse;
import io.camunda.zeebe.stream.impl.metrics.ProcessingMetrics;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
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
 *
 * <p>The queue is bounded. Processing reads uncommitted records, so it can run arbitrarily far
 * ahead of the commit index while commits are slow, and every queued entry retains its responses
 * and post-commit task closures until it runs. {@link #hasCapacity()} lets the processing state
 * machine stop reading new records once the queue is full, which bounds that memory. Capacity frees
 * up on commit, which never depends on further processing, so this cannot deadlock.
 */
final class SideEffectRunner implements CommittedPositionListener {
  private static final Logger LOG = LoggerFactory.getLogger(SideEffectRunner.class);

  private final int partitionId;
  private final ActorControl actor;
  private final ProcessingMetrics processingMetrics;
  private final CommandResponseWriter responseWriter;
  private final int maxPendingSideEffects;
  private final Runnable onCapacityAvailable;
  private final Queue<SideEffects> sideEffects;
  private long highestCommittedPosition = StreamProcessor.UNSET_POSITION;
  private boolean executingSideEffects;
  private volatile boolean capacityExhausted;
  private volatile long capacityExhaustedSinceMillis;

  SideEffectRunner(
      final int partitionId,
      final ActorControl actor,
      final ProcessingMetrics processingMetrics,
      final CommandResponseWriter responseWriter,
      final int maxPendingSideEffects,
      final Runnable onCapacityAvailable) {
    this.actor = actor;
    this.processingMetrics = processingMetrics;
    this.responseWriter = responseWriter;
    this.partitionId = partitionId;
    this.maxPendingSideEffects = maxPendingSideEffects;
    this.onCapacityAvailable = onCapacityAvailable;
    sideEffects = new ArrayDeque<>();
  }

  /**
   * Whether another processing result's side effects fit in the queue.
   *
   * <p>Must be called on {@link #actor}'s thread. The processing state machine checks this before
   * it reads the next record, so the queue never grows beyond {@code maxPendingSideEffects}.
   */
  boolean hasCapacity() {
    return sideEffects.size() < maxPendingSideEffects;
  }

  /**
   * How long the queue has been continuously at {@code maxPendingSideEffects}, or {@link
   * Duration#ZERO} if it currently has room. Distinguishes a stall that has outlasted a normal
   * commit round trip from the brief, expected waits that happen under healthy operation.
   *
   * <p>Must be called on {@link #actor}'s thread to read a value consistent with {@link
   * #hasCapacity()}; the stream processor's health report calls it from other threads too, where it
   * still returns a safe, if possibly stale, answer.
   */
  Duration capacityExhaustedDuration() {
    return capacityExhausted
        ? Duration.ofMillis(
            Math.max(0, ActorClock.currentTimeMillis() - capacityExhaustedSinceMillis))
        : Duration.ZERO;
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
   *
   * <p>Unlike {@link #onCommittedPosition(long)} this must be called on {@link #actor}'s thread.
   * The only caller is the processing state machine, which already runs there, so it touches the
   * queue directly instead of paying for another actor job per processed record.
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
            new AggregatePostCommitTask(partitionId, position, postCommitTasks),
            completionCallback));
    processingMetrics.setPendingSideEffects(sideEffects.size());
    if (!capacityExhausted && !hasCapacity()) {
      // Ordered before the flag write below: a reader that observes capacityExhausted == true is
      // then guaranteed to observe this timestamp too.
      capacityExhaustedSinceMillis = ActorClock.currentTimeMillis();
      capacityExhausted = true;
    }
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
    try {
      sendResponses(sideEffects.responses());
      try (final var timer = processingMetrics.startBatchProcessingPostCommitTasksTimer()) {
        sideEffects.postCommitTask().flush();
      }
    } catch (final Exception e) {
      // Side effects are best-effort: an exception escaping here would be an uncaught failure in an
      // actor job, which fails the whole stream processor. It would also leave the queue stuck on
      // this entry forever, so no later side effect would ever run either.
      LOG.error(
          "Expected to execute the side effects of the processing result at position '{}' on partition '{}' successfully, but exception was thrown. The result carried {} response(s) and its post-commit tasks.",
          sideEffects.position(),
          partitionId,
          sideEffects.responses().size(),
          e);
    }
    completeSideEffects(sideEffects);
  }

  private void completeSideEffects(final SideEffects completedSideEffects) {
    final var wasFull = !hasCapacity();
    sideEffects.remove();
    processingMetrics.setPendingSideEffects(sideEffects.size());
    executingSideEffects = false;
    completedSideEffects.completionCallback().run();
    executeNextSideEffects();
    if (wasFull) {
      capacityExhausted = false;
      // Processing stopped reading records because the queue was full. Nothing else will wake it:
      // the records it stopped on are already appended, so no append notification is coming.
      // Signalling only on the full -> not-full transition keeps this to one job per stall
      // instead of one per side effect.
      onCapacityAvailable.run();
    }
  }

  private void sendResponses(final Collection<ProcessingResponse> responses) {
    for (final var processingResponse : responses) {
      final var responseValue = processingResponse.responseValue();
      final var recordMetadata = responseValue.recordMetadata();
      // Isolated per response, like the post-commit tasks: one client that cannot be answered must
      // not cost the others their response.
      try {
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
      } catch (final Exception e) {
        LOG.error(
            "Expected to send response '{}' for key '{}' on partition '{}' to request '{}' of stream '{}', but exception was thrown.",
            recordMetadata,
            responseValue.key(),
            partitionId,
            processingResponse.requestId(),
            processingResponse.requestStreamId(),
            e);
      }
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
    private final int partitionId;
    private final long position;
    private final List<PostCommitTask> postCommitTasks;

    private AggregatePostCommitTask(
        final int partitionId, final long position, final List<PostCommitTask> postCommitTasks) {
      this.partitionId = partitionId;
      this.position = position;
      this.postCommitTasks = postCommitTasks;
    }

    @Override
    public void flush() {
      for (int i = 0; i < postCommitTasks.size(); i++) {
        try {
          postCommitTasks.get(i).flush();
        } catch (final Exception e) {
          // The task is a lambda closed over by its processor, so its class name is the only handle
          // on which side effect failed. Its index identifies it among the tasks of the same
          // result.
          LOG.error(
              "Expected to execute post-commit task {} of {} ('{}') for the processing result at position '{}' on partition '{}' successfully, but exception was thrown.",
              i + 1,
              postCommitTasks.size(),
              postCommitTasks.get(i).getClass().getName(),
              position,
              partitionId,
              e);
        }
      }
    }
  }
}
