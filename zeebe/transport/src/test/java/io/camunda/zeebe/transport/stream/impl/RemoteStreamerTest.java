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
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.transport.stream.api.RemoteStreamMetrics;
import io.camunda.zeebe.transport.stream.impl.AggregatedRemoteStream.StreamId;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.StreamTopics;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Objects;
import java.util.UUID;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

final class RemoteStreamerTest {

  private final ClusterCommunicationService communicationService =
      Mockito.mock(ClusterCommunicationService.class);
  private final RemoteStreamRegistry<TestMetadata> registry =
      new RemoteStreamRegistry<>(RemoteStreamMetrics.noop());

  private final RemoteStreamerImpl<TestMetadata, TestPayload> streamer =
      new RemoteStreamerImpl<>(
          communicationService, registry, (e, d) -> {}, RemoteStreamMetrics.noop());

  @RegisterExtension
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  @BeforeEach
  void beforeEach() {
    scheduler.submitActor(streamer);
    scheduler.workUntilDone();
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(
        () -> {
          streamer.closeAsync();
          scheduler.workUntilDone();
        });
  }

  @Test
  void shouldReportCorrectMetadata() {
    // given
    final var type = new UnsafeBuffer(BufferUtil.wrapString("foo"));
    final var streamId = new StreamId(UUID.randomUUID(), MemberId.from("a"));
    final var metadata = new TestMetadata(1);
    registry.add(type, streamId.streamId(), streamId.receiver(), metadata);

    // when
    final var stream = streamer.streamFor(type).orElseThrow();

    // then
    assertThat(stream.metadata()).isSameAs(metadata);
  }

  @Test
  void shouldFilterStreamForWithPredicate() {
    // given
    final var type = new UnsafeBuffer(BufferUtil.wrapString("foo"));
    final var streamAId = new StreamId(UUID.randomUUID(), MemberId.from("a"));
    final var streamAMeta = new TestMetadata(1);
    final var streamBId = new StreamId(UUID.randomUUID(), MemberId.from("b"));
    final var streamBMeta = new TestMetadata(2);
    registry.add(type, streamBId.streamId(), streamBId.receiver(), streamBMeta);
    registry.add(type, streamAId.streamId(), streamAId.receiver(), streamAMeta);

    // when
    final var streamA = streamer.streamFor(type, m -> m.id() == 1).orElseThrow();
    final var streamB = streamer.streamFor(type, m -> m.id() == 2).orElseThrow();
    final var empty = streamer.streamFor(type, Objects::isNull);

    // then
    assertThat(streamA.metadata()).isSameAs(streamAMeta);
    assertThat(streamB.metadata()).isSameAs(streamBMeta);
    assertThat(empty).isEmpty();
  }

  @Test
  void shouldPush() {
    // given - a registry which returns a set of consumers sorted by their member IDs
    final var type = new UnsafeBuffer(BufferUtil.wrapString("foo"));
    final var streamId = new StreamId(UUID.randomUUID(), MemberId.from("a"));
    final var metadata = new TestMetadata(1);
    final var payload = new TestPayload(1);
    registry.add(type, streamId.streamId(), streamId.receiver(), metadata);

    // when
    final var stream = streamer.streamFor(type).orElseThrow();
    stream.push(payload);
    scheduler.workUntilDone();

    // then
    Mockito.verify(communicationService, Mockito.timeout(5_000).times(1))
        .send(
            Mockito.eq(StreamTopics.PUSH.topic()),
            Mockito.eq(new PushStreamRequest().streamId(streamId.streamId()).payload(payload)),
            Mockito.any(),
            Mockito.any(),
            Mockito.eq(streamId.receiver()),
            Mockito.any());
  }

  private record TestPayload(long key) implements BufferWriter {

    @Override
    public int getLength() {
      return 0;
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      return 0;
    }
  }

  private record TestMetadata(int id) implements BufferReader {

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {}
  }
}
