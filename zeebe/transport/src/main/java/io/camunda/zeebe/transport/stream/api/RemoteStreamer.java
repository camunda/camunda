/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.api;

import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Optional;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;

/**
 * A {@link RemoteStreamer} allows to push data back to a single gateway (any). It keeps track of
 * multiple {@link RemoteStream} instances, each with their own streamType. The semantics of the
 * streamType, associated with the metadata and payload, are owned by the consumer of the API.
 *
 * @param <M> associated metadata with a single stream
 * @param <P> the payload type that can be pushed to the stream
 */
@FunctionalInterface
public interface RemoteStreamer<M, P extends BufferWriter> {
  /**
   * Returns a valid stream for the given streamType, or {@link Optional#empty()} if there is none.
   *
   * <p>The predicate should return false to exclude streams from the list of possible streams.
   *
   * @param streamType the job type to look for
   * @param filter a filter to include/exclude eligible job streams based on their properties
   * @return a job stream which matches the type and given filter, or {@link Optional#empty()} if
   *     none match
   */
  Optional<RemoteStream<M, P>> streamFor(DirectBuffer streamType, Predicate<M> filter);

  /**
   * Returns a valid stream for the given streamType, or {@link Optional#empty()} if there is none.
   */
  default Optional<RemoteStream<M, P>> streamFor(final DirectBuffer jobType) {
    return streamFor(jobType, ignored -> true);
  }
}
