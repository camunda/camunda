/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.concurrent.CompletableFuture;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class ClientStreamRegistryTest {
  private static final ClientStreamConsumer CLIENT_STREAM_CONSUMER =
      p -> CompletableFuture.completedFuture(null);

  private final TestClientStreamMetrics metrics = new TestClientStreamMetrics();
  private final ClientStreamRegistry<TestSerializableData> registry =
      new ClientStreamRegistry<>(metrics);

  @Test
  void shouldReportStreamCountOnAdd() {
    // given
    final var metadata = new TestSerializableData();

    // when - add 2 aggregated streams, bar (2 clients) and foo (1 client)
    registry.addClient(BufferUtil.wrapString("bar"), metadata, CLIENT_STREAM_CONSUMER);
    registry.addClient(BufferUtil.wrapString("foo"), metadata, CLIENT_STREAM_CONSUMER);
    registry.addClient(BufferUtil.wrapString("bar"), metadata, CLIENT_STREAM_CONSUMER);

    // then
    assertThat(metrics.getAggregatedStreamCount()).isEqualTo(2);
    assertThat(metrics.getClientCount()).isEqualTo(3);
  }

  @Test
  void shouldReportStreamCountOnRemove() {
    // given - 2 aggregated streams, bar and food
    final var metadata = new TestSerializableData();
    final var fooId =
        registry.addClient(BufferUtil.wrapString("foo"), metadata, CLIENT_STREAM_CONSUMER);
    final var barId =
        registry.addClient(BufferUtil.wrapString("bar"), metadata, CLIENT_STREAM_CONSUMER);
    registry.addClient(BufferUtil.wrapString("bar"), metadata, CLIENT_STREAM_CONSUMER);

    // when - remove only one aggregated stream (bar still has one client)
    registry.removeClient(fooId.streamId());
    registry.removeClient(barId.streamId());

    // then
    Assertions.assertThat(metrics.getAggregatedStreamCount()).isOne();
    assertThat(metrics.getClientCount()).isOne();
  }

  @Test
  void shouldReportMetricsOnClear() {
    // given
    final var metadata = new TestSerializableData();
    registry.addClient(BufferUtil.wrapString("bar"), metadata, CLIENT_STREAM_CONSUMER);
    registry.addClient(BufferUtil.wrapString("foo"), metadata, CLIENT_STREAM_CONSUMER);
    registry.addClient(BufferUtil.wrapString("bar"), metadata, CLIENT_STREAM_CONSUMER);

    // when
    registry.clear();

    // then
    assertThat(metrics.getAggregatedStreamCount()).isZero();
    assertThat(metrics.getClientCount()).isZero();
  }
}
