/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import com.google.common.collect.Lists;
import io.camunda.zeebe.engine.metrics.MessageCorrelationMetrics;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartCorrelationKeyLockReleaseRecord;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduled reconciliation task on {@code P_K} that is the slow-path backstop for releasing the
 * correlation-key locks held for message-start instances created via the cross-partition handshake.
 *
 * <p>For a locally-started message-start instance the correlation-key lock is released when the
 * holder completes on the same partition. For a cross-partition start the holder lives on {@code
 * P_B}: on completion or termination {@code P_B} pushes a {@code RELEASE} straight to {@code P_K},
 * which is the fast path. This scheduler exists only to recover locks whose push was lost — e.g.
 * dropped inter-partition traffic — and therefore runs at a coarse cadence (see {@link
 * io.camunda.zeebe.engine.EngineConfiguration#DEFAULT_MESSAGE_START_LOCK_RELEASE_POLL_INTERVAL}).
 *
 * <p>Each tick it walks the cross-partition lock entries in local {@link MessageState}, groups them
 * by the target partition (derived from each holder instance key's partition bits, since every
 * Zeebe key encodes its generating partition), and dispatches a {@code QUERY} per target partition.
 * All of a partition's holders are reconciled on every tick; they are split into chunks of at most
 * {@code batchLimit} holders per {@code QUERY} so each {@code QUERY}/{@code QUERIED} record stays
 * well under the broker's max message size, no matter how many holders currently target that
 * partition. {@code P_B} replies {@code RELEASE} for any holder that is gone; the RELEASE command
 * processor on {@code P_K} then releases the correlation-key lock and picks up the next buffered
 * message for that key.
 *
 * <p>The poll set is fully reconstructable from local lock state, so the scheduler keeps no
 * transient bookkeeping and no cross-partition coordination state is persisted: every tick
 * reconciles purely from the current lock entries. When there is nothing to reconcile the tick is a
 * no-op.
 */
public final class CrossPartitionMessageStartLockReleaseScheduler
    implements Runnable, StreamProcessorLifecycleAware {

  private static final Logger LOG =
      LoggerFactory.getLogger(CrossPartitionMessageStartLockReleaseScheduler.class);

  private final int partitionId;
  private final SubscriptionCommandSender commandSender;
  private final MessageState messageState;
  private final Supplier<Duration> pollInterval;
  private final IntSupplier batchLimit;
  private final MessageCorrelationMetrics metrics;

  public CrossPartitionMessageStartLockReleaseScheduler(
      final int partitionId,
      final SubscriptionCommandSender commandSender,
      final MessageState messageState,
      final Supplier<Duration> pollInterval,
      final IntSupplier batchLimit,
      final MessageCorrelationMetrics metrics) {
    this.partitionId = partitionId;
    this.commandSender = commandSender;
    this.messageState = messageState;
    this.pollInterval = pollInterval;
    this.batchLimit = batchLimit;
    this.metrics = metrics;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    context.getScheduleService().runAtFixedRate(pollInterval.get(), this);
  }

  @Override
  public void run() {
    final int limit = batchLimit.getAsInt();
    if (limit <= 0) {
      // Defensive: a non-positive batch limit is a misconfiguration. Warn so it is diagnosable and
      // skip the tick rather than emitting empty queries and pointless inter-partition traffic.
      LOG.warn(
          "Skipping cross-partition message-start lock reconciliation: batch limit is {} but must "
              + "be positive; check the message-start lock-release poll batch-limit configuration.",
          limit);
      return;
    }

    // Group every cross-partition lock entry by the partition its holder lives on. Buffers handed
    // to the visitor are only valid during the callback, so copy into immutable values here.
    final Map<Integer, List<Lock>> locksByPartition = new HashMap<>();
    messageState.visitCrossPartitionStartLocks(
        (bpmnProcessId, correlationKey, holderProcessInstanceKey, tenantId) -> {
          final int targetPartition = Protocol.decodePartitionId(holderProcessInstanceKey);
          if (targetPartition == partitionId) {
            // Defensive: a cross-partition lock entry never targets the local partition. Skip
            // rather than poll ourselves.
            return;
          }
          locksByPartition
              .computeIfAbsent(targetPartition, p -> new ArrayList<>())
              .add(
                  new Lock(
                      BufferUtil.bufferAsString(bpmnProcessId),
                      BufferUtil.bufferAsString(correlationKey),
                      holderProcessInstanceKey,
                      tenantId));
        });

    locksByPartition.forEach(
        (targetPartition, locks) -> pollPartition(targetPartition, locks, limit));
  }

  private void pollPartition(final int targetPartition, final List<Lock> locks, final int limit) {
    // Reconcile every holder each tick, split into chunks of at most batchLimit so a single
    // QUERY/QUERIED record stays well under the broker's max message size no matter how many
    // cross-partition holders currently target this partition. Chunking (rather than a hard cap on
    // the first batchLimit entries) guarantees a holder sorted past the limit is still queried on
    // the same tick and cannot starve while the leading holders stay long-lived.
    Lists.partition(locks, limit).forEach(chunk -> sendQuery(targetPartition, chunk));
  }

  private void sendQuery(final int targetPartition, final List<Lock> chunk) {
    final var query =
        new MessageStartCorrelationKeyLockReleaseRecord()
            // requestKey only needs to carry P_K in its partition bits so P_B can route the reply
            // back; it is never used as an event key.
            .setRequestKey(Protocol.encodePartitionId(partitionId, 0L));
    for (final var lock : chunk) {
      query
          .addHolder()
          .setProcessInstanceKey(lock.holderProcessInstanceKey())
          .setBpmnProcessId(lock.bpmnProcessId())
          .setCorrelationKey(lock.correlationKey())
          .setTenantId(lock.tenantId());
    }

    LOG.trace(
        "Reconciling partition {} for {} cross-partition message-start holder(s)",
        targetPartition,
        chunk.size());
    commandSender.sendDirectCorrelationKeyLockReleaseQuery(targetPartition, query);
    metrics.lockReleaseQuerySent();
    metrics.lockReleaseQueryBatchSize(chunk.size());
  }

  private record Lock(
      String bpmnProcessId,
      String correlationKey,
      long holderProcessInstanceKey,
      String tenantId) {}
}
