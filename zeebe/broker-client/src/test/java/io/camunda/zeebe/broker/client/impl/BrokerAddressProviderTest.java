/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class BrokerAddressProviderTest {

  private static final String TENANT_B = "tenant-b";
  private static final int PARTITION_ID = 1;
  private static final BrokerMemberId NODE = BrokerMemberId.from(0);

  @Test
  void shouldResolveRecoveringNodeOnlyForItsOwnGroup() {
    // given -- node is recovering in tenant-b's partition group but merely processing in
    // default's, and is inactive for partition 1 in both groups' live topology
    final var topologyManager =
        new TestTopologyManager()
            .addInactiveNode(TENANT_B, PARTITION_ID, NODE)
            .addInactiveNode(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, PARTITION_ID, NODE)
            .withClusterConfiguration(
                new CurrentClusterConfiguration(
                    CurrentClusterConfiguration.INITIAL_VERSION,
                    GlobalConfiguration.init(),
                    Map.of(
                        PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                        groupWithMember(BrokerPartitionState.initialize(Map.of())),
                        TENANT_B,
                        groupWithMember(
                            BrokerPartitionState.initialize(Map.of()).setMode(Mode.RECOVERING))),
                    PhasedChangeState.empty()));

    // when -- tenant-b is queried, recovering in that group
    final var tenantBAddress =
        BrokerAddressProvider.leaderOrAnyRecovery(
                topologyManager, new PartitionId(TENANT_B, PARTITION_ID))
            .get();

    // then
    assertThat(tenantBAddress).isEqualTo("address-" + NODE.id());

    // when -- default is queried, only processing (not recovering) in that group
    final var defaultAddress =
        BrokerAddressProvider.leaderOrAnyRecovery(
                topologyManager,
                new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, PARTITION_ID))
            .get();

    // then -- the node is not treated as recovering in the default group
    assertThat(defaultAddress).isNull();
  }

  private static PartitionGroupConfiguration groupWithMember(final BrokerPartitionState state) {
    return new PartitionGroupConfiguration(
        PartitionGroupConfiguration.INITIAL_VERSION,
        PartitionGroupConfiguration.INITIAL_INCARNATION_NUMBER,
        Map.of(NODE.memberId(), state),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }
}
