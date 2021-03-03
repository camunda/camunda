/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.behavior;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import java.util.function.Consumer;

public final class TypedResponseWriterProxy implements TypedResponseWriter {

  private TypedResponseWriter writer;
  private Consumer<TypedResponseWriter> flushCallback;

  public void wrap(
      final TypedResponseWriter writer, final Consumer<TypedResponseWriter> flushCallback) {
    this.writer = writer;
    this.flushCallback = flushCallback;
  }

  @Override
  public void writeRejectionOnCommand(
      final TypedRecord<?> command, final RejectionType type, final String reason) {
    writer.writeRejectionOnCommand(command, type, reason);
  }

  @Override
  public void writeEvent(final TypedRecord<?> event) {
    writer.writeEvent(event);
  }

  @Override
  public void writeEventOnCommand(
      final long eventKey,
      final Intent eventState,
      final UnpackedObject eventValue,
      final TypedRecord<?> command) {
    writer.writeEventOnCommand(eventKey, eventState, eventValue, command);
  }

  @Override
  public void writeResponse(
      final long eventKey,
      final Intent eventState,
      final UnpackedObject eventValue,
      final ValueType valueType,
      final long requestId,
      final int requestStreamId) {
    writer.writeResponse(eventKey, eventState, eventValue, valueType, requestId, requestStreamId);
  }

  @Override
  public boolean flush() {
    flushCallback.accept(writer);
    return true;
  }

  @Override
  public void reset() {
    writer.reset();
  }
}
