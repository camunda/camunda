/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;

public interface TypedResponseWriter {

  void writeRejectionOnCommand(TypedRecord<?> command, RejectionType type, String reason);

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
