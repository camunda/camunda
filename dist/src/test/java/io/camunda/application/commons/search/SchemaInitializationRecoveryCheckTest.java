/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.MemberId;
import io.camunda.application.commons.pt.PerTenantSchemaInitialization.Deferral;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Covers the bounded wait this check adds on top of the topology manager's own answer. The wait
 * exists for exactly one ambiguous state - a node that can see no broker of the tenant, which is
 * either a cluster that does not exist or one it has not discovered yet - and getting its bounds
 * wrong fails in both directions: too short recreates the indices of a tenant mid-restore, too long
 * outlives the liveness grace and has the decision taken by the orchestrator instead.
 *
 * <p>The grace is passed in rather than faked through a clock, so each case picks a bound that
 * makes its own outcome the only possible one: a grace no test can outlast, or one that has already
 * expired before the check is built.
 */
@Timeout(30)
final class SchemaInitializationRecoveryCheckTest {

  private static final String TENANT = "default";

  /** Longer than any test run, so "still waiting" can only mean the check decided to wait. */
  private static final Duration NEVER_EXPIRES = Duration.ofHours(1);

  /** Expired before the check is even built, so "not waiting" can only mean the grace lapsed. */
  private static final Duration ALREADY_EXPIRED = Duration.ZERO;

  @Test
  void shouldWaitOutTheDiscoveryGraceWhenNoBrokerCanAnswer() {
    // given - a node that has discovered no broker of this tenant. Either there is no cluster, or
    // membership discovery has not landed yet, and it cannot yet tell which.
    final var check = checkFor(noClusterConfiguration(), noBrokersDiscovered(), NEVER_EXPIRES);

    // when / then - it holds off rather than answering into a restore it may simply not see yet
    assertThat(check.test(TENANT)).isTrue();
    assertThat(check.shouldDefer(TENANT)).isEqualTo(Deferral.PENDING);
  }

  @Test
  void shouldInitializeOnceTheDiscoveryGraceHasExpired() {
    // given - the same node, given a window to discover a cluster and finding none
    final var check =
        checkFor(noClusterConfiguration(), noBrokersDiscovered(), Duration.ofMillis(50));

    // when / then - waiting longer changes nothing here, and something has to create the schema. A
    // node that can see no cluster is restarted by its liveness probe, so an unbounded wait would
    // never lift anyway.
    Awaitility.await("the grace lapses and the tenant stops being deferred")
        .atMost(Duration.ofSeconds(10))
        .until(() -> !check.test(TENANT));
  }

  @Test
  void shouldKeepWaitingPastTheGraceWhileTheTenantsBrokersAreVisible() {
    // given - a node that has discovered a broker of the tenant but has not been gossiped a
    // configuration yet, on a grace that has already run out
    final var check = checkFor(noClusterConfiguration(), brokerDiscovered(), ALREADY_EXPIRED);

    // when / then - that broker will gossip the mode, so there is nothing ambiguous left to time
    // out
    assertThat(check.test(TENANT)).isTrue();
    assertThat(check.shouldDefer(TENANT)).isEqualTo(Deferral.DEFERRED);
  }

  @Test
  void shouldKeepWaitingPastTheGraceWhileTheTenantIsRecovering() {
    // given - a mode that is known, and known to be recovering
    final var check =
        checkFor(clusterConfigurationWith(Mode.RECOVERING), noBrokersDiscovered(), ALREADY_EXPIRED);

    // when / then - the grace never applies to a decided answer; the restore may take hours
    assertThat(check.test(TENANT)).isTrue();
    assertThat(check.shouldDefer(TENANT)).isEqualTo(Deferral.DEFERRED);
  }

  @Test
  void shouldInitializeImmediatelyWhenTheTenantIsProcessing() {
    // given
    final var check =
        checkFor(clusterConfigurationWith(Mode.PROCESSING), brokerDiscovered(), NEVER_EXPIRES);

    // when / then - a decided answer costs no wait at all
    assertThat(check.test(TENANT)).isFalse();
    assertThat(check.shouldDefer(TENANT)).isEqualTo(Deferral.NONE);
  }

  private static SchemaInitializationRecoveryCheck checkFor(
      final CurrentClusterConfiguration configuration,
      final List<BrokerMemberId> discoveredBrokers,
      final Duration discoveryGrace) {
    final var topology = mock(BrokerClusterState.class);
    when(topology.getBrokers()).thenReturn(discoveredBrokers);
    final var topologyManager = mock(BrokerTopologyManager.class);
    when(topologyManager.getClusterConfiguration()).thenReturn(configuration);
    when(topologyManager.getTopology(anyString())).thenReturn(topology);
    return new SchemaInitializationRecoveryCheck(topologyManager, discoveryGrace);
  }

  private static List<BrokerMemberId> noBrokersDiscovered() {
    return List.of();
  }

  private static List<BrokerMemberId> brokerDiscovered() {
    return List.of(BrokerMemberId.from(0));
  }

  private static CurrentClusterConfiguration noClusterConfiguration() {
    return CurrentClusterConfiguration.uninitialized();
  }

  private static CurrentClusterConfiguration clusterConfigurationWith(final Mode mode) {
    final var group =
        PartitionGroupConfiguration.empty(PartitionGroupConfiguration.INITIAL_VERSION)
            .addMember(
                MemberId.from("0"), new BrokerPartitionState(1, Instant.EPOCH, Map.of(), mode));
    final var base = CurrentClusterConfiguration.uninitialized();
    return new CurrentClusterConfiguration(
        base.version(),
        base.globalConfiguration(),
        Map.of(TENANT, group),
        base.phasedChangeState());
  }
}
