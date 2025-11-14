/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.assertj.core.condition.VerboseCondition;
import org.junit.jupiter.api.Test;

@SuppressWarnings("resource")
final class TestClusterBuilderTest {
  @Test
  void shouldCreateBrokers() {
    // given
    final var builder = new TestClusterBuilder();

    // when
    builder.withBrokersCount(2);
    final var cluster = builder.build();

    // then
    assertThat(cluster.brokers())
        .allSatisfy(haveProperty("cluster size", b -> b.unifiedConfig().getCluster().getSize(), 2));
  }

  @Test
  void shouldSetPartitionsCount() {
    // given
    final var builder = new TestClusterBuilder();

    // when
    builder.withPartitionsCount(2);
    final var cluster = builder.build();

    // then
    final var brokers = cluster.brokers();
    assertThat(brokers)
        .allSatisfy(
            haveProperty(
                "partition count", b -> b.unifiedConfig().getCluster().getPartitionCount(), 2));
  }

  @Test
  void shouldSetReplicationFactor() {
    // given
    final var builder = new TestClusterBuilder();

    // when
    builder.withBrokersCount(2).withReplicationFactor(2);
    final var cluster = builder.build();

    // then
    final var brokers = cluster.brokers();
    assertThat(brokers)
        .allSatisfy(
            haveProperty(
                "replication factor",
                b -> b.unifiedConfig().getCluster().getReplicationFactor(),
                2));
  }

  @Test
  void shouldSetClusterName() {
    // given
    final var builder = new TestClusterBuilder();

    // when
    builder
        .withName("test-cluster")
        .withEmbeddedGateway(false)
        .withBrokersCount(2)
        .withGatewaysCount(2);
    final var cluster = builder.build();

    // then
    assertThat(cluster.brokers())
        .allSatisfy(
            haveProperty(
                "cluster name", b -> b.unifiedConfig().getCluster().getName(), "test-cluster"));
    assertThat(cluster.gateways())
        .allSatisfy(
            haveProperty(
                "cluster name", g -> g.unifiedConfig().getCluster().getName(), "test-cluster"));
  }

  @Test
  void shouldAssignUniqueMemberIdToEachGateway() {
    // given
    final var builder = new TestClusterBuilder();

    // when
    builder.withEmbeddedGateway(false).withGatewaysCount(2);
    final var cluster = builder.build();

    // then
    final var memberIds = cluster.gateways().keySet();
    assertThat(memberIds).hasSize(2);
    for (final var memberId : memberIds) {
      final var gateway = cluster.gateways().get(memberId);
      assertThat(gateway.nodeId()).as("every gateway has a unique member").isEqualTo(memberId);
    }
  }

  @Test
  void shouldAssignUniqueNodeIdToEachBroker() {
    // given
    final var builder = new TestClusterBuilder();

    // when
    builder.withEmbeddedGateway(false).withBrokersCount(2);
    final var cluster = builder.build();

    // then
    final var nodeIds = cluster.brokers().keySet();
    assertThat(nodeIds).hasSize(2);
    for (final var nodeId : nodeIds) {
      final var broker = cluster.brokers().get(nodeId);
      assertThat(broker.nodeId())
          .as("every broker has a unique node ID configured")
          .isEqualTo(nodeId);
    }
  }

  @Test
  void shouldAssignAllBrokersAsInitialContactPoints() {
    // given
    final var builder = new TestClusterBuilder();

    // when
    builder.withEmbeddedGateway(false).withBrokersCount(2);
    final var cluster = builder.build();

    // then
    final var expectedValue =
        cluster.brokers().values().stream().map(b -> b.address(TestZeebePort.CLUSTER)).toList();
    assertThat(cluster.brokers())
        .allSatisfy(
            haveProperty(
                "initial contact points",
                b -> b.unifiedConfig().getCluster().getInitialContactPoints(),
                expectedValue));
    assertThat(cluster.gateways())
        .allSatisfy(
            haveProperty(
                "initial contact points",
                g -> g.unifiedConfig().getCluster().getInitialContactPoints(),
                expectedValue));
  }

  @Test
  void shouldNotAssignContactPointToStandaloneGatewayIfNoBrokersAvailable() {
    // given
    final var builder = new TestClusterBuilder();

    // when
    builder.withGatewaysCount(1).withBrokersCount(0);
    final var cluster = builder.build();

    // then
    final var gateway = cluster.gateways().values().iterator().next();
    assertThat(gateway)
        .has(
            hasProperty(
                "initial contact point",
                g -> g.unifiedConfig().getCluster().getInitialContactPoints(),
                Collections.emptyList()));
  }

  @Test
  void shouldConfigureEmbeddedGateway() {
    // given
    final var builder = new TestClusterBuilder();

    // when
    builder.withEmbeddedGateway(true).withBrokersCount(1);
    final var cluster = builder.build();

    // then
    final var brokerId = MemberId.from("0");
    final var broker = cluster.brokers().get(brokerId);
    assertThat(broker)
        .has(hasProperty("embedded gateway enabled", TestStandaloneBroker::isGateway, true));
    assertThat(broker.isGateway()).isTrue();
  }

  @Test
  void shouldNotConfigureEmbeddedGateway() {
    // given
    final var builder = new TestClusterBuilder();

    // when
    builder.withEmbeddedGateway(false).withBrokersCount(1);
    final var cluster = builder.build();

    // then
    final var brokerId = MemberId.from("0");
    final var broker = cluster.brokers().get(brokerId);
    assertThat(broker)
        .has(hasProperty("embedded gateway not enabled", TestStandaloneBroker::isGateway, false));
    assertThat(broker.isGateway()).isFalse();
  }

  private <T extends TestApplication<T>, U> Condition<T> hasProperty(
      final String name, final Function<T, U> extractor, final U expected) {
    return VerboseCondition.verboseCondition(
        app -> extractor.apply(app).equals(expected),
        "has property '%s' == '%s'".formatted(name, expected),
        app -> " but actual property is '%s'".formatted(extractor.apply(app)));
  }

  private <T extends TestApplication<T>, U> BiConsumer<MemberId, T> haveProperty(
      final String name, final Function<T, U> extractor, final U expected) {
    return (memberId, app) ->
        assertThat(app)
            .as("application '%s'", app.nodeId())
            .satisfies(hasProperty(name, extractor, expected));
  }
}
