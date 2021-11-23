/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.camunda.zeebe.broker.transport.ApiRequestHandler.ResponseWriter;
import io.camunda.zeebe.transport.ServerOutput;
import org.agrona.MutableDirectBuffer;

public class CommandApiResponseWriter implements ResponseWriter {

  @Override
  public void tryWriteResponse(
      final ServerOutput output, final int partitionId, final long requestId) {}

  @Override
  public void reset() {}

  @Override
  public int getLength() {
    return 0;
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {}
}
