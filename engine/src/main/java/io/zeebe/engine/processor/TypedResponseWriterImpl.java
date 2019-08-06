/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import java.nio.charset.StandardCharsets;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class TypedResponseWriterImpl implements TypedResponseWriter, SideEffectProducer {

  private final UnsafeBuffer stringWrapper = new UnsafeBuffer(0, 0);
  protected CommandResponseWriter writer;
  protected int partitionId;
  private long requestId;
  private int requestStreamId;
  private boolean isResponseStaged;

  public TypedResponseWriterImpl(CommandResponseWriter writer, int partitionId) {
    this.writer = writer;
    this.partitionId = partitionId;
  }

  @Override
  public void writeRejectionOnCommand(TypedRecord<?> command, RejectionType type, String reason) {
    final byte[] bytes = reason.getBytes(StandardCharsets.UTF_8);
    stringWrapper.wrap(bytes);

    stage(
        RecordType.COMMAND_REJECTION,
        command.getIntent(),
        command.getKey(),
        type,
        stringWrapper,
        command.getValueType(),
        command.getRequestId(),
        command.getRequestStreamId(),
        command.getValue());
  }

  @Override
  public void writeEvent(TypedRecord<?> event) {
    stringWrapper.wrap(0, 0);

    stage(
        RecordType.EVENT,
        event.getIntent(),
        event.getKey(),
        RejectionType.NULL_VAL,
        stringWrapper,
        event.getValueType(),
        event.getRequestId(),
        event.getRequestStreamId(),
        event.getValue());
  }

  @Override
  public void writeEventOnCommand(
      long eventKey, Intent eventState, UnpackedObject eventValue, TypedRecord<?> command) {
    stringWrapper.wrap(0, 0);

    stage(
        RecordType.EVENT,
        eventState,
        eventKey,
        RejectionType.NULL_VAL,
        stringWrapper,
        command.getValueType(),
        command.getRequestId(),
        command.getRequestStreamId(),
        eventValue);
  }

  public boolean flush() {
    if (isResponseStaged) {
      return writer.tryWriteResponse(requestStreamId, requestId);
    } else {
      return true;
    }
  }

  private void stage(
      RecordType type,
      Intent intent,
      long key,
      RejectionType rejectionType,
      DirectBuffer rejectionReason,
      ValueType valueType,
      long requestId,
      int requestStreamId,
      UnpackedObject value) {
    writer
        .partitionId(partitionId)
        .key(key)
        .intent(intent)
        .recordType(type)
        .valueType(valueType)
        .rejectionType(rejectionType)
        .rejectionReason(rejectionReason)
        .valueWriter(value);

    this.requestId = requestId;
    this.requestStreamId = requestStreamId;
    isResponseStaged = true;
  }

  public void reset() {
    isResponseStaged = false;
  }
}
