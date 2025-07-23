/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.messaging.MessagingException;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.util.CloseableSilently;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RemoteClientConnectionTest {

  private SimpleMessagingMetrics simpleMetrics;
  private Channel channel;
  private RemoteClientConnection remoteClientConnection;
  private InetSocketAddress toAddress;

  @BeforeEach
  public void setup() {
    channel = mock(Channel.class);
    toAddress = new InetSocketAddress(0);
    when(channel.remoteAddress()).thenReturn(toAddress);
    final ChannelFuture channelFuture = mock(ChannelFuture.class);
    when(channel.writeAndFlush(any())).thenReturn(channelFuture);
    simpleMetrics = new SimpleMessagingMetrics();
    remoteClientConnection = new RemoteClientConnection(simpleMetrics, channel);
  }

  @Test
  public void shouldCountForMessage() {
    // given

    // when
    remoteClientConnection.sendAsync(
        new ProtocolRequest(1, new Address("", 12345), "subj", "payload".getBytes()));

    // then
    final String expectedKey = simpleMetrics.computeKey(toAddress.toString(), "subj");
    assertThat(simpleMetrics.messageCount.get(expectedKey)).isNotNull().isEqualTo(1);
    assertThat(simpleMetrics.reqSize.get(expectedKey)).isEqualTo("payload".getBytes().length);

    // only counted for req-resp
    assertThat(simpleMetrics.inFlightRequestCount.size()).isEqualTo(0);
    assertThat(simpleMetrics.reqRespCount.size()).isEqualTo(0);
  }

  @Test
  public void shouldCountForRequestResponse() {
    // given

    // when
    remoteClientConnection.sendAndReceive(
        new ProtocolRequest(1, new Address("", 12345), "subj", "payload".getBytes()));

    // then
    final String expectedKey = simpleMetrics.computeKey(toAddress.toString(), "subj");
    assertThat(simpleMetrics.inFlightRequestCount.get(expectedKey)).isEqualTo(1);
    assertThat(simpleMetrics.reqRespCount.get(expectedKey)).isEqualTo(1);
    assertThat(simpleMetrics.reqSize.get(expectedKey)).isEqualTo("payload".getBytes().length);

    // only counted for message
    assertThat(simpleMetrics.messageCount.size()).isZero();
  }

  @Test
  public void shouldCountForResponse() {
    // given
    final var responseFuture =
        remoteClientConnection.sendAndReceive(
            new ProtocolRequest(1, new Address("", 12345), "subj", "payload".getBytes()));

    // when
    responseFuture.complete("complete".getBytes());

    // then
    final String expectedKey = simpleMetrics.computeKey(toAddress.toString(), "subj");
    assertThat(simpleMetrics.inFlightRequestCount.get(expectedKey)).isEqualTo(0);
    assertThat(simpleMetrics.reqRespCount.get(expectedKey)).isEqualTo(1);
    assertThat(simpleMetrics.reqSize.get(expectedKey)).isEqualTo("payload".getBytes().length);
    assertThat(simpleMetrics.requestResponseLatency).isGreaterThan(0);
    assertThat(simpleMetrics.requestOutcome.get(expectedKey)).isTrue();

    // only counted for message
    assertThat(simpleMetrics.messageCount.size()).isZero();
  }

  @Test
  public void shouldCountForFailedResponse() {
    // given
    final var responseFuture =
        remoteClientConnection.sendAndReceive(
            new ProtocolRequest(1, new Address("", 12345), "subj", "payload".getBytes()));

    // when
    responseFuture.completeExceptionally(new RuntimeException());

    // then
    final String expectedKey = simpleMetrics.computeKey(toAddress.toString(), "subj");
    assertThat(simpleMetrics.inFlightRequestCount.get(expectedKey)).isEqualTo(0);
    assertThat(simpleMetrics.reqRespCount.get(expectedKey)).isEqualTo(1);
    assertThat(simpleMetrics.reqSize.get(expectedKey)).isEqualTo("payload".getBytes().length);
    assertThat(simpleMetrics.requestResponseLatency).isGreaterThan(0);
    assertThat(simpleMetrics.requestOutcome.get(expectedKey)).isFalse();

    // only counted for message
    assertThat(simpleMetrics.messageCount.size()).isZero();
  }

  @Test
  public void shouldReceiveConnectionClosedExceptionForResponseOnClientClose() {
    // given
    final var responseFuture =
        remoteClientConnection.sendAndReceive(
            new ProtocolRequest(1, new Address("", 12345), "subj", "payload".getBytes()));

    // when
    remoteClientConnection.close();

    // then
    assertThat(responseFuture)
        .failsWithin(100, TimeUnit.MILLISECONDS)
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(MessagingException.ConnectionClosed.class)
        .withMessageContaining("Connection")
        .withMessageContaining("was closed");
  }

  private static final class SimpleMessagingMetrics implements MessagingMetrics {

    private static final String LABEL_FORMAT = "%s-%s";

    long requestResponseLatency;
    final Map<String, Integer> messageCount = new HashMap<>();
    final Map<String, Integer> inFlightRequestCount = new HashMap<>();
    final Map<String, Integer> reqRespCount = new HashMap<>();
    final Map<String, Integer> reqSize = new HashMap<>();
    final Map<String, Boolean> requestOutcome = new HashMap<>();

    @Override
    public CloseableSilently startRequestTimer(final String name) {
      final long start = System.nanoTime();
      return () -> requestResponseLatency = System.nanoTime() - start;
    }

    @Override
    public void observeRequestSize(
        final String to, final String name, final int requestSizeInBytes) {
      reqSize.put(computeKey(to, name), requestSizeInBytes);
    }

    @Override
    public void countMessage(final String to, final String name, final String channelId) {
      final String key = computeKey(to, name);
      final Integer integer = messageCount.computeIfAbsent(key, s -> 0);
      messageCount.put(key, integer + 1);
    }

    @Override
    public void countRequestResponse(final String to, final String name, final String channelId) {
      final String key = computeKey(to, name);
      final Integer integer = reqRespCount.computeIfAbsent(key, k -> 0);
      reqRespCount.put(key, integer + 1);
    }

    @Override
    public void countSuccessResponse(final String address, final String name) {
      final String key = computeKey(address, name);
      requestOutcome.put(key, true);
    }

    @Override
    public void countFailureResponse(final String address, final String name, final String error) {
      final String key = computeKey(address, name);
      requestOutcome.put(key, false);
    }

    @Override
    public void incInFlightRequests(final String address, final String topic) {
      final String key = computeKey(address, topic);
      final Integer integer = inFlightRequestCount.computeIfAbsent(key, k -> 0);
      inFlightRequestCount.put(key, integer + 1);
    }

    @Override
    public void decInFlightRequests(final String address, final String topic) {
      final String key = computeKey(address, topic);
      final Integer integer = inFlightRequestCount.computeIfAbsent(key, k -> 0);
      inFlightRequestCount.put(key, integer - 1);
    }

    String computeKey(final String to, final String name) {
      return String.format(LABEL_FORMAT, to, name);
    }
  }
}
