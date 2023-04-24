/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamRequest;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientStreamManagerTest {

  private static final ClientStreamConsumer NOOP_CONSUMER =
      p -> TestActorFuture.completedFuture(null);
  private final DirectBuffer streamType = BufferUtil.wrapString("foo");
  private final TestMetadata metadata = new TestMetadata(1);
  private final ClientStreamRegistry<TestMetadata> registry = new ClientStreamRegistry<>();
  private final ClusterCommunicationService mockTransport = mock(ClusterCommunicationService.class);
  private final TestClientStreamMetrics metrics = new TestClientStreamMetrics();
  private final ClientStreamManager<TestMetadata> clientStreamManager =
      new ClientStreamManager<>(
          registry,
          new ClientStreamRequestManager<>(mockTransport, new TestConcurrencyControl()),
          metrics);

  @BeforeEach
  void setup() {

    when(mockTransport.send(any(), any(), any(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));
  }

  @Test
  void shouldAddStream() {
    // when
    final var streamId = clientStreamManager.add(streamType, metadata, NOOP_CONSUMER);

    // then
    assertThat(registry.getClient(streamId)).isNotEmpty();
  }

  @Test
  void shouldAggregateStreamsWithSameStreamTypeAndMetadata() {
    // when
    final var uuid1 =
        clientStreamManager.add(
            BufferUtil.wrapString("foo"),
            new TestMetadata(1),
            p -> TestActorFuture.completedFuture(null));
    final var uuid2 =
        clientStreamManager.add(BufferUtil.wrapString("foo"), new TestMetadata(1), NOOP_CONSUMER);
    final var stream1 = registry.getClient(uuid1).orElseThrow();
    final var stream2 = registry.getClient(uuid2).orElseThrow();

    // then
    assertThat(stream1.serverStream().getStreamId())
        .isEqualTo(stream2.serverStream().getStreamId());
  }

  @Test
  void shouldNoAggregateStreamsWithDifferentMetadata() {
    // when
    final var uuid1 = clientStreamManager.add(streamType, new TestMetadata(1), NOOP_CONSUMER);
    final var uuid2 = clientStreamManager.add(streamType, new TestMetadata(2), NOOP_CONSUMER);
    final var stream1 = registry.getClient(uuid1).orElseThrow();
    final var stream2 = registry.getClient(uuid2).orElseThrow();

    // then
    assertThat(stream1.serverStream().getStreamId())
        .isNotEqualTo(stream2.serverStream().getStreamId());
  }

  @Test
  void shouldNoAggregateStreamsWithDifferentStreamType() {
    // when
    final var uuid1 =
        clientStreamManager.add(BufferUtil.wrapString("foo"), metadata, NOOP_CONSUMER);
    final var uuid2 =
        clientStreamManager.add(BufferUtil.wrapString("bar"), metadata, NOOP_CONSUMER);
    final var stream1 = registry.getClient(uuid1).orElseThrow();
    final var stream2 = registry.getClient(uuid2).orElseThrow();

    // then
    assertThat(stream1.serverStream().getStreamId())
        .isNotEqualTo(stream2.serverStream().getStreamId());
  }

  @Test
  void shouldOpenStreamToExistingServers() {
    // given
    final MemberId server1 = MemberId.from("1");
    clientStreamManager.onServerJoined(server1);
    final MemberId server2 = MemberId.from("2");
    clientStreamManager.onServerJoined(server2);

    // when
    final var uuid = clientStreamManager.add(streamType, metadata, NOOP_CONSUMER);

    // then
    final UUID serverStreamId = getServerStreamId(uuid);
    final var stream = registry.get(serverStreamId).orElseThrow();

    assertThat(stream.isConnected(server1)).isTrue();
    assertThat(stream.isConnected(server2)).isTrue();
  }

  @Test
  void shouldOpenStreamToNewlyAddedServer() {
    // given
    final var uuid = clientStreamManager.add(streamType, metadata, NOOP_CONSUMER);
    final var serverStream = registry.get(getServerStreamId(uuid)).orElseThrow();

    // when
    final MemberId server = MemberId.from("3");
    clientStreamManager.onServerJoined(server);

    // then
    assertThat(serverStream.isConnected(server)).isTrue();
  }

  @Test
  void shouldOpenStreamToNewlyAddedServerForAllOpenStreams() {
    // given
    final var stream1 =
        clientStreamManager.add(BufferUtil.wrapString("foo"), metadata, NOOP_CONSUMER);
    final var stream2 =
        clientStreamManager.add(BufferUtil.wrapString("bar"), metadata, NOOP_CONSUMER);
    final var serverStream1 = registry.get(getServerStreamId(stream1)).orElseThrow();
    final var serverStream2 = registry.get(getServerStreamId(stream2)).orElseThrow();
    // when
    final MemberId server = MemberId.from("3");
    clientStreamManager.onServerJoined(server);

    // then
    assertThat(serverStream1.isConnected(server)).isTrue();
    assertThat(serverStream2.isConnected(server)).isTrue();
  }

  @Test
  void shouldRemoveStream() {
    // given
    final var uuid = clientStreamManager.add(streamType, metadata, NOOP_CONSUMER);
    final var serverStreamId = getServerStreamId(uuid);

    // when
    clientStreamManager.remove(uuid);

    // then
    assertThat(registry.getClient(uuid)).isEmpty();
    assertThat(registry.get(serverStreamId)).isEmpty();
  }

  @Test
  void shouldNotRemoveIfOtherClientStreamExist() {
    // given
    final var uuid1 = clientStreamManager.add(streamType, metadata, NOOP_CONSUMER);
    final var uuid2 = clientStreamManager.add(streamType, metadata, NOOP_CONSUMER);
    final var serverStreamId = getServerStreamId(uuid1);

    // when
    clientStreamManager.remove(uuid1);

    // then
    assertThat(registry.getClient(uuid1)).isEmpty();
    assertThat(registry.getClient(uuid2)).isPresent();
    assertThat(registry.get(serverStreamId)).isPresent();
  }

  @Test
  void shouldPushPayloadToClient() {
    // given
    final DirectBuffer payloadReceived = new UnsafeBuffer();
    final var clientStreamId =
        clientStreamManager.add(
            streamType,
            metadata,
            p -> {
              payloadReceived.wrap(p);
              return TestActorFuture.completedFuture(null);
            });
    final var streamId = getServerStreamId(clientStreamId);

    // when
    final var payloadPushed = BufferUtil.wrapString("data");
    final var request = new PushStreamRequest().streamId(streamId).payload(payloadPushed);
    final var future = new TestActorFuture<Void>();
    clientStreamManager.onPayloadReceived(request, future);

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(100));
    assertThat(payloadReceived).isEqualTo(payloadPushed);
    assertThat(metrics.getPushSucceeded()).isOne();
  }

  @Test
  void shouldNotPushIfNoStream() {
    // given -- no stream registered

    // when
    final var payloadPushed = BufferUtil.wrapString("data");
    final var request = new PushStreamRequest().streamId(UUID.randomUUID()).payload(payloadPushed);
    final var future = new TestActorFuture<Void>();
    clientStreamManager.onPayloadReceived(request, future);

    // then
    assertThat(future)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(NoSuchStreamException.class);
    assertThat(metrics.getPushFailed()).isOne();
  }

  @Test
  void shouldForwardErrorWhenPushFails() {
    // given
    final var clientStreamId =
        clientStreamManager.add(
            streamType,
            metadata,
            p -> {
              throw new RuntimeException("Expected");
            });
    final var streamId = getServerStreamId(clientStreamId);

    // when
    final var payloadPushed = BufferUtil.wrapString("data");
    final var request = new PushStreamRequest().streamId(streamId).payload(payloadPushed);
    final var future = new TestActorFuture<Void>();
    clientStreamManager.onPayloadReceived(request, future);

    // then
    assertThat(future)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldRemoveServerFromClientStream() {
    // given
    final MemberId server = MemberId.from("1");
    clientStreamManager.onServerJoined(server);
    final var uuid =
        clientStreamManager.add(streamType, metadata, p -> TestActorFuture.completedFuture(null));
    final var stream = registry.get(getServerStreamId(uuid)).orElseThrow();
    assertThat(stream.isConnected(server)).isTrue();

    // when
    clientStreamManager.onServerRemoved(server);

    // then
    assertThat(stream.isConnected(server)).isFalse();
  }

  @Test
  void shouldReportServerCountOnJoined() {
    // given
    final MemberId server = MemberId.from("1");

    // when
    clientStreamManager.onServerJoined(server);

    // then
    assertThat(metrics.getServerCount()).isOne();
  }

  @Test
  void shouldReportServerCountOnRemoved() {
    // given
    final MemberId server = MemberId.from("1");
    clientStreamManager.onServerJoined(server);

    // when
    clientStreamManager.onServerRemoved(server);

    // then
    assertThat(metrics.getServerCount()).isZero();
  }

  private UUID getServerStreamId(final ClientStreamId clientStreamId) {
    return registry.getClient(clientStreamId).orElseThrow().serverStream().getStreamId();
  }

  private record TestMetadata(int data) implements BufferWriter {
    @Override
    public int getLength() {
      return Integer.BYTES;
    }

    @Override
    public void write(final MutableDirectBuffer buffer, final int offset) {
      buffer.putInt(offset, data);
    }
  }
}
