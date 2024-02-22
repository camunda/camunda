/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.transport.RequestHandler;
import io.camunda.zeebe.transport.ServerOutput;
import org.agrona.DirectBuffer;

/**
 * This can be used when the actual handler is removed because the broker is not the leader anymore.
 */
public class NotPartitionLeaderHandler implements RequestHandler {

  @Override
  public void onRequest(
      final ServerOutput serverOutput,
      final int partitionId,
      final long requestId,
      final DirectBuffer buffer,
      final int offset,
      final int length) {
    final ErrorResponseWriter errorResponseWriter = new ErrorResponseWriter();
    errorResponseWriter
        .partitionLeaderMismatch(partitionId)
        .tryWriteResponseOrLogFailure(serverOutput, partitionId, requestId);
  }
}
