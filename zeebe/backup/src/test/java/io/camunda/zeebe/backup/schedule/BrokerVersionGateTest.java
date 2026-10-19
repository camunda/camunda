/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import java.util.ArrayList;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

final class BrokerVersionGateTest {

  private static final String CURRENT = "8.10.0";

  @Test
  void shouldOpenWhenAllBrokersRunTheCurrentVersion() {
    // given
    final var topology = topology(3, CURRENT, CURRENT, CURRENT);

    // when
    final var upgraded = BrokerVersionGate.allBrokersUpgraded(topology, CURRENT);

    // then
    assertThat(upgraded).isTrue();
  }

  @Test
  void shouldStayClosedWithOneOldBroker() {
    // given
    final var topology = topology(3, CURRENT, "8.9.4", CURRENT);

    // when
    final var upgraded = BrokerVersionGate.allBrokersUpgraded(topology, CURRENT);

    // then
    assertThat(upgraded).isFalse();
  }

  @Test
  void shouldStayClosedWhenABrokerIsMissingFromTheTopology() {
    // given
    final var topology = topology(3, CURRENT, CURRENT);

    // when
    final var upgraded = BrokerVersionGate.allBrokersUpgraded(topology, CURRENT);

    // then
    assertThat(upgraded).isFalse();
  }

  @Test
  void shouldStayClosedWhenABrokerVersionIsUnknown() {
    // given
    final var topology = topology(3, CURRENT, null, CURRENT);

    // when
    final var upgraded = BrokerVersionGate.allBrokersUpgraded(topology, CURRENT);

    // then
    assertThat(upgraded).isFalse();
  }

  @Test
  void shouldStayClosedWhenABrokerVersionCannotBeParsed() {
    // given
    final var topology = topology(1, "not-a-version");

    // when
    final var upgraded = BrokerVersionGate.allBrokersUpgraded(topology, CURRENT);

    // then
    assertThat(upgraded).isFalse();
  }

  @Test
  void shouldStayClosedWithoutTopology() {
    assertThat(BrokerVersionGate.allBrokersUpgraded(null, CURRENT)).isFalse();
    assertThat(BrokerVersionGate.allBrokersUpgraded(topology(1), CURRENT)).isFalse();
  }

  @Test
  void shouldOpenWhenOnlyThePatchVersionDiffers() {
    // given
    final var topology = topology(3, "8.10.0", "8.10.3", "8.10.1");

    // when
    final var upgraded = BrokerVersionGate.allBrokersUpgraded(topology, "8.10.1");

    // then
    assertThat(upgraded).isTrue();
  }

  @Test
  void shouldIgnoreThePreReleaseSuffix() {
    // given
    final var topology = topology(2, "8.10.0-SNAPSHOT", "8.10.0");

    // when
    final var upgraded = BrokerVersionGate.allBrokersUpgraded(topology, "8.10.0-SNAPSHOT");

    // then
    assertThat(upgraded).isTrue();
  }

  @Test
  void shouldStayClosedForAnOldSnapshotBroker() {
    // given
    final var topology = topology(2, "8.9.0-SNAPSHOT", "8.10.0-SNAPSHOT");

    // when
    final var upgraded = BrokerVersionGate.allBrokersUpgraded(topology, "8.10.0-SNAPSHOT");

    // then
    assertThat(upgraded).isFalse();
  }

  @Test
  void shouldCountANewerBrokerAsUpgraded() {
    assertThat(BrokerVersionGate.isUpgraded("8.11.0", CURRENT)).isTrue();
    assertThat(BrokerVersionGate.isUpgraded("9.0.0", CURRENT)).isTrue();
  }

  private static BrokerClusterState topology(
      final int clusterSize, final @Nullable String... versions) {
    final var topology = mock(BrokerClusterState.class);
    final var brokers = new ArrayList<BrokerMemberId>();
    final @Nullable String[] versionList = versions;
    for (int i = 0; i < versionList.length; i++) {
      final var broker = BrokerMemberId.from(i);
      brokers.add(broker);
      when(topology.getBrokerVersion(broker)).thenReturn(versionList[i]);
    }
    when(topology.isInitialized()).thenReturn(true);
    when(topology.getClusterSize()).thenReturn(clusterSize);
    when(topology.getBrokers()).thenReturn(brokers);
    return topology;
  }
}
