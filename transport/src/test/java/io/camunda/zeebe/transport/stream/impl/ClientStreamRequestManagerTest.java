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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.transport.stream.impl.messages.StreamTopics;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientStreamRequestManagerTest {

  private final ClusterCommunicationService mockTransport = mock(ClusterCommunicationService.class);
  private final ClientStreamRequestManager<BufferWriter> requestManager =
      new ClientStreamRequestManager<>(mockTransport, new TestConcurrencyControl());

  private final AggregatedClientStream<BufferWriter> clientStream =
      new AggregatedClientStream<>(
          UUID.randomUUID(),
          new LogicalId<>(new UnsafeBuffer(BufferUtil.wrapString("foo")), new TestMetadata()));

  @BeforeEach
  void setup() {
    when(mockTransport.send(any(), any(), any(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));
  }

  @Test
  void shouldSendAddRequestToAllServers() {
    // given
    final MemberId server1 = MemberId.from("1");
    final MemberId server2 = MemberId.from("2");
    final var servers = Set.of(server1, server2);
    // when
    requestManager.openStream(clientStream, servers);

    // then
    verify(mockTransport)
        .send(eq(StreamTopics.ADD.topic()), any(), any(), any(), eq(server1), any());
    verify(mockTransport)
        .send(eq(StreamTopics.ADD.topic()), any(), any(), any(), eq(server2), any());

    assertThat(clientStream.isConnected(server1)).isTrue();
    assertThat(clientStream.isConnected(server2)).isTrue();
  }

  @Test
  void shouldRetryWhenAddRequestFails() {
    // given
    final MemberId server = MemberId.from("1");
    final var servers = Set.of(server);
    when(mockTransport.send(any(), any(), any(), any(), any(), any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Expected")))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    requestManager.openStream(clientStream, servers);

    // then
    verify(mockTransport, times(2))
        .send(eq(StreamTopics.ADD.topic()), any(), any(), any(), eq(server), any());
    assertThat(clientStream.isConnected(server)).isTrue();
  }

  @Test
  void shouldSendAddRequestAgainAfterAServerIsReAdded() {
    // given
    final MemberId server = MemberId.from("1");
    requestManager.openStream(clientStream, Set.of(server));

    // when
    clientStream.remove(server);
    requestManager.openStream(clientStream, Set.of(server));

    // then
    verify(mockTransport, times(2))
        .send(eq(StreamTopics.ADD.topic()), any(), any(), any(), eq(server), any());

    assertThat(clientStream.isConnected(server)).isTrue();
  }

  @Test
  void shouldNotSendAddRequestIfStreamIsClosed() {
    // given
    clientStream.close();

    // when
    final MemberId server = MemberId.from("1");
    requestManager.openStream(clientStream, Set.of(server));

    // then
    verify(mockTransport, never())
        .send(eq(StreamTopics.ADD.topic()), any(), any(), any(), eq(server), any());
  }

  @Test
  void shouldSendRemoveRequestToAllServers() {
    // given
    final MemberId server1 = MemberId.from("1");
    final MemberId server2 = MemberId.from("2");
    clientStream.add(server1);
    clientStream.add(server2);
    final var servers = Set.of(server1, server2);

    // when
    requestManager.removeStream(clientStream, servers);

    // then
    verify(mockTransport)
        .send(eq(StreamTopics.REMOVE.topic()), any(), any(), any(), eq(server1), any());
    verify(mockTransport)
        .send(eq(StreamTopics.REMOVE.topic()), any(), any(), any(), eq(server2), any());

    assertThat(clientStream.isConnected(server1)).isFalse();
    assertThat(clientStream.isConnected(server2)).isFalse();
  }

  @Test
  void shouldRetryWhenRemoveRequestFails() {
    // given
    final MemberId server = MemberId.from("1");
    clientStream.add(server);

    when(mockTransport.send(any(), any(), any(), any(), any(), any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Expected")))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    requestManager.removeStream(clientStream, Set.of(server));

    // then
    verify(mockTransport, times(2))
        .send(eq(StreamTopics.REMOVE.topic()), any(), any(), any(), eq(server), any());
    assertThat(clientStream.isConnected(server)).isFalse();
  }

  @Test
  void shouldSendRemoveAllRequestToAllServers() {
    // given
    final MemberId server1 = MemberId.from("1");
    final MemberId server2 = MemberId.from("2");
    final var servers = Set.of(server1, server2);

    // when
    requestManager.removeAll(servers);

    // then
    verify(mockTransport)
        .unicast(eq(StreamTopics.REMOVE_ALL.topic()), any(), any(), eq(server1), anyBoolean());
    verify(mockTransport)
        .unicast(eq(StreamTopics.REMOVE_ALL.topic()), any(), any(), eq(server2), anyBoolean());
  }

  private static class TestMetadata implements BufferWriter {
    @Override
    public int getLength() {
      return Integer.BYTES;
    }

    @Override
    public void write(final MutableDirectBuffer buffer, final int offset) {
      buffer.putInt(offset, 0);
    }
  }
}
