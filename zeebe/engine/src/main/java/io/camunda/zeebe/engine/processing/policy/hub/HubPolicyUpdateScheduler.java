/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import io.camunda.fanout.client.FanoutClient;
import io.camunda.fanout.client.port.ClientState;
import io.camunda.zeebe.engine.processing.policy.PolicyUpdateOutcomeListener;
import io.camunda.zeebe.engine.processing.policy.hub.PolicyUpdateInbox.PendingUpdate;
import io.camunda.zeebe.engine.processing.policy.hub.PolicyUpdateOutcome.Applied;
import io.camunda.zeebe.engine.state.immutable.PolicyState;
import io.camunda.zeebe.engine.state.immutable.PolicyState.CommittedPolicy;
import io.camunda.zeebe.engine.state.immutable.PolicyUpdateState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.util.VersionUtil;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives policy change sets from Hub on the leader of partition 1, and writes each one as the
 * {@code APPEND_CHUNK} commands of a policy update. The processors of the policy update apply and
 * commit it from there.
 *
 * <p>The scheduler starts a {@link FanoutClient} once partition 1 starts processing, and closes it
 * when processing stops. The client gives each change set to a {@link PolicyChangeSetEventHandler},
 * which splits it and hands it over through a {@link PolicyUpdateInbox}. The client gives one
 * change set at a time and waits until it is reported, so at most one update is in flight.
 *
 * <p>Every cycle runs on {@link AsyncTaskGroup#HUB_POLICY}, so neither the stream processor nor
 * other scheduled tasks wait for Hub. A cycle does one of these, in this order:
 *
 * <ol>
 *   <li>It writes the chunks of the update it is sending, as many as fit into one batch; the rest
 *       follow in the next cycle, which starts at once.
 *   <li>It reports the outcome of the update it sent, once that outcome is committed in the log.
 *   <li>It takes the next update from the inbox. An update whose outcome is already known is
 *       reported at once; one that meets another open update waits until that one ends.
 * </ol>
 *
 * <p>The outcome is reported only once it is committed in the log: the processors tell {@link
 * PolicyUpdateOutcomes} from post-commit tasks, when an update's version is committed or the update
 * failed. The state the scheduler reads may hold records that are not committed yet, so it never
 * reports from it. It reads the state only to decide when to write: whether another update is open,
 * and whether the update it sent was opened at all. An update that is not opened within {@link
 * #SENT_UPDATE_TIMEOUT}, for example because its first chunk was rejected, is given up; the client
 * sends it again.
 *
 * <p>Everything the scheduler holds is lost on a leader change. The client of the old leader is
 * closed and its update given up, and the client of the next leader starts from the committed Hub
 * sequence. An update that is received again meets its own chunks, which the engine ignores, or its
 * committed outcome.
 */
@NullMarked
public final class HubPolicyUpdateScheduler implements StreamProcessorLifecycleAware {

  static final Duration WAIT_INTERVAL = Duration.ofSeconds(1);
  static final Duration RETRY_DELAY = Duration.ofSeconds(5);
  static final Duration SENT_UPDATE_TIMEOUT = Duration.ofSeconds(30);

  private static final Logger LOG = LoggerFactory.getLogger(HubPolicyUpdateScheduler.class);

  private final FanoutClientFactory clientFactory;
  private final PolicyState policyState;
  private final PolicyUpdateState policyUpdateState;
  private final PolicyUpdateOutcomes outcomes = new PolicyUpdateOutcomes();
  private final PolicyUpdateInbox inbox = new PolicyUpdateInbox();
  private final PolicyChangeSetEventHandler eventHandler;

  /** Whether the scheduler may schedule its next cycle; set by the stream processor lifecycle. */
  private volatile boolean shouldReschedule;

  /** Guards against a second chain of cycles, as a scheduled task survives a pause. */
  private final AtomicBoolean taskScheduled = new AtomicBoolean();

  private @Nullable ReadonlyStreamProcessorContext processingContext;
  private @Nullable InstantSource clock;

  /** Started by a cycle and closed by the lifecycle; both hold the lock of the scheduler. */
  private @Nullable FanoutClient client;

  // read and written only on the HUB_POLICY actor
  private @Nullable SentUpdate sentUpdate;

  public HubPolicyUpdateScheduler(
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final FanoutClientFactory clientFactory) {
    final var scheduledTaskState = scheduledTaskStateFactory.get();
    policyState = scheduledTaskState.getPolicyState();
    policyUpdateState = scheduledTaskState.getPolicyUpdateState();
    this.clientFactory = clientFactory;
    eventHandler =
        new PolicyChangeSetEventHandler(
            new PolicyChangeSetSplitter(PolicyChangeSetSplitter.DEFAULT_MAX_CHUNK_SIZE),
            inbox,
            VersionUtil.getVersion());
  }

  /** The listener that the processors tell about committed outcomes. */
  public PolicyUpdateOutcomeListener outcomeListener() {
    return outcomes;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    if (context.getPartitionId() != Protocol.DEPLOYMENT_PARTITION) {
      return;
    }
    processingContext = context;
    clock = context.getClock();
    startProcessing();
  }

  @Override
  public void onResumed() {
    if (processingContext == null) {
      return;
    }
    startProcessing();
  }

  @Override
  public void onPaused() {
    stopProcessing();
  }

  @Override
  public void onClose() {
    stopProcessing();
  }

  @Override
  public void onFailed() {
    stopProcessing();
  }

  TaskResult runCycle(final TaskResultBuilder resultBuilder) {
    taskScheduled.set(false);
    Duration nextDelay = RETRY_DELAY;
    try {
      nextDelay = cycle(resultBuilder);
    } catch (final RuntimeException e) {
      LOG.warn("Failed to send a policy update from Hub; retrying in {}", RETRY_DELAY, e);
      sentUpdate = null;
    } finally {
      scheduleNext(nextDelay);
    }
    return resultBuilder.build();
  }

  /**
   * Runs on the stream processor's actor before processing starts, when the state holds only the
   * committed log.
   */
  private void startProcessing() {
    outcomes.onProcessingStarted(
        policyState.getCurrentPolicy().map(CommittedPolicy::seqNo).orElse(0L));
    shouldReschedule = true;
    scheduleNext(Duration.ZERO);
  }

  private void stopProcessing() {
    final FanoutClient stoppedClient;
    synchronized (this) {
      shouldReschedule = false;
      stoppedClient = client;
      client = null;
    }
    if (stoppedClient != null) {
      // waits for a running handler call, which only hands the update over
      stoppedClient.close();
    }
    inbox.giveUpPending(
        new IllegalStateException("The engine stopped processing partition 1; send it again"));
  }

  private Duration cycle(final TaskResultBuilder resultBuilder) {
    if (!startClient()) {
      return WAIT_INTERVAL;
    }

    final var pending = inbox.peek();
    if (pending == null) {
      sentUpdate = null;
      return WAIT_INTERVAL;
    }

    if (sentUpdate == null || sentUpdate.pending != pending) {
      sentUpdate = null;
      return takeUpdate(resultBuilder, pending);
    }

    final var sent = sentUpdate;
    if (!sent.unsentChunks.isEmpty()) {
      return writeUnsentChunks(resultBuilder, sent);
    }
    return reportOutcome(sent);
  }

  /** Starts the client once, unless processing stopped; returns whether it runs. */
  private synchronized boolean startClient() {
    if (!shouldReschedule) {
      return false;
    }
    if (client == null) {
      final var started = clientFactory.create(eventHandler);
      started.start(new ClientState(outcomes.committedSeqNo(), null));
      client = started;
    }
    return true;
  }

  private Duration takeUpdate(final TaskResultBuilder resultBuilder, final PendingUpdate pending) {
    final var knownOutcome = outcomes.take(pending.updateId());
    if (knownOutcome.isPresent()) {
      inbox.complete(pending, knownOutcome.get());
      return Duration.ZERO;
    }
    if (pending.seqNo() <= outcomes.committedSeqNo()) {
      LOG.debug(
          "Policy update '{}' with Hub sequence {} is already committed; report it as applied",
          pending.updateId(),
          pending.seqNo());
      inbox.complete(pending, new Applied());
      return Duration.ZERO;
    }

    final var openUpdate = policyUpdateState.getOpenUpdate();
    if (openUpdate.isPresent()
        && !openUpdate.get().getUpdate().getUpdateId().equals(pending.updateId())) {
      // wait until the other update ends; an open update with the same ID is an earlier copy of
      // this one, whose missing chunks the engine buffers and whose duplicate chunks it ignores
      return WAIT_INTERVAL;
    }

    final var sent = new SentUpdate(pending, now() + SENT_UPDATE_TIMEOUT.toMillis());
    sentUpdate = sent;
    return writeUnsentChunks(resultBuilder, sent);
  }

  private Duration reportOutcome(final SentUpdate sent) {
    final var outcome = outcomes.take(sent.pending.updateId());
    if (outcome.isPresent()) {
      inbox.complete(sent.pending, outcome.get());
      sentUpdate = null;
      return Duration.ZERO;
    }

    if (!sent.seenOpen) {
      sent.seenOpen =
          policyUpdateState
              .getOpenUpdate()
              .filter(open -> open.getUpdate().getUpdateId().equals(sent.pending.updateId()))
              .isPresent();
    }
    if (!sent.seenOpen && now() >= sent.giveUpAt) {
      LOG.warn(
          "Policy update '{}' with Hub sequence {} was not opened within {}; give it up",
          sent.pending.updateId(),
          sent.pending.seqNo(),
          SENT_UPDATE_TIMEOUT);
      inbox.giveUp(
          sent.pending,
          new TimeoutException(
              "Policy update '%s' was not opened within %s"
                  .formatted(sent.pending.updateId(), SENT_UPDATE_TIMEOUT)));
      sentUpdate = null;
      return Duration.ZERO;
    }
    return WAIT_INTERVAL;
  }

  private Duration writeUnsentChunks(final TaskResultBuilder resultBuilder, final SentUpdate sent) {
    boolean wroteAny = false;
    while (!sent.unsentChunks.isEmpty()) {
      if (!resultBuilder.appendCommandRecord(
          PolicyUpdateIntent.APPEND_CHUNK, sent.unsentChunks.getFirst())) {
        if (wroteAny) {
          return Duration.ZERO;
        }
        final var tooLarge = sent.unsentChunks.getFirst();
        LOG.error(
            "Chunk {} of policy update '{}' is too large to be written; give it up",
            tooLarge.getChunkIndex(),
            tooLarge.getUpdateId());
        inbox.giveUp(
            sent.pending,
            new IllegalStateException(
                "Chunk %d of policy update '%s' is too large to be written"
                    .formatted(tooLarge.getChunkIndex(), tooLarge.getUpdateId())));
        sentUpdate = null;
        return RETRY_DELAY;
      }
      sent.unsentChunks.removeFirst();
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

  /** The update the scheduler is sending, until its outcome is reported or it is given up. */
  private static final class SentUpdate {
    private final PendingUpdate pending;
    private final Deque<PolicyUpdateRecord> unsentChunks;
    private final long giveUpAt;
    private boolean seenOpen;

    private SentUpdate(final PendingUpdate pending, final long giveUpAt) {
      this.pending = pending;
      unsentChunks = new ArrayDeque<>(pending.chunks());
      this.giveUpAt = giveUpAt;
    }
  }
}
