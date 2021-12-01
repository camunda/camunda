/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.adminapi;

import io.camunda.zeebe.broker.transport.ApiRequestHandler.ResponseWriter;
import io.camunda.zeebe.protocol.record.AdminResponseEncoder;
import io.camunda.zeebe.protocol.record.ExecuteQueryResponseEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.impl.ServerResponseImpl;
import org.agrona.MutableDirectBuffer;

public class ApiResponseWriter implements ResponseWriter {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final AdminResponseEncoder responseEncoder = new AdminResponseEncoder();
  private final ServerResponseImpl response = new ServerResponseImpl();

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
  public void reset() {}

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH + ExecuteQueryResponseEncoder.BLOCK_LENGTH;
  }

  @Override
  public void write(final MutableDirectBuffer buffer, int offset) {
    headerEncoder.wrap(buffer, offset);

    headerEncoder
        .blockLength(responseEncoder.sbeBlockLength())
        .templateId(responseEncoder.sbeTemplateId())
        .schemaId(responseEncoder.sbeSchemaId())
        .version(responseEncoder.sbeSchemaVersion());

    offset += headerEncoder.encodedLength();

    responseEncoder.wrap(buffer, offset);
  }
}
