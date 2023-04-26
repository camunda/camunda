/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.transport.stream.api.RemoteStreamMetrics;
import io.camunda.zeebe.transport.stream.impl.ImmutableStreamRegistry.StreamConsumer;
import io.camunda.zeebe.transport.stream.impl.ImmutableStreamRegistry.StreamId;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.StreamTopics;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
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
      new RemoteStreamerImpl<>(communicationService, registry, RemoteStreamMetrics.noop());

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
    final var metadata = new TestMetadata();
    registry.add(type, streamId.streamId(), streamId.receiver(), metadata);

    // when
    final var stream = streamer.streamFor(type).orElseThrow();

    // then
    assertThat(stream.metadata()).isSameAs(metadata);
  }

  @Test
  void shouldPush() {
    // given - a registry which returns a set of consumers sorted by their member IDs
    final var type = new UnsafeBuffer(BufferUtil.wrapString("foo"));
    final var streamId = new StreamId(UUID.randomUUID(), MemberId.from("a"));
    final var metadata = new TestMetadata();
    final var payload = new TestPayload(1);
    registry.add(type, streamId.streamId(), streamId.receiver(), metadata);

    // when
    final var stream = streamer.streamFor(type).orElseThrow();
    stream.push(payload, (job, error) -> {});
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
    public void write(final MutableDirectBuffer buffer, final int offset) {}
  }

  private record TestMetadata() implements BufferReader {

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {}
  }
}
