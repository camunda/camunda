/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.transport.stream.api.RemoteStreamInfo;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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

  @Override
  public boolean isBlocked() {
    return streamConsumers.stream()
        .map(StreamConsumer::isBlocked)
        .reduce((a, b) -> a && b)
        .orElse(false);
  }

  /** A stream consumer uniquely identified by the id, with its properties and streamType. */
  static final class StreamConsumer<T> {
    private final StreamId id;
    private final LogicalId<T> logicalId;

    private volatile boolean blocked;

    StreamConsumer(final StreamId id, final LogicalId<T> logicalId) {
      this.id = id;
      this.logicalId = logicalId;
    }

    public StreamId id() {
      return id;
    }

    public LogicalId<T> logicalId() {
      return logicalId;
    }

    public void block() {
      blocked = true;
    }

    public void unblock() {
      blocked = false;
    }

    public boolean isBlocked() {
      return blocked;
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, logicalId);
    }

    @Override
    public boolean equals(final Object obj) {
      if (obj == this) {
        return true;
      }

      if (obj == null || obj.getClass() != getClass()) {
        return false;
      }

      final var that = (StreamConsumer<T>) obj;
      return Objects.equals(id, that.id) && Objects.equals(logicalId, that.logicalId);
    }

    @Override
    public String toString() {
      return "StreamConsumer[" + "id=" + id + ", " + "logicalId=" + logicalId + ']';
    }
  }

  /**
   * Uniquely identifies a stream
   *
   * @param streamId
   * @param receiver
   */
  record StreamId(UUID streamId, MemberId receiver) implements RemoteStreamId {}
}
