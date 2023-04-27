/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import java.util.ArrayList;
import java.util.List;
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
  Set<AggregatedRemoteStream<M>> get(final UnsafeBuffer streamType);

  /**
   * Uniquely identifies a stream
   *
   * @param streamId
   * @param receiver
   */
  record StreamId(UUID streamId, MemberId receiver) {}

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
   * Keep tracks of {@link StreamConsumer}s which are logically similar.
   *
   * @param <M> type of the properties
   */
  class AggregatedRemoteStream<M> {
    private final LogicalId<M> logicalId;
    private final List<StreamConsumer<M>> streamConsumers = new ArrayList<>();

    AggregatedRemoteStream(final LogicalId<M> logicalId) {
      this.logicalId = logicalId;
    }

    void addConsumer(final StreamConsumer<M> consumer) {
      streamConsumers.add(consumer);
    }

    void removeConsumer(final StreamConsumer<M> consumer) {
      streamConsumers.remove(consumer);
    }

    public List<StreamConsumer<M>> getStreamConsumers() {
      return streamConsumers;
    }

    public LogicalId<M> getLogicalId() {
      return logicalId;
    }
  }
}
