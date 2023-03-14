/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.api;

import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;

public interface RemoteStream<M extends BufferReader, P extends BufferWriter> {

  M metadata();

  void push(final P payload, ErrorHandler<P> errorHandler);

  interface ErrorHandler<P> {
    void handleError(final Throwable error, P data);
  }
}
