/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.engine.api.ProcessingResultBuilder;
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.agrona.concurrent.UnsafeBuffer;

public class ResultBuilderBackedTypedResponseWriter extends AbstractResultBuilderBackedWriter
    implements TypedResponseWriter {

  private final UnsafeBuffer stringWrapper = new UnsafeBuffer(0, 0);

  ResultBuilderBackedTypedResponseWriter(
      final Supplier<ProcessingResultBuilder> resultBuilderSupplier) {
    super(resultBuilderSupplier);
  }

  @Override
  public void writeRejectionOnCommand(
      final TypedRecord<?> command, final RejectionType type, final String reason) {
    final byte[] bytes = reason.getBytes(StandardCharsets.UTF_8);
    stringWrapper.wrap(bytes);

    resultBuilder()
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

  @Override
  public void writeEvent(final TypedRecord<?> event) {
    writeResponse(
        event.getKey(),
        event.getIntent(),
        event.getValue(),
        event.getValueType(),
        event.getRequestId(),
        event.getRequestStreamId());
  }

  @Override
  public void writeEventOnCommand(
      final long eventKey,
      final Intent eventState,
      final UnpackedObject eventValue,
      final TypedRecord<?> command) {
    writeResponse(
        eventKey,
        eventState,
        eventValue,
        command.getValueType(),
        command.getRequestId(),
        command.getRequestStreamId());
  }

  @Override
  public void writeResponse(
      final long eventKey,
      final Intent eventState,
      final UnpackedObject eventValue,
      final ValueType valueType,
      final long requestId,
      final int requestStreamId) {

    resultBuilder()
        .withResponse(
            RecordType.EVENT,
            eventKey,
            eventState,
            eventValue,
            valueType,
            RejectionType.NULL_VAL,
            "",
            requestId,
            requestStreamId);
  }
}
