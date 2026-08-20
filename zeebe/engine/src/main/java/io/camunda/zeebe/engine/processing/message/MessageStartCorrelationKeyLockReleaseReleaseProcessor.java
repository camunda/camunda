/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.metrics.MessageCorrelationMetrics;
import io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.ReleaseResult;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBufferedMessageStartEventBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartCorrelationKeyLockReleaseRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageStartCorrelationKeyLockReleaseIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartCorrelationKeyLockReleaseRecordValue.MessageStartLockReleaseHolderValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the {@link MessageStartCorrelationKeyLockReleaseIntent#RELEASE} reply on {@code P_K =
 * hash(correlationKey)}, the partition that holds the correlation-key lock for a message-start
 * instance created via the cross-partition handshake.
 *
 * <p>{@code P_B} sends this reply once the holder instance it was polled for is gone. For each
 * holder in the reply {@code P_K} releases the lock and picks up the next buffered message for that
 * correlation key, restoring the behaviour a local start would have had when its holder completed
 * on the same partition.
 *
 * <p><b>Idempotency.</b> Inter-partition replies are at-least-once, and by the time a redelivered
 * reply arrives the lock may already have been released — possibly re-acquired by a
 * <em>different</em> instance for the same correlation key. The release therefore fires only while
 * the lock is still held by the exact instance the reply names ({@link
 * MessageState#getCrossPartitionStartLockHolder} matches the reply's holder key); otherwise the
 * holder is skipped. A reply whose holders are all skipped changes no state and is rejected as
 * redundant rather than releasing anything. This guarantees a stale reply can never release a
 * successor's lock.
 *
 * <p>The lock removal is applied as the state effect of the {@code RELEASED} event; the buffered
 * message pick-up runs in this processor immediately after, so it observes the freed lock and
 * re-routes the next buffered message through the normal correlation path.
 */
@ExcludeAuthorizationCheck
public final class MessageStartCorrelationKeyLockReleaseReleaseProcessor
    implements TypedRecordProcessor<MessageStartCorrelationKeyLockReleaseRecord> {

  private static final Logger LOG =
      LoggerFactory.getLogger(MessageStartCorrelationKeyLockReleaseReleaseProcessor.class);

  private static final String REDUNDANT_RELEASE_REJECTION_REASON =
      """
      Expected to release the correlation-key lock still held by the holder(s) named in the \
      release reply, but none of them holds it anymore (already released or re-acquired by another \
      instance)""";

  private final MessageState messageState;
  private final BpmnBufferedMessageStartEventBehavior bufferedMessageStartEventBehavior;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final MessageCorrelationMetrics metrics;

  public MessageStartCorrelationKeyLockReleaseReleaseProcessor(
      final MessageState messageState,
      final BpmnBufferedMessageStartEventBehavior bufferedMessageStartEventBehavior,
      final Writers writers,
      final MessageCorrelationMetrics metrics) {
    this.messageState = messageState;
    this.bufferedMessageStartEventBehavior = bufferedMessageStartEventBehavior;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    this.metrics = metrics;
  }

  @Override
  public void processRecord(final TypedRecord<MessageStartCorrelationKeyLockReleaseRecord> record) {
    final var reply = record.getValue();
    // getHolders() deep-copies the array on every call, so materialize it once and reuse it.
    final var reportedHolders = reply.getHolders();

    final var releasable =
        new MessageStartCorrelationKeyLockReleaseRecord().setRequestKey(reply.getRequestKey());
    for (final var holder : reportedHolders) {
      final var bpmnProcessId = BufferUtil.wrapString(holder.getBpmnProcessId());
      final var correlationKey = BufferUtil.wrapString(holder.getCorrelationKey());
      if (messageState.getCrossPartitionStartLockHolder(bpmnProcessId, correlationKey)
          == holder.getProcessInstanceKey()) {
        releasable
            .addHolder()
            .setProcessInstanceKey(holder.getProcessInstanceKey())
            .setBpmnProcessId(holder.getBpmnProcessId())
            .setCorrelationKey(holder.getCorrelationKey())
            .setTenantId(holder.getTenantId());
      }
    }

    if (!releasable.hasHolders()) {
      // Redundant reply: none of the reported holders still holds its correlation-key lock (already
      // released, or the lock was re-acquired by a newer instance for the same key). Reject rather
      // than silently returning, so the command is recorded as processed and cannot release a
      // successor's lock.
      rejectionWriter.appendRejection(
          record, RejectionType.INVALID_STATE, REDUNDANT_RELEASE_REJECTION_REASON);
      metrics.lockReleased(ReleaseResult.REDUNDANT);
      if (LOG.isDebugEnabled()) {
        LOG.atDebug()
            .addKeyValue(
                "holderProcessInstanceKeys",
                reportedHolders.stream()
                    .map(MessageStartLockReleaseHolderValue::getProcessInstanceKey)
                    .toList())
            .log(
                "Ignoring redundant correlation-key lock release (already released or re-acquired)");
      }
      return;
    }

    // Remove the correlation-key lock and its holder discriminator (applied synchronously) ...
    stateWriter.appendFollowUpEvent(
        record.getKey(), MessageStartCorrelationKeyLockReleaseIntent.RELEASED, releasable);

    // ... then pick up the next buffered message for each freed correlation key. This runs after
    // the lock removal above, so the pick-up sees the lock free and can trigger / re-route the next
    // buffered message through the normal correlation logic.
    final var releasableHolders = releasable.getHolders();
    for (final var holder : releasableHolders) {
      metrics.lockReleased(ReleaseResult.RELEASED);
      bufferedMessageStartEventBehavior.correlateNextBufferedMessage(
          BufferUtil.wrapString(holder.getBpmnProcessId()),
          BufferUtil.wrapString(holder.getCorrelationKey()),
          holder.getTenantId());
    }

    LOG.atDebug()
        .addKeyValue("releasedCount", releasableHolders.size())
        .log("Released correlation-key lock(s)");
  }
}
