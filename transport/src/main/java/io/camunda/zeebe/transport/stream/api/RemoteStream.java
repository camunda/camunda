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

/**
 * A {@link RemoteStream} is a unidirectional stream from the Broker to a Gateway. A {@link
 * RemoteStream} allows consumers to push out payload of type {@link P} to a Gateway.
 *
 * <p>NOTE: it's up to consumers of this API to interpret the metadata {@link #metadata()} and its
 * relation to the payload.
 *
 * <p>NOTE: implementations of the {@link RemoteStream#push(BufferWriter)}, so the payload should be
 * immutable.
 *
 * @param <M> associated metadata with the stream
 * @param <P> the payload type that can be pushed to the stream
 */
public interface RemoteStream<M extends BufferReader, P extends BufferWriter> {
  /** Returns the stream's metadata */
  M metadata();

  /**
   * Pushes the given payload to the stream. Implementations of this are likely asynchronous; it's
   * recommended that callers ensure that the given payload is immutable, and that the error handler
   * does not close over any shared state.
   *
   * @param payload the data to push to the remote gateway
   */
  void push(final P payload);
}
