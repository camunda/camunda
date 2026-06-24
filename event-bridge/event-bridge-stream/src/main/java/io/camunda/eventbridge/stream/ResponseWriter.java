/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.function.Supplier;

/**
 * Stages the response to a request-command — the event-bridge counterpart of the engine's {@code
 * TypedResponseWriter}. The response is attached to the processing result and flushed by the
 * platform through the configured {@code CommandResponseWriter} only <em>after the batch
 * commits</em>, so a client sees a reply only once the write is durable. Correlation rides on the
 * command's {@code requestId}/{@code requestStreamId} (set when the request was written), so the
 * flushing writer can route the reply back to the waiting caller.
 *
 * <p>The response value is any {@link UnpackedObject} the caller decodes — e.g. a {@code
 * CommitOffsetResponse} carrying an error code; success and rejection are both ordinary responses
 * here (event-bridge does not use Zeebe's command-rejection records on the wire).
 */
public final class ResponseWriter {

  private final Supplier<ProcessingResultBuilder> resultBuilder;

  ResponseWriter(final Supplier<ProcessingResultBuilder> resultBuilder) {
    this.resultBuilder = resultBuilder;
  }

  /** Stages {@code responseValue} as the successful reply to {@code command}. */
  public void respond(final TypedRecord<?> command, final UnpackedObject responseValue) {
    resultBuilder
        .get()
        .withResponse(
            RecordType.EVENT,
            command.getKey(),
            command.getIntent(),
            responseValue,
            command.getValueType(),
            RejectionType.NULL_VAL,
            "",
            command.getRequestId(),
            command.getRequestStreamId());
  }

  /**
   * Rejects {@code command} with a reason — the event-bridge counterpart of the engine's {@code
   * writeRejectionOnCommand}. The reply is a {@code COMMAND_REJECTION}, so the waiting caller
   * completes <em>exceptionally</em> (a rejected command is not a success). Use this for malformed
   * or invalid commands, not for protocol signals a client is expected to act on (those are
   * ordinary responses carrying a status, Kafka-style).
   */
  public void writeRejection(
      final TypedRecord<? extends UnifiedRecordValue> command,
      final RejectionType type,
      final String reason) {
    resultBuilder
        .get()
        .withResponse(
            RecordType.COMMAND_REJECTION,
            command.getKey(),
            command.getIntent(),
            command.getValue(),
            command.getValueType(),
            type,
            reason,
            command.getRequestId(),
            command.getRequestStreamId());
  }
}
