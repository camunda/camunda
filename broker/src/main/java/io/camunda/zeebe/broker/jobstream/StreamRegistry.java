/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.atomix.cluster.MemberId;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A registry for remote streams. The streams are typically between broker and the gateway, where
 * the broker pushes data to the stream and the gateway consumes them.
 *
 * <p>A stream is uniquely identified by the streamId and the receiver. A stream has also an
 * associated type and properties. Two streams with the same type and properties can consume the
 * same data.
 *
 * @param <M> the type of the properties of the stream.
 */
public class StreamRegistry<M> {

  /**
   * Adds a stream receiver that can receive data from the stream with the given streamType.
   *
   * @param streamType type of the stream
   * @param streamId id of the stream. The pair (receiver, streamId) must uniquely identify the
   *     stream.
   * @param receiver The id of the node that receives data from the stream
   * @param properties properties used by the producer to generate data to be pushed to the stream
   */
  public void add(
      final UnsafeBuffer streamType,
      final long streamId,
      final MemberId receiver,
      final M properties) {}

  /**
   * Removes the stream.
   *
   * @param streamId id of the stream
   * @param receiver The id of the node that receives data from the stream
   */
  public void remove(final long streamId, final MemberId receiver) {}

  /**
   * Removes all stream from the given receiver
   *
   * @param receiver id of the node
   */
  public void removeAll(final MemberId receiver) {}
}
