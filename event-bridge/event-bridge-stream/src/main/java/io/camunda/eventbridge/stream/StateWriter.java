/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import java.util.function.Supplier;

/**
 * Writes follow-up events from a command processor and applies them in the same step — the
 * event-bridge counterpart of the engine's {@code EventApplyingStateWriter}. {@link
 * #appendFollowUpEvent} stages the event on the current {@link ProcessingResultBuilder}
 * <em>and</em> immediately applies it via the {@link EventApplier}, so the leader applies exactly
 * what a follower will later replay through the same applier. The record value supplies its own
 * {@code valueType} (see each record's {@code valueType()} override), so only the record type and
 * intent are stamped here.
 */
public final class StateWriter {

  private final Supplier<ProcessingResultBuilder> resultBuilder;
  private final EventApplier eventApplier;

  public StateWriter(
      final Supplier<ProcessingResultBuilder> resultBuilder, final EventApplier eventApplier) {
    this.resultBuilder = resultBuilder;
    this.eventApplier = eventApplier;
  }

  public void appendFollowUpEvent(
      final long key, final Intent intent, final UnifiedRecordValue value) {
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .intent(intent)
            .rejectionType(RejectionType.NULL_VAL)
            .rejectionReason("");

    resultBuilder.get().appendRecord(key, value, metadata);
    eventApplier.applyState(key, intent, value);
  }
}
