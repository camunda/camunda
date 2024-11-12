/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.transport.stream.api.RemoteStreamInfo;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

/**
 * Keep tracks of {@link StreamConsumer}s which are logically similar.
 *
 * @param <M> type of the properties
 */
record AggregatedRemoteStream<M>(LogicalId<M> logicalId, List<StreamConsumer<M>> streamConsumers)
    implements RemoteStreamInfo<M> {

  void addConsumer(final StreamConsumer<M> consumer) {
    streamConsumers.add(consumer);
  }

  void removeConsumer(final StreamConsumer<M> consumer) {
    streamConsumers.remove(consumer);
  }

  @Override
  public Collection<RemoteStreamId> consumers() {
    return streamConsumers.stream().map(StreamConsumer::id).collect(Collectors.toSet());
  }

  @Override
  public DirectBuffer streamType() {
    return logicalId().streamType();
  }

  @Override
  public M metadata() {
    return logicalId.metadata();
  }

  /**
   * A stream consumer uniquely identified by the id, with its properties and streamType.
   *
   * @param id unique id
   * @param logicalId logical id
   * @param <M> type of the properties
   */
  record StreamConsumer<M>(StreamId id, LogicalId<M> logicalId) {}

  /**
   * Uniquely identifies a stream
   *
   * @param streamId
   * @param receiver
   */
  record StreamId(UUID streamId, MemberId receiver) implements RemoteStreamId {}
}
