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
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * The {@code CommandResponseWriter} the platform flushes a staged response through after a command
 * commits. Instead of sending over a command-API transport (event-bridge requests arrive over
 * cluster messaging), it serializes the response value to bytes and completes the waiting reply via
 * {@link RequestResponseBridge#complete}, keyed by the command's request id. The serialized bytes
 * are exactly the response value's encoding — the same wire format the gateway already decodes.
 */
public final class BridgingCommandResponseWriter implements CommandResponseWriter {

  private final RequestResponseBridge bridge;
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
    return this;
  }

  @Override
  public CommandResponseWriter valueType(final ValueType valueType) {
    return this;
  }

  @Override
  public CommandResponseWriter rejectionType(final RejectionType rejectionType) {
    return this;
  }

  @Override
  public CommandResponseWriter rejectionReason(final DirectBuffer rejectionReason) {
    return this;
  }

  @Override
  public CommandResponseWriter valueWriter(final BufferWriter value) {
    valueWriter = value;
    return this;
  }

  @Override
  public void tryWriteResponse(final int requestStreamId, final long requestId) {
    final var bytes = new byte[valueWriter.getLength()];
    valueWriter.write(new UnsafeBuffer(bytes), 0);
    valueWriter = null;
    bridge.complete(requestId, bytes);
  }
}
