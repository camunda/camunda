/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@link MessageStartProcessInstanceRequestIntent#REJECT_EXPIRED} command on {@code
 * P_K}, which is the rejection reply from {@code P_B} indicating that the deterministic TTL-gated
 * expiry guard refused a past-deadline request ({@code messageDeadline <= now} and {@code
 * messageTtl > 0}).
 *
 * <p>This processor writes the {@link MessageStartProcessInstanceRequestIntent#EXPIRED_REJECTED}
 * follow-up event whose applier backs the pending ask off — the identical semantics of the
 * uniqueness and no-subscription rejections. The pending ask is deliberately <em>kept</em>: its
 * removal remains exclusively owned by {@code P_K}'s message-expiry path (on {@code P_K}'s clock),
 * so a fast {@code P_B} clock can never drive {@code P_K}-side cleanup. The persisted rejection
 * count feeds the scheduler's exponential back-off, damping post-deadline retries until the message
 * expires locally and clears the ask.
 */
@ExcludeAuthorizationCheck
public final class MessageStartProcessInstanceRequestRejectExpiredProcessor
    implements TypedRecordProcessor<MessageStartProcessInstanceRequestRecord> {

  private final StateWriter stateWriter;

  public MessageStartProcessInstanceRequestRejectExpiredProcessor(final StateWriter stateWriter) {
    this.stateWriter = stateWriter;
  }

  @Override
  public void processRecord(final TypedRecord<MessageStartProcessInstanceRequestRecord> record) {
    stateWriter.appendFollowUpEvent(
        record.getKey(),
        MessageStartProcessInstanceRequestIntent.EXPIRED_REJECTED,
        record.getValue());
  }
}
