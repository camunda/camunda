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
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.function.Supplier;

/**
 * Appends a {@code COMMAND_REJECTION} <em>record</em> to the log — the event-bridge counterpart of
 * the engine's {@code TypedRejectionWriter}. This makes the rejection part of the replicated stream
 * (visible to observability/exporters and replayed deterministically as a no-op, since rejections
 * change no state); it does <em>not</em> reply to the caller. To also fail the waiting request, the
 * processor pairs this with {@link ResponseWriter#writeRejection} — exactly as an engine processor
 * pairs {@code rejection().appendRejection} with {@code response().writeRejectionOnCommand}.
 */
public final class RejectionWriter {

  private final Supplier<ProcessingResultBuilder> resultBuilder;

  RejectionWriter(final Supplier<ProcessingResultBuilder> resultBuilder) {
    this.resultBuilder = resultBuilder;
  }

  public void appendRejection(
      final TypedRecord<? extends UnifiedRecordValue> command,
      final RejectionType type,
      final String reason) {
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND_REJECTION)
            .intent(command.getIntent())
            .rejectionType(type)
            .rejectionReason(reason);
    resultBuilder.get().appendRecord(command.getKey(), command.getValue(), metadata);
  }
}
