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
 * <p>NOTE: implementations of the {@link RemoteStream#push(Object, ErrorHandler)} method are likely
 * asynchronous. As such, errors handled via the {@link ErrorHandler} may be executed after the
 * initial call. Callers should be careful with the state they close on in the implementations of
 * their {@link ErrorHandler}.
 *
 * @param <M> associated metadata with the stream
 * @param <P> the payload type that can be pushed to the stream
 */
public interface RemoteStream<M extends BufferReader, P extends BufferWriter> {

  M metadata();

  void push(final P payload, ErrorHandler<P> errorHandler);

  interface ErrorHandler<P> {
    void handleError(final Throwable error, P data);
  }
}
