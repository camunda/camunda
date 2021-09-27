/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.camunda.zeebe.protocol.record.ExecuteQueryResponseEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.impl.ServerResponseImpl;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class QueryResponseWriter implements BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final ExecuteQueryResponseEncoder responseEncoder = new ExecuteQueryResponseEncoder();
  private final ServerResponseImpl response = new ServerResponseImpl();
  private final DirectBuffer bpmnProcessId = new UnsafeBuffer();

  public void tryWriteResponse(
      final ServerOutput output, final int partitionId, final long requestId) {

    try {
      response.reset().writer(this).setPartitionId(partitionId).setRequestId(requestId);
      output.sendResponse(response);

    } finally {
      reset();
    }
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ExecuteQueryResponseEncoder.BLOCK_LENGTH
        + ExecuteQueryResponseEncoder.bpmnProcessIdHeaderLength()
        + bpmnProcessId.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, int offset) {
    // protocol header
    headerEncoder.wrap(buffer, offset);

    headerEncoder
        .blockLength(responseEncoder.sbeBlockLength())
        .templateId(responseEncoder.sbeTemplateId())
        .schemaId(responseEncoder.sbeSchemaId())
        .version(responseEncoder.sbeSchemaVersion());

    offset += headerEncoder.encodedLength();

    responseEncoder.wrap(buffer, offset);

    responseEncoder.putBpmnProcessId(bpmnProcessId, 0, bpmnProcessId.capacity());
  }

  public void reset() {
    bpmnProcessId.wrap(0, 0);
  }

  public QueryResponseWriter bpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId.wrap(bpmnProcessId);
    return this;
  }
}
