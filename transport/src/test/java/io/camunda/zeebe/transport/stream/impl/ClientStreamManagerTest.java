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
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamRequest;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientStreamManagerTest {

  private final DirectBuffer streamType = BufferUtil.wrapString("foo");
  private final BufferWriter metadata = mock(BufferWriter.class);
  private final ClientStreamRegistry<BufferWriter> registry = new ClientStreamRegistry<>();
  private final ClusterCommunicationService mockTransport = mock(ClusterCommunicationService.class);
  private final ClientStreamManager<BufferWriter> clientStreamManager =
      new ClientStreamManager<>(
          registry, new ClientStreamRequestManager<>(mockTransport, new TestConcurrencyControl()));

  @BeforeEach
  void setup() {

    when(mockTransport.send(any(), any(), any(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));
  }

  @Test
  void shouldAddStream() {
    // when
    final var uuid = clientStreamManager.add(streamType, metadata, p -> {});

    // then
    assertThat(registry.get(uuid)).isNotNull();
  }

  @Test
  void shouldOpenStreamToExistingServers() {
    // given
    final MemberId server1 = MemberId.from("1");
    clientStreamManager.onServerJoined(server1);
    final MemberId server2 = MemberId.from("2");
    clientStreamManager.onServerJoined(server2);

    // when
    final var uuid = clientStreamManager.add(streamType, metadata, p -> {});

    // then
    final var clientStream = registry.get(uuid).orElseThrow();

    assertThat(clientStream.isConnected(server1)).isTrue();
    assertThat(clientStream.isConnected(server2)).isTrue();
  }

  @Test
  void shouldOpenStreamToNewlyAddedServer() {
    // given
    final var uuid = clientStreamManager.add(streamType, metadata, p -> {});
    final var clientStream = registry.get(uuid).orElseThrow();

    // when
    final MemberId server = MemberId.from("3");
    clientStreamManager.onServerJoined(server);

    // then
    assertThat(clientStream.isConnected(server)).isTrue();
  }

  @Test
  void shouldOpenStreamToNewlyAddedServerForAllOpenStreams() {
    // given
    final var stream1 = clientStreamManager.add(streamType, metadata, p -> {});
    final var stream2 = clientStreamManager.add(streamType, metadata, p -> {});

    // when
    final MemberId server = MemberId.from("3");
    clientStreamManager.onServerJoined(server);

    // then
    assertThat(registry.get(stream1).orElseThrow().isConnected(server)).isTrue();
    assertThat(registry.get(stream2).orElseThrow().isConnected(server)).isTrue();
  }

  @Test
  void shouldRemoveStream() {
    // given
    final var uuid = clientStreamManager.add(streamType, metadata, p -> {});

    // when
    clientStreamManager.remove(uuid);

    // then
    assertThat(registry.get(uuid)).isEmpty();
  }

  @Test
  void shouldPushPayloadToClient() {
    // given
    final DirectBuffer payloadReceived = new UnsafeBuffer();
    final var streamId = clientStreamManager.add(streamType, metadata, payloadReceived::wrap);

    // when
    final var payloadPushed = BufferUtil.wrapString("data");
    final var request = new PushStreamRequest().streamId(streamId).payload(payloadPushed);
    final CompletableFuture<Void> future = new CompletableFuture<>();
    clientStreamManager.onPayloadReceived(request, future);

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(100));
    assertThat(payloadReceived).isEqualTo(payloadPushed);
  }

  @Test
  void shouldNotPushIfNoStream() {
    // given -- no stream registered

    // when
    final var payloadPushed = BufferUtil.wrapString("data");
    final var request = new PushStreamRequest().streamId(UUID.randomUUID()).payload(payloadPushed);
    final CompletableFuture<Void> future = new CompletableFuture<>();
    clientStreamManager.onPayloadReceived(request, future);

    // then
    assertThat(future)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(NoSuchStreamException.class);
  }

  @Test
  void shouldRemoveServerFromClientStream() {
    // given
    final MemberId server = MemberId.from("1");
    clientStreamManager.onServerJoined(server);
    final var uuid = clientStreamManager.add(streamType, metadata, p -> {});
    final var clientStream = registry.get(uuid).orElseThrow();
    assertThat(clientStream.isConnected(server)).isTrue();

    // when
    clientStreamManager.onServerRemoved(server);

    // then
    assertThat(clientStream.isConnected(server)).isFalse();
  }
}
