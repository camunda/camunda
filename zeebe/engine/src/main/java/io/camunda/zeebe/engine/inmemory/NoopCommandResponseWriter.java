/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.inmemory;

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class NoopCommandResponseWriter implements CommandResponseWriter {

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
    return this;
  }

  @Override
  public void tryWriteResponse(final int requestStreamId, final long requestId) {
    // NOOP
  }
}
