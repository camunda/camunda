/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
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
public class RemoteStreamRegistry<M> implements ImmutableStreamRegistry<M> {
  private final RemoteStreamMetrics metrics;

  // Needs to be thread-safe for readers
  private final ConcurrentMap<UnsafeBuffer, Set<StreamConsumer<M>>> typeToConsumers =
      new ConcurrentHashMap<>();
  private final Map<StreamId, StreamConsumer<M>> idToConsumer = new HashMap<>();

  public RemoteStreamRegistry(final RemoteStreamMetrics metrics) {
    this.metrics = metrics;
  }

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
      final UUID streamId,
      final MemberId receiver,
      final M properties) {

    final StreamId uniqueId = new StreamId(streamId, receiver);
    if (idToConsumer.containsKey(uniqueId)) {
      return;
    }

    // Using CopyOnWriteArraySet assuming the size is small and updates/removal is less frequent. If
    // this is not the case, better use other thread-safe sets.
    typeToConsumers.putIfAbsent(streamType, new CopyOnWriteArraySet<>());

    final var streamConsumer = new StreamConsumer<>(uniqueId, properties, streamType);

    idToConsumer.put(uniqueId, streamConsumer);
    typeToConsumers.get(streamType).add(streamConsumer);
    metrics.addStream();
  }

  /**
   * Removes the stream.
   *
   * @param streamId id of the stream
   * @param receiver The id of the node that receives data from the stream
   */
  public void remove(final UUID streamId, final MemberId receiver) {
    final var uniqueId = new StreamId(streamId, receiver);
    final var consumer = idToConsumer.remove(uniqueId);
    if (consumer != null) {
      typeToConsumers.computeIfPresent(
          consumer.streamType(),
          (type, consumerSet) -> {
            consumerSet.remove(consumer);
            return consumerSet.isEmpty() ? null : consumerSet;
          });
      metrics.removeStream();
    }
  }

  /**
   * Removes all stream from the given receiver
   *
   * @param receiver id of the node
   */
  public void removeAll(final MemberId receiver) {
    final var streamOfReceiver =
        idToConsumer.keySet().stream().filter(id -> id.receiver().equals(receiver)).toList();

    streamOfReceiver.forEach(stream -> remove(stream.streamId(), stream.receiver()));
  }

  @Override
  public Set<StreamConsumer<M>> get(final UnsafeBuffer streamType) {
    return typeToConsumers.getOrDefault(streamType, Collections.emptySet());
  }

  public void clear() {
    typeToConsumers.clear();
    idToConsumer.clear();
  }
}
