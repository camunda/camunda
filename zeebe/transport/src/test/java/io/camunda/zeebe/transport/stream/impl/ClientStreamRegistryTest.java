/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class ClientStreamRegistryTest {
  private static final String OTHER_PHYSICAL_TENANT_ID = "other-tenant";
  private static final ClientStreamConsumer CLIENT_STREAM_CONSUMER =
      p -> CompletableActorFuture.completed(null);

  private final TestClientStreamMetrics metrics = new TestClientStreamMetrics();
  private final ClientStreamRegistry<TestSerializableData> registry =
      new ClientStreamRegistry<>(metrics);

  @Test
  void shouldNotAggregateSameStreamTypeAndMetadataAcrossDifferentPhysicalTenants() {
    // given
    final var metadata = new TestSerializableData();
    final var streamType = BufferUtil.wrapString("foo");

    // when - two different tenants subscribe with the exact same streamType and metadata
    final var defaultTenantClient =
        registry.addClient(
            streamType, metadata, CLIENT_STREAM_CONSUMER, DEFAULT_PHYSICAL_TENANT_ID);
    final var otherTenantClient =
        registry.addClient(streamType, metadata, CLIENT_STREAM_CONSUMER, OTHER_PHYSICAL_TENANT_ID);

    // then - each tenant gets its own aggregated stream, not a shared one
    final var defaultServerStream = defaultTenantClient.serverStream();
    final var otherServerStream = otherTenantClient.serverStream();
    assertThat(defaultServerStream.streamId()).isNotEqualTo(otherServerStream.streamId());
    assertThat(defaultServerStream.physicalTenantId()).isEqualTo(DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(otherServerStream.physicalTenantId()).isEqualTo(OTHER_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldAssignFreshServerStreamIdAfterRemovingLastClientForATenant() {
    // given
    final var metadata = new TestSerializableData();
    final var streamType = BufferUtil.wrapString("foo");
    final var firstClient =
        registry.addClient(
            streamType, metadata, CLIENT_STREAM_CONSUMER, DEFAULT_PHYSICAL_TENANT_ID);
    final var firstStreamId = firstClient.serverStream().streamId();

    // when - the only client is removed, closing the aggregated stream entirely
    registry.removeClient(firstClient.streamId());

    // then - a new subscription for the same tenant and logical id gets a fresh stream id,
    // proving the stale serverStreamIds entry was actually cleaned up under its tenant-scoped key
    final var secondClient =
        registry.addClient(
            streamType, metadata, CLIENT_STREAM_CONSUMER, DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(secondClient.serverStream().streamId()).isNotEqualTo(firstStreamId);
  }

  @Test
  void shouldReportStreamCountOnAdd() {
    // given
    final var metadata = new TestSerializableData();

    // when - add 2 aggregated streams, bar (2 clients) and foo (1 client)
    registry.addClient(
        BufferUtil.wrapString("bar"), metadata, CLIENT_STREAM_CONSUMER, DEFAULT_PHYSICAL_TENANT_ID);
    registry.addClient(
        BufferUtil.wrapString("foo"), metadata, CLIENT_STREAM_CONSUMER, DEFAULT_PHYSICAL_TENANT_ID);
    registry.addClient(
        BufferUtil.wrapString("bar"), metadata, CLIENT_STREAM_CONSUMER, DEFAULT_PHYSICAL_TENANT_ID);

    // then
    assertThat(metrics.getAggregatedStreamCount()).isEqualTo(2);
    assertThat(metrics.getClientCount()).isEqualTo(3);
  }

  @Test
  void shouldReportStreamCountOnRemove() {
    // given - 2 aggregated streams, bar and food
    final var metadata = new TestSerializableData();
    final var fooId =
        registry.addClient(
            BufferUtil.wrapString("foo"),
            metadata,
            CLIENT_STREAM_CONSUMER,
            DEFAULT_PHYSICAL_TENANT_ID);
    final var barId =
        registry.addClient(
            BufferUtil.wrapString("bar"),
            metadata,
            CLIENT_STREAM_CONSUMER,
            DEFAULT_PHYSICAL_TENANT_ID);
    registry.addClient(
        BufferUtil.wrapString("bar"), metadata, CLIENT_STREAM_CONSUMER, DEFAULT_PHYSICAL_TENANT_ID);

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
    registry.addClient(
        BufferUtil.wrapString("bar"), metadata, CLIENT_STREAM_CONSUMER, DEFAULT_PHYSICAL_TENANT_ID);
    registry.addClient(
        BufferUtil.wrapString("foo"), metadata, CLIENT_STREAM_CONSUMER, DEFAULT_PHYSICAL_TENANT_ID);
    registry.addClient(
        BufferUtil.wrapString("bar"), metadata, CLIENT_STREAM_CONSUMER, DEFAULT_PHYSICAL_TENANT_ID);

    // when
    registry.clear();

    // then
    assertThat(metrics.getAggregatedStreamCount()).isZero();
    assertThat(metrics.getClientCount()).isZero();
  }
}
