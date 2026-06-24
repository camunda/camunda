/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * The {@code CommandResponseWriter} the platform flushes a staged response through after a command
 * commits. Instead of sending over a command-API transport (event-bridge requests arrive over
 * cluster messaging), it routes the reply to the waiting caller via {@link RequestResponseBridge},
 * keyed by the command's request id:
 *
 * <ul>
 *   <li>A success ({@code EVENT}) reply serializes the response value to bytes — the same wire
 *       format the gateway decodes — and {@link RequestResponseBridge#complete completes} the
 *       future.
 *   <li>A {@code COMMAND_REJECTION} reply {@link RequestResponseBridge#fail fails} the future with
 *       a {@link CommandRejectionException} carrying the rejection type and reason — a rejected
 *       command is not a success.
 * </ul>
 */
public final class BridgingCommandResponseWriter implements CommandResponseWriter {

  private final RequestResponseBridge bridge;

  private RecordType recordType = RecordType.EVENT;
  private RejectionType rejectionType = RejectionType.NULL_VAL;
  private String rejectionReason = "";
  private BufferWriter valueWriter;

  public BridgingCommandResponseWriter(final RequestResponseBridge bridge) {
    this.bridge = bridge;
  }

  @Override
  public CommandResponseWriter partitionId(final int partitionId) {
    return this;
  }

  @Override
  public CommandResponseWriter key(final long key) {
    return this;
  }

  @Override
  public CommandResponseWriter intent(final Intent intent) {
    return this;
  }

  @Override
  public CommandResponseWriter recordType(final RecordType type) {
    recordType = type;
    return this;
  }

  @Override
  public CommandResponseWriter valueType(final ValueType valueType) {
    return this;
  }

  @Override
  public CommandResponseWriter rejectionType(final RejectionType type) {
    rejectionType = type;
    return this;
  }

  @Override
  public CommandResponseWriter rejectionReason(final DirectBuffer reason) {
    rejectionReason = BufferUtil.bufferAsString(reason);
    return this;
  }

  @Override
  public CommandResponseWriter valueWriter(final BufferWriter value) {
    valueWriter = value;
    return this;
  }

  @Override
  public void tryWriteResponse(final int requestStreamId, final long requestId) {
    if (recordType == RecordType.COMMAND_REJECTION) {
      bridge.fail(requestId, new CommandRejectionException(rejectionType, rejectionReason));
    } else {
      final var bytes = new byte[valueWriter.getLength()];
      valueWriter.write(new UnsafeBuffer(bytes), 0);
      bridge.complete(requestId, bytes);
    }
    reset();
  }

  private void reset() {
    recordType = RecordType.EVENT;
    rejectionType = RejectionType.NULL_VAL;
    rejectionReason = "";
    valueWriter = null;
  }
}
