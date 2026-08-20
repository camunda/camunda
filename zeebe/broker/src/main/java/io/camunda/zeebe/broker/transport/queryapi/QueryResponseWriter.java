/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.queryapi;

import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.ResponseWriter;
import io.camunda.zeebe.protocol.record.ExecuteQueryResponseEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.impl.ServerResponseImpl;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class QueryResponseWriter implements ResponseWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final ExecuteQueryResponseEncoder responseEncoder = new ExecuteQueryResponseEncoder();
  private final ServerResponseImpl response = new ServerResponseImpl();
  private final DirectBuffer bpmnProcessId = new UnsafeBuffer();

  @Override
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
  public void reset() {
    bpmnProcessId.wrap(0, 0);
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ExecuteQueryResponseEncoder.BLOCK_LENGTH
        + ExecuteQueryResponseEncoder.bpmnProcessIdHeaderLength()
        + bpmnProcessId.capacity();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, int offset) {
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

    return getLength();
  }

  public QueryResponseWriter bpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId.wrap(bpmnProcessId);
    return this;
  }
}
