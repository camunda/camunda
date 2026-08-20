/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.ResponseWriter;
import io.camunda.zeebe.transport.ServerOutput;
import org.agrona.MutableDirectBuffer;

/**
 * This is a no-op response writer. The reason for this is that the command API does not write a
 * response directly, instead it relies on the engine to eventually write the response.
 */
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
  public int write(final MutableDirectBuffer buffer, final int offset) {
    return 0;
  }
}
