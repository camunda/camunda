/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;

public final class NoopResponseWriter implements TypedResponseWriter {

  @Override
  public void writeRejectionOnCommand(
      final TypedRecord<?> command, final RejectionType type, final String reason) {}

  @Override
  public void writeEvent(final TypedRecord<?> event) {}

  @Override
  public void writeEventOnCommand(
      final long eventKey,
      final Intent eventState,
      final UnpackedObject eventValue,
      final TypedRecord<?> command) {}

  @Override
  public void writeResponse(
      final long eventKey,
      final Intent eventState,
      final UnpackedObject eventValue,
      final ValueType valueType,
      final long requestId,
      final int requestStreamId) {}

  @Override
  public boolean flush() {
    return false;
  }

  @Override
  public void reset() {}
}
