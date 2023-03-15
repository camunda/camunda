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
import java.util.Optional;
import org.agrona.DirectBuffer;

/**
 * A {@link RemoteStreamer} allows to push data back to a single gateway (any). It keeps track of
 * multiple {@link RemoteStream} instances, each with their own streamType. The semantics of the
 * streamType, associated with the metadata and payload, are owned by the consumer of the API.
 *
 * @param <M> associated metadata with a single stream
 * @param <P> the payload type that can be pushed to the stream
 */
public interface RemoteStreamer<M extends BufferReader, P extends BufferWriter> {
  Optional<RemoteStream<M, P>> streamFor(DirectBuffer streamType);
}
