/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceAskState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;

/**
 * Records a {@link MessageStartProcessInstanceRequestIntent#EXPIRED_REJECTED} reply on {@code P_K}
 * by backing the pending cross-partition ask off rather than dropping it — the identical semantics
 * of {@link MessageStartProcessInstanceUniquenessRejectedV1Applier} / {@link
 * MessageStartProcessInstanceNoSubscriptionRejectedV1Applier}.
 *
 * <p>{@code P_B} sends this rejection when its deterministic TTL-gated expiry guard refuses a
 * past-deadline request ({@code messageDeadline <= now} and {@code messageTtl > 0}). Keeping the
 * ask — rather than removing it — is deliberate: removal stays exclusively owned by {@code P_K}'s
 * message-expiry path (on {@code P_K}'s clock, the authoritative clock for the message's
 * lifecycle), so a fast {@code P_B} clock can never drop the ask while the buffered message is
 * still alive and re-open the single-retry-owner guard to a fresh ask. The incremented rejection
 * count feeds the scheduler's exponential back-off, damping post-deadline retries until the message
 * expires locally and the expiry applier clears the ask.
 *
 * <p>{@code backOff} is a no-op when the entry is already absent (a racing success or a completed
 * message expiry), so a late reply is tolerated exactly like the other two rejections.
 *
 * <p>This applier is V1-only because the {@code EXPIRED_REJECTED} intent has no production stream
 * history (it is introduced together with this feature).
 */
final class MessageStartProcessInstanceExpiredRejectedV1Applier
    implements TypedEventApplier<
        MessageStartProcessInstanceRequestIntent, MessageStartProcessInstanceRequestRecord> {

  private final MutableMessageStartProcessInstanceAskState askState;

  MessageStartProcessInstanceExpiredRejectedV1Applier(
      final MutableMessageStartProcessInstanceAskState askState) {
    this.askState = askState;
  }

  @Override
  public void applyState(final long key, final MessageStartProcessInstanceRequestRecord value) {
    askState.backOff(value.getMessageKey(), value.getProcessDefinitionKey());
  }
}
