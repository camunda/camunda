/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.transport.stream.api.RemoteStream;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;

public class RemoteStreamImpl<M extends BufferReader, P extends BufferWriter>
    implements RemoteStream<M, P> {

  private final M metadata;
  private final StreamPusher<P> streamer;

  public RemoteStreamImpl(final M metadata, final StreamPusher<P> streamer) {
    this.metadata = metadata;
    this.streamer = streamer;
  }

  @Override
  public M metadata() {
    return metadata;
  }

  @Override
  public void push(final P payload, final ErrorHandler<P> errorHandler) {
    streamer.pushAsync(payload, errorHandler);
  }
}
