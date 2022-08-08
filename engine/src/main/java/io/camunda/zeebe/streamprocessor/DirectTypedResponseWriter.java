/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;

public interface DirectTypedResponseWriter extends SideEffectProducer, TypedResponseWriter {

  @Override
  void writeRejectionOnCommand(TypedRecord<?> command, RejectionType type, String reason);

  @Override
  void writeEvent(TypedRecord<?> event);

  @Override
  void writeEventOnCommand(
      long eventKey, Intent eventState, UnpackedObject eventValue, TypedRecord<?> command);

  @Override
  void writeResponse(
      long eventKey,
      Intent eventState,
      UnpackedObject eventValue,
      ValueType valueType,
      long requestId,
      int requestStreamId);

  void writeResponse(
      final RecordType recordType,
      final long key,
      final Intent intent,
      final UnpackedObject value,
      final ValueType valueType,
      final RejectionType rejectionType,
      final String rejectionReason,
      final long requestId,
      final int requestStreamId);

  /**
   * Submits the response to transport.
   *
   * @return false in case of backpressure, else true
   */
  @Override
  boolean flush();

  void reset();
}
