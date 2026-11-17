/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.transport.stream.impl.AggregatedRemoteStream.StreamConsumer;
import io.camunda.zeebe.transport.stream.impl.AggregatedRemoteStream.StreamId;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.UUID;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class RemoteStreamRegistryTest {

  private final TestRemoteStreamMetrics metrics = new TestRemoteStreamMetrics();
  private final RemoteStreamRegistry<Integer> streamRegistry = new RemoteStreamRegistry<>(metrics);
  private final MemberId gateway = MemberId.from("gateway");
  private final MemberId otherGateway = MemberId.from("gateway-other");
  private final UnsafeBuffer typeBar = new UnsafeBuffer(BufferUtil.wrapString("bar"));
  private final UnsafeBuffer typeFoo = new UnsafeBuffer(BufferUtil.wrapString("foo"));

  @Test
  void shouldAddMultipleStreams() {
    // given
    final UUID id1 = UUID.randomUUID();
    final UUID id2 = UUID.randomUUID();

    // when
    final var addedFoo = streamRegistry.add(typeFoo, id1, gateway, 1);
    final var addedBar = streamRegistry.add(typeBar, id2, gateway, 2);

    // then
    assertThat(addedFoo).isTrue();
    assertThat(addedBar).isTrue();
    final AggregatedRemoteStream<Integer> streamFoo =
        streamRegistry.get(typeFoo).stream().findFirst().orElseThrow();
    assertThat(streamFoo.logicalId()).isEqualTo(new LogicalId<>(typeFoo, 1));
    assertThat(streamFoo.streamConsumers())
        .containsExactly(new StreamConsumer<>(new StreamId(id1, gateway), streamFoo.logicalId()));

    final AggregatedRemoteStream<Integer> streamBar =
        streamRegistry.get(typeBar).stream().findFirst().orElseThrow();
    assertThat(streamBar.logicalId()).isEqualTo(new LogicalId<>(typeBar, 2));
    assertThat(streamBar.streamConsumers())
        .containsExactly(new StreamConsumer<>(new StreamId(id2, gateway), streamBar.logicalId()));
  }

  @Test
  void shouldTrackAddedStreams() {
    // given
    final UUID removedId = UUID.randomUUID();

    // when
    streamRegistry.add(typeFoo, removedId, gateway, 1);
    streamRegistry.add(typeBar, UUID.randomUUID(), gateway, 2);
    streamRegistry.add(typeBar, UUID.randomUUID(), gateway, 3);
    streamRegistry.remove(removedId, gateway);

    // then
    assertThat(metrics.getStreamCount()).isEqualTo(2);
  }

  @Test
  void shouldAggregateMultipleStreamsWithSameProperties() {
    // given
    final UUID id1 = UUID.randomUUID();
    final UUID id2 = UUID.randomUUID();
    final int properties = 1;

    // when
    final var added1 = streamRegistry.add(typeFoo, id1, gateway, properties);
    final var added2 = streamRegistry.add(typeFoo, id2, gateway, properties);

    // then
    assertThat(added1).isTrue();
    assertThat(added2).isTrue();
    final AggregatedRemoteStream<Integer> streamFoo =
        streamRegistry.get(typeFoo).stream().findFirst().orElseThrow();
    assertThat(streamFoo.logicalId()).isEqualTo(new LogicalId<>(typeFoo, properties));
    assertThat(streamFoo.streamConsumers())
        .containsExactly(
            new StreamConsumer<>(new StreamId(id1, gateway), streamFoo.logicalId()),
            new StreamConsumer<>(new StreamId(id2, gateway), streamFoo.logicalId()));
  }

  @Test
  void shouldRemoveClientStream() {
    // given
    final UUID id = UUID.randomUUID();
    streamRegistry.add(typeFoo, id, gateway, 1);
    streamRegistry.add(typeFoo, id, otherGateway, 1);

    // when
    streamRegistry.remove(id, gateway);

    // then
    final AggregatedRemoteStream<Integer> aggregatedRemoteStream =
        streamRegistry.get(typeFoo).stream().findFirst().orElseThrow();
    assertThat(aggregatedRemoteStream.streamConsumers())
        .containsExactly(
            new StreamConsumer<>(new StreamId(id, otherGateway), new LogicalId<>(typeFoo, 1)));
  }

  @Test
  void shouldRemoveAggregatedStream() {
    // given
    final UUID id = UUID.randomUUID();
    streamRegistry.add(typeFoo, id, gateway, 1);

    // when
    final var removed = streamRegistry.remove(id, gateway);

    // then
    assertThat(removed).isTrue();
    assertThat(streamRegistry.get(typeFoo)).isEmpty();
  }

  @Test
  void shouldIgnoreDuplicateAdd() {
    // given
    final UUID id = UUID.randomUUID();
    streamRegistry.add(typeFoo, id, gateway, 1);

    // when
    final var added = streamRegistry.add(typeFoo, id, gateway, 1);

    // then
    assertThat(added).isFalse();
    assertThat(metrics.getStreamCount()).isEqualTo(1);
  }

  @Test
  void shouldIgnoreDuplicateRemove() {
    // given
    final UUID id = UUID.randomUUID();
    streamRegistry.add(typeFoo, id, gateway, 1);
    streamRegistry.remove(id, gateway);

    // when
    final var removed = streamRegistry.remove(id, gateway);

    // then
    assertThat(removed).isFalse();
    assertThat(metrics.getStreamCount()).isZero();
  }

  @Test
  void shouldIgnoreRemoveOfUnknownStream() {
    // when
    final var removed = streamRegistry.remove(UUID.randomUUID(), gateway);

    // then
    assertThat(removed).isFalse();
    assertThat(metrics.getStreamCount()).isZero();
  }

  @Test
  void shouldAddStreamAfterRemoved() {
    // given
    final UUID id = UUID.randomUUID();
    streamRegistry.add(typeFoo, id, gateway, 1);
    streamRegistry.remove(id, gateway);

    // when
    streamRegistry.add(typeFoo, id, gateway, 1);

    // then
    final AggregatedRemoteStream<Integer> aggregatedRemoteStream =
        streamRegistry.get(typeFoo).stream().findFirst().orElseThrow();
    assertThat(aggregatedRemoteStream.streamConsumers())
        .containsExactly(
            new StreamConsumer<>(new StreamId(id, gateway), new LogicalId<>(typeFoo, 1)));
  }

  @Test
  void shouldRemoveAllStreamsFromAReceiver() {
    // given
    streamRegistry.add(typeFoo, UUID.randomUUID(), gateway, 1);
    streamRegistry.add(typeBar, UUID.randomUUID(), gateway, 2);
    final UUID idOther = UUID.randomUUID();
    streamRegistry.add(typeBar, idOther, otherGateway, 3);

    // when
    streamRegistry.removeAll(gateway);

    // then
    assertThat(streamRegistry.get(typeFoo)).isEmpty();
    final AggregatedRemoteStream<Integer> aggregatedRemoteStream =
        streamRegistry.get(typeBar).stream().findFirst().orElseThrow();
    assertThat(aggregatedRemoteStream.streamConsumers())
        .containsExactly(
            new StreamConsumer<>(new StreamId(idOther, otherGateway), new LogicalId<>(typeBar, 3)));
  }

  @Test
  void shouldClearAll() {
    // given
    streamRegistry.add(typeFoo, UUID.randomUUID(), gateway, 1);
    streamRegistry.add(typeBar, UUID.randomUUID(), gateway, 2);
    streamRegistry.add(typeBar, UUID.randomUUID(), otherGateway, 3);

    // when
    streamRegistry.clear();

    // then
    assertThat(streamRegistry.get(typeFoo)).isEmpty();
    assertThat(streamRegistry.get(typeBar)).isEmpty();
  }
}
