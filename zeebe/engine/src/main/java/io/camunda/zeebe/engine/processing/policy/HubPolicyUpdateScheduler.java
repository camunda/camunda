/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.zeebe.engine.state.immutable.PolicyState;
import io.camunda.zeebe.engine.state.immutable.PolicyState.CommittedPolicy;
import io.camunda.zeebe.engine.state.immutable.PolicyUpdateState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.policy.PersistedPolicyUpdate;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polls Hub for policy updates on the leader of partition 1, and writes each update as {@code
 * APPEND_CHUNK} commands. The processors of the policy update apply and commit it from there.
 *
 * <p>Every cycle runs on {@link AsyncTaskGroup#HUB_POLICY}, so neither the stream processor nor
 * other scheduled tasks wait for Hub. A cycle never blocks on the {@link HubPolicyClient}: it
 * starts a poll and picks up the result in a later cycle, so the client may long-poll Hub.
 *
 * <p>A cycle does one of these, in this order:
 *
 * <ol>
 *   <li>It writes the chunks of the update it is sending, as many as fit into one batch; the rest
 *       follow in the next cycle, which starts at once.
 *   <li>It waits while the update it sent has not ended, or while another update is open.
 *   <li>It starts a poll from the committed Hub sequence, or picks up the result of a finished one.
 *       An update whose sequence is already committed is dropped.
 * </ol>
 *
 * <p>The state is read outside the stream processor, so it may already contain records that are not
 * committed in the log. That is safe here: the scheduler only runs on the leader, and the commands
 * it writes land in the log after those records, so they are lost together with them. Two reads may
 * also see different states; the open update is therefore read before the committed policy, so a
 * commit between the reads can only show a newer sequence, never an outdated one.
 *
 * <p>The commands the scheduler wrote are not visible in the state until they are processed. It
 * therefore remembers the update it sent until the state shows it committed, or open and then gone.
 * An update that never shows up as open, for example because its first chunk was rejected, is given
 * up after {@link #SENT_UPDATE_TIMEOUT}. Everything the scheduler remembers is lost on a leader
 * change; the next leader starts over from the state, and the engine ignores chunks that are
 * received again.
 */
@NullMarked
public final class HubPolicyUpdateScheduler implements StreamProcessorLifecycleAware {

  static final Duration WAIT_INTERVAL = Duration.ofSeconds(1);
  static final Duration RETRY_DELAY = Duration.ofSeconds(5);
  static final Duration SENT_UPDATE_TIMEOUT = Duration.ofSeconds(30);

  private static final Logger LOG = LoggerFactory.getLogger(HubPolicyUpdateScheduler.class);

  private final HubPolicyClient client;
  private final PolicyState policyState;
  private final PolicyUpdateState policyUpdateState;

  /** Whether the scheduler may schedule its next cycle; set by the stream processor lifecycle. */
  private volatile boolean shouldReschedule;

  /** Guards against a second chain of cycles, as a scheduled task survives a pause. */
  private final AtomicBoolean taskScheduled = new AtomicBoolean();

  private @Nullable ReadonlyStreamProcessorContext processingContext;
  private @Nullable InstantSource clock;

  // read and written only on the HUB_POLICY actor
  private final Deque<PolicyUpdateRecord> unsentChunks = new ArrayDeque<>();
  private @Nullable SentUpdate sentUpdate;
  private @Nullable CompletableFuture<List<PolicyUpdateRecord>> poll;

  public HubPolicyUpdateScheduler(
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory, final HubPolicyClient client) {
    final var scheduledTaskState = scheduledTaskStateFactory.get();
    policyState = scheduledTaskState.getPolicyState();
    policyUpdateState = scheduledTaskState.getPolicyUpdateState();
    this.client = client;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    if (context.getPartitionId() != Protocol.DEPLOYMENT_PARTITION) {
      return;
    }
    processingContext = context;
    clock = context.getClock();
    shouldReschedule = true;
    scheduleNext(Duration.ZERO);
  }

  @Override
  public void onResumed() {
    if (processingContext == null) {
      return;
    }
    shouldReschedule = true;
    scheduleNext(Duration.ZERO);
  }

  @Override
  public void onPaused() {
    shouldReschedule = false;
  }

  @Override
  public void onClose() {
    shouldReschedule = false;
  }

  @Override
  public void onFailed() {
    shouldReschedule = false;
  }

  TaskResult runCycle(final TaskResultBuilder resultBuilder) {
    taskScheduled.set(false);
    Duration nextDelay = RETRY_DELAY;
    try {
      nextDelay = cycle(resultBuilder);
    } catch (final RuntimeException e) {
      LOG.warn("Failed to receive a policy update from Hub; retrying in {}", RETRY_DELAY, e);
      unsentChunks.clear();
      sentUpdate = null;
      poll = null;
    } finally {
      scheduleNext(nextDelay);
    }
    return resultBuilder.build();
  }

  private Duration cycle(final TaskResultBuilder resultBuilder) {
    if (!unsentChunks.isEmpty()) {
      return writeUnsentChunks(resultBuilder);
    }

    final var openUpdate = policyUpdateState.getOpenUpdate();
    final long committedSeqNo =
        policyState.getCurrentPolicy().map(CommittedPolicy::seqNo).orElse(0L);

    if (sentUpdate != null && !hasEnded(sentUpdate, openUpdate, committedSeqNo)) {
      return WAIT_INTERVAL;
    }
    sentUpdate = null;
    if (openUpdate.isPresent()) {
      return WAIT_INTERVAL;
    }
    return pollHub(resultBuilder, committedSeqNo);
  }

  private boolean hasEnded(
      final SentUpdate sent,
      final Optional<PersistedPolicyUpdate> openUpdate,
      final long committedSeqNo) {
    if (committedSeqNo >= sent.seqNo) {
      return true;
    }
    if (openUpdate.isPresent()) {
      sent.seenOpen |= openUpdate.get().getUpdate().getUpdateId().equals(sent.updateId);
      return false;
    }
    if (sent.seenOpen) {
      LOG.warn(
          "Policy update '{}' with Hub sequence {} ended without being committed; polling Hub again",
          sent.updateId,
          sent.seqNo);
      return true;
    }
    if (now() >= sent.giveUpAt) {
      LOG.warn(
          "Policy update '{}' with Hub sequence {} was not opened within {}; polling Hub again",
          sent.updateId,
          sent.seqNo,
          SENT_UPDATE_TIMEOUT);
      return true;
    }
    return false;
  }

  private Duration pollHub(final TaskResultBuilder resultBuilder, final long committedSeqNo) {
    if (poll == null) {
      poll = client.poll(committedSeqNo);
    }
    final var finishedPoll = poll;
    if (!finishedPoll.isDone()) {
      return WAIT_INTERVAL;
    }
    poll = null;

    final List<PolicyUpdateRecord> chunks;
    try {
      chunks = finishedPoll.join();
    } catch (final CompletionException e) {
      LOG.warn(
          "Failed to poll Hub after sequence {}; retrying in {}",
          committedSeqNo,
          RETRY_DELAY,
          e.getCause());
      return RETRY_DELAY;
    }
    if (chunks.isEmpty()) {
      return WAIT_INTERVAL;
    }

    final var first = chunks.getFirst();
    if (first.getSeqNo() <= committedSeqNo) {
      LOG.debug(
          "Drop policy update '{}' from Hub; its sequence {} is already committed",
          first.getUpdateId(),
          first.getSeqNo());
      return Duration.ZERO;
    }
    sentUpdate =
        new SentUpdate(
            first.getUpdateId(), first.getSeqNo(), now() + SENT_UPDATE_TIMEOUT.toMillis());
    unsentChunks.addAll(chunks);
    return writeUnsentChunks(resultBuilder);
  }

  private Duration writeUnsentChunks(final TaskResultBuilder resultBuilder) {
    boolean wroteAny = false;
    while (!unsentChunks.isEmpty()) {
      if (!resultBuilder.appendCommandRecord(
          PolicyUpdateIntent.APPEND_CHUNK, unsentChunks.getFirst())) {
        if (wroteAny) {
          return Duration.ZERO;
        }
        final var tooLarge = unsentChunks.getFirst();
        LOG.error(
            "Chunk {} of policy update '{}' is too large to be written; retrying in {}",
            tooLarge.getChunkIndex(),
            tooLarge.getUpdateId(),
            RETRY_DELAY);
        unsentChunks.clear();
        sentUpdate = null;
        return RETRY_DELAY;
      }
      unsentChunks.removeFirst();
      wroteAny = true;
    }
    return WAIT_INTERVAL;
  }

  private long now() {
    return Objects.requireNonNull(clock).millis();
  }

  private void scheduleNext(final Duration delay) {
    if (!shouldReschedule || !taskScheduled.compareAndSet(false, true)) {
      return;
    }
    Objects.requireNonNull(processingContext)
        .getScheduleService()
        .runDelayedAsync(delay, this::runCycle, AsyncTaskGroup.HUB_POLICY);
  }

  /** The update the scheduler wrote, until the state shows that it ended. */
  private static final class SentUpdate {
    private final String updateId;
    private final long seqNo;
    private final long giveUpAt;
    private boolean seenOpen;

    private SentUpdate(final String updateId, final long seqNo, final long giveUpAt) {
      this.updateId = updateId;
      this.seqNo = seqNo;
      this.giveUpAt = giveUpAt;
    }
  }
}
