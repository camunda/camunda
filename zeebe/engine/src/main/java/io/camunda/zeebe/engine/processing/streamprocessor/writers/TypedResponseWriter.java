/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public interface TypedResponseWriter {

  void writeRejectionOnCommand(TypedRecord<?> command, RejectionType type, String reason);

  void writeRejection(
      final TypedRecord<?> command,
      final RejectionType type,
      final String reason,
      final long requestId,
      final int requestStreamId);

  void writeRejection(
      final long key,
      final Intent intent,
      final UnifiedRecordValue value,
      final ValueType valueType,
      final RejectionType type,
      final String reason,
      final long requestId,
      final int requestStreamId);

  void writeEvent(TypedRecord<?> event);

  void writeEventOnCommand(
      long eventKey, Intent eventState, UnpackedObject eventValue, TypedRecord<?> command);

  void writeResponse(
      long eventKey,
      Intent eventState,
      UnpackedObject eventValue,
      ValueType valueType,
      long requestId,
      int requestStreamId);
}
