/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBufferedMessageStartEventBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartCorrelationKeyLockReleaseRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartCorrelationKeyLockReleaseIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;

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
 * holder is skipped and no {@code RELEASED} event is written. This guarantees a stale reply can
 * never release a successor's lock.
 *
 * <p>The lock removal is applied as the state effect of the {@code RELEASED} event; the buffered
 * message pick-up runs in this processor immediately after, so it observes the freed lock and
 * re-routes the next buffered message through the normal correlation path.
 */
@ExcludeAuthorizationCheck
public final class MessageStartCorrelationKeyLockReleaseReleaseProcessor
    implements TypedRecordProcessor<MessageStartCorrelationKeyLockReleaseRecord> {

  private final MessageState messageState;
  private final BpmnBufferedMessageStartEventBehavior bufferedMessageStartEventBehavior;
  private final StateWriter stateWriter;

  public MessageStartCorrelationKeyLockReleaseReleaseProcessor(
      final MessageState messageState,
      final BpmnBufferedMessageStartEventBehavior bufferedMessageStartEventBehavior,
      final Writers writers) {
    this.messageState = messageState;
    this.bufferedMessageStartEventBehavior = bufferedMessageStartEventBehavior;
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<MessageStartCorrelationKeyLockReleaseRecord> record) {
    final var reply = record.getValue();

    final var releasable =
        new MessageStartCorrelationKeyLockReleaseRecord().setRequestKey(reply.getRequestKey());
    for (final var holder : reply.getHolders()) {
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
      // every holder was already released (redelivered or superseded reply) — nothing to do
      return;
    }

    // Remove the correlation-key lock and its holder discriminator (applied synchronously) ...
    stateWriter.appendFollowUpEvent(
        record.getKey(), MessageStartCorrelationKeyLockReleaseIntent.RELEASED, releasable);

    // ... then pick up the next buffered message for each freed correlation key. This runs after
    // the lock removal above, so the pick-up sees the lock free and can trigger / re-route the next
    // buffered message through the normal correlation logic.
    for (final var holder : releasable.getHolders()) {
      bufferedMessageStartEventBehavior.correlateNextBufferedMessage(
          BufferUtil.wrapString(holder.getBpmnProcessId()),
          BufferUtil.wrapString(holder.getCorrelationKey()),
          holder.getTenantId());
    }
  }
}
