/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartCorrelationKeyLockReleaseRecord;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduled task on {@code P_K} that releases the correlation-key locks held for message-start
 * instances created via the cross-partition handshake, by polling {@code P_B} for the completion of
 * each holder instance.
 *
 * <p>For a locally-started message-start instance the correlation-key lock is released when the
 * holder completes on the same partition. For a cross-partition start the holder lives on {@code
 * P_B}, which {@code P_K} cannot observe directly — so {@code P_K} polls. Each tick this scheduler
 * walks the cross-partition lock entries in local {@link MessageState}, groups them by the target
 * partition (derived from each holder instance key's partition bits, since every Zeebe key encodes
 * its generating partition), and dispatches one batched {@code QUERY} per target partition. {@code
 * P_B} replies {@code RELEASE} for any holder that is gone; the actual lock release and
 * buffered-message pick-up are wired in a later commit, so for now the responses are accepted but
 * trigger no release action.
 *
 * <p>The poll set is fully reconstructable from local lock state, so no cross-partition
 * coordination state is persisted. The per-entry back-off bookkeeping is transient and rebuilt from
 * an empty map on recovery: a newly observed lock entry is polled immediately and then backs off
 * exponentially (×2) up to the configured maximum, so a long-running holder does not keep polling
 * at the base rate. Entries that disappear from local state (their lock was released elsewhere) are
 * dropped from the bookkeeping. When there is nothing to poll the tick is a no-op.
 */
public final class CrossPartitionMessageStartLockReleaseScheduler
    implements Runnable, StreamProcessorLifecycleAware {

  private static final Logger LOG =
      LoggerFactory.getLogger(CrossPartitionMessageStartLockReleaseScheduler.class);

  private final int partitionId;
  private final SubscriptionCommandSender commandSender;
  private final MessageState messageState;
  private final Supplier<Duration> pollInterval;
  private final Supplier<Duration> maxBackoff;
  private final IntSupplier batchLimit;

  /**
   * Transient per-lock back-off bookkeeping, rebuilt on recovery (mirrors the pending-ask state).
   */
  private final Map<LockKey, PollState> backoffByLock = new HashMap<>();

  private InstantSource clock;

  /**
   * Monotonic tick counter used to sweep bookkeeping for locks that disappeared: every entry seen
   * in a tick is stamped with the current value, and entries left unstamped afterwards are dropped.
   */
  private long pollEpoch;

  public CrossPartitionMessageStartLockReleaseScheduler(
      final int partitionId,
      final SubscriptionCommandSender commandSender,
      final MessageState messageState,
      final Supplier<Duration> pollInterval,
      final Supplier<Duration> maxBackoff,
      final IntSupplier batchLimit) {
    this.partitionId = partitionId;
    this.commandSender = commandSender;
    this.messageState = messageState;
    this.pollInterval = pollInterval;
    this.maxBackoff = maxBackoff;
    this.batchLimit = batchLimit;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    clock = context.getClock();
    context.getScheduleService().runAtFixedRate(pollInterval.get(), this);
  }

  @Override
  public void run() {
    final long now = clock.millis();
    final long epoch = ++pollEpoch;

    // Single pass over the local lock entries: refresh/age the back-off bookkeeping, drop entries
    // for locks that disappeared, and materialise only the entries that are actually due into the
    // per-target-partition batches. Non-due entries (the common case under back-off) are not
    // copied.
    final Map<Integer, List<Lock>> dueByPartition = new HashMap<>();
    messageState.visitCrossPartitionStartLocks(
        (bpmnProcessId, correlationKey, holderProcessInstanceKey, tenantId) -> {
          // buffers are only valid during the callback, so copy into immutable strings
          final var key =
              new LockKey(
                  BufferUtil.bufferAsString(bpmnProcessId),
                  BufferUtil.bufferAsString(correlationKey));
          final var pollState =
              backoffByLock.computeIfAbsent(
                  key,
                  // A newly observed lock is polled immediately; its back-off starts at the base.
                  k -> new PollState(now, pollInterval.get().toMillis()));
          pollState.epoch = epoch;

          if (pollState.nextPollTime > now) {
            return;
          }
          final int targetPartition = Protocol.decodePartitionId(holderProcessInstanceKey);
          if (targetPartition == partitionId) {
            // Defensive: a cross-partition lock entry never targets the local partition. Skip
            // rather than poll ourselves.
            return;
          }
          dueByPartition
              .computeIfAbsent(targetPartition, p -> new ArrayList<>())
              .add(
                  new Lock(
                      key.bpmnProcessId(),
                      key.correlationKey(),
                      holderProcessInstanceKey,
                      tenantId));
        });

    // Drop bookkeeping for locks that are no longer present (released elsewhere): any entry not
    // re-stamped in this tick is gone from local state.
    backoffByLock.values().removeIf(pollState -> pollState.epoch != epoch);

    dueByPartition.forEach((targetPartition, due) -> pollPartition(targetPartition, due, now));
  }

  private void pollPartition(final int targetPartition, final List<Lock> due, final long now) {
    final int limit = batchLimit.getAsInt();
    final var query =
        new MessageStartCorrelationKeyLockReleaseRecord()
            // requestKey only needs to carry P_K in its partition bits so P_B can route the reply
            // back; it is never used as an event key.
            .setRequestKey(Protocol.encodePartitionId(partitionId, 0L));

    int batched = 0;
    for (final var lock : due) {
      if (batched == limit) {
        // Remaining due entries keep their nextPollTime in the past and are picked up next tick.
        break;
      }
      query
          .addHolder()
          .setProcessInstanceKey(lock.holderProcessInstanceKey())
          .setBpmnProcessId(lock.bpmnProcessId())
          .setCorrelationKey(lock.correlationKey())
          .setTenantId(lock.tenantId());
      advanceBackoff(lock.key(), now);
      batched++;
    }

    if (batched == 0) {
      // Nothing was batched (e.g. a non-positive batch limit). Skip the empty query so we don't
      // emit pointless inter-partition traffic and QUERIED events every tick.
      return;
    }

    LOG.trace(
        "Polling partition {} for {} cross-partition message-start holder(s)",
        targetPartition,
        batched);
    commandSender.sendDirectCorrelationKeyLockReleaseQuery(targetPartition, query);
  }

  private void advanceBackoff(final LockKey key, final long now) {
    final var pollState = backoffByLock.get(key);
    pollState.nextPollTime = now + pollState.intervalMillis;
    pollState.intervalMillis = Math.min(pollState.intervalMillis * 2, maxBackoff.get().toMillis());
  }

  private record Lock(
      String bpmnProcessId, String correlationKey, long holderProcessInstanceKey, String tenantId) {
    LockKey key() {
      return new LockKey(bpmnProcessId, correlationKey);
    }
  }

  /** Identifies a cross-partition lock entry; mirrors the lock column family's composite key. */
  private record LockKey(String bpmnProcessId, String correlationKey) {}

  private static final class PollState {
    private long nextPollTime;
    private long intervalMillis;
    private long epoch;

    private PollState(final long nextPollTime, final long intervalMillis) {
      this.nextPollTime = nextPollTime;
      this.intervalMillis = intervalMillis;
    }
  }
}
