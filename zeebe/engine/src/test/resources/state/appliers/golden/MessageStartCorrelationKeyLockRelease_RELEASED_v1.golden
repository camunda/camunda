/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartCorrelationKeyLockReleaseRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartCorrelationKeyLockReleaseIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;

/**
 * Applier for {@link MessageStartCorrelationKeyLockReleaseIntent#RELEASED}.
 *
 * <p>Emitted on {@code P_K} when it accepts a {@code RELEASE} reply from {@code P_B} reporting that
 * a cross-partition message-start holder instance has completed. For each holder carried by the
 * event it drops the underlying correlation-key lock ({@link
 * MutableMessageState#removeActiveProcessInstance}) so the next buffered message for that key can
 * be picked up, removes the holder-instance discriminator that the pull-based release loop polls on
 * ({@link MutableMessageState#removeCrossPartitionStartLock}), and removes the process-instance ->
 * correlation-key row ({@link MutableMessageState#removeProcessInstanceCorrelationKey}) that {@code
 * P_K} wrote for the remote holder when it created it via the handshake — otherwise that row leaks
 * once per cross-partition start, since the completing element lives on {@code P_B} and never
 * reaches {@code P_K}'s local cleanup path.
 *
 * <p>The release decision — including the idempotency guard that ensures the lock is still held by
 * the exact instance the reply names — lives in the {@code RELEASE} processor; the event is only
 * written for holders that actually have a matching live lock, so the lock removal is
 * unconditional. The correlation-key row removal is guarded (the underlying delete throws on a
 * missing key), which also tolerates holders that never had one (empty correlation key) and replays
 * of a {@code RELEASE} whose row was already removed.
 */
final class MessageStartCorrelationKeyLockReleaseReleasedV1Applier
    implements TypedEventApplier<
        MessageStartCorrelationKeyLockReleaseIntent, MessageStartCorrelationKeyLockReleaseRecord> {

  private final MutableMessageState messageState;

  MessageStartCorrelationKeyLockReleaseReleasedV1Applier(final MutableMessageState messageState) {
    this.messageState = messageState;
  }

  @Override
  public void applyState(final long key, final MessageStartCorrelationKeyLockReleaseRecord value) {
    for (final var holder : value.getHolders()) {
      final var bpmnProcessId = BufferUtil.wrapString(holder.getBpmnProcessId());
      final var correlationKey = BufferUtil.wrapString(holder.getCorrelationKey());
      messageState.removeActiveProcessInstance(bpmnProcessId, correlationKey);
      messageState.removeCrossPartitionStartLock(bpmnProcessId, correlationKey);

      final var holderProcessInstanceKey = holder.getProcessInstanceKey();
      if (messageState.getProcessInstanceCorrelationKey(holderProcessInstanceKey) != null) {
        messageState.removeProcessInstanceCorrelationKey(holderProcessInstanceKey);
      }
    }
  }
}
