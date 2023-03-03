/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.stream.api.GatewayStreamer.Metadata;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import org.agrona.concurrent.UnsafeBuffer;

public final class StreamRegistry<M extends Metadata, P extends BufferWriter> {

  // Use unsafebuffer to ensure equals works
  // Map<UnsafeBuffer, Map<Metadata, Set<Recipient>>> streams = new ConcurrentHashMap<>();

  // jobType -> {Consumer(metadata, recipient)}
  // streamId -> jobType

  // Following is needed for retry
  // jobType -> Metadata
  // Metadata -> {Recipient}

  Map<UnsafeBuffer, Set<Consumer<M>>> streams;
  Map<Recipient, UnsafeBuffer> streamToJobType;

  // TODO: Keep maps streamId -< stream and memberId -> stream to help remove api

  public void add(
      final UnsafeBuffer streamType,
      final long streamId,
      final MemberId gateway,
      final M metadata) {
    // Should be concurrent-safe as well
    // streams.putIfAbsent(streamType, new CopyOnWriteArrayList<>());
    // TODO: check for duplicate streams from same gateway
    // streams.get(streamType).add(new Recipient<>(streamId, recipient, metadata));

    streams.putIfAbsent(streamType, new ConcurrentSkipListSet<>());
    final Recipient recipient = new Recipient(streamId, gateway);
    streams.get(streamType).add(new Consumer<>(metadata, recipient));
    streamToJobType.put(recipient, streamType);
  }

  public void remove(final long streamId, final MemberId recipient) {
    final Recipient o = new Recipient(streamId, recipient);
    final var jobType = streamToJobType.remove(o);
    // TODO null check jobType
    final var streamsOfType = streams.get(jobType);
    final var consumersToRemove =
        streamsOfType.stream().filter(consumer -> consumer.recipient.equals(o)).toList();
    streamsOfType.removeAll(consumersToRemove);

    if (streamsOfType.isEmpty()) {
      streams.remove(jobType);
    }
  }

  public void removeAll(final MemberId sender) {}

  record Recipient(long streamId, MemberId recipient) {}

  record Consumer<M extends Metadata>(M metadata, Recipient recipient) {}
}
