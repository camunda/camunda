/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public interface ZpaStream<M extends BufferWriter, P extends BufferReader> {
  M metadata();

  DirectBuffer streamType();

  void push(P payload, ErrorHandler<P> errorHandler);

  void close();

  void close(final Throwable error);

  interface ErrorHandler<P> {
    void handleError(Throwable error, P payload);
  }
}
