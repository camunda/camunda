/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import java.util.Set;
import java.util.UUID;
import org.agrona.concurrent.UnsafeBuffer;

interface ImmutableStreamRegistry<M> {

  /**
   * Returns a set of streams for the given type.
   *
   * <p>Implementations of this must be thread-safe.
   *
   * @param streamType type of the stream
   * @return set of streams for the given type
   */
  Set<StreamConsumer<M>> get(final UnsafeBuffer streamType);

  /**
   * A stream consumer uniquely identified by the id, with its properties and streamType.
   *
   * @param id unique id
   * @param properties properties of the stream
   * @param streamType type of the stream
   * @param <M> type of the properties
   */
  record StreamConsumer<M>(StreamId id, M properties, UnsafeBuffer streamType) {}

  /**
   * Uniquely identifies a stream
   *
   * @param streamId
   * @param receiver
   */
  record StreamId(UUID streamId, MemberId receiver) {}
}
