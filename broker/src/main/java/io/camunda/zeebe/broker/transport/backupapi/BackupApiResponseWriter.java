/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.ResponseWriter;
import io.camunda.zeebe.protocol.impl.encoding.BackupStatusResponse;
import io.camunda.zeebe.protocol.management.MessageHeaderEncoder;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.impl.ServerResponseImpl;
import org.agrona.MutableDirectBuffer;

public final class BackupApiResponseWriter implements ResponseWriter {
  private final ServerResponseImpl response = new ServerResponseImpl();

  private boolean hasResponse = true;

  private BackupStatusResponse status;

  public BackupApiResponseWriter withStatus(final BackupStatusResponse response) {
    status = response;
    hasResponse = true;
    return this;
  }

  public BackupApiResponseWriter noResponse() {
    hasResponse = false;
    return this;
  }

  @Override
  public void tryWriteResponse(
      final ServerOutput output, final int partitionId, final long requestId) {
    if (hasResponse) {
      try {
        response.reset().writer(this).setPartitionId(partitionId).setRequestId(requestId);
        output.sendResponse(response);
      } finally {
        reset();
      }
    }
  }

  @Override
  public void reset() {
    response.reset();
    hasResponse = true;
  }

  @Override
  public int getLength() {
    if (hasResponse) {
      return MessageHeaderEncoder.ENCODED_LENGTH + status.getLength();
    } else {
      return 0;
    }
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    status.write(buffer, offset);
  }
}
