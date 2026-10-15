/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import static io.camunda.zeebe.broker.system.syspartition.RegionTopologyTestSupport.CONTROL;
import static io.camunda.zeebe.broker.system.syspartition.RegionTopologyTestSupport.REGION_A;
import static io.camunda.zeebe.broker.system.syspartition.RegionTopologyTestSupport.REGION_B;
import static io.camunda.zeebe.broker.system.syspartition.RegionTopologyTestSupport.threeByThreeByOne;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import org.junit.jupiter.api.Test;

final class SystemPartitionMembersTest {

  @Test
  void shouldSpreadReplicationFactorRoundRobinAcrossZones() {
    // given
    final var brokerCfg = threeByThreeByOne(5);

    // when
    final var members = SystemPartition.members(brokerCfg);

    // then - 2 brokers in each data region and the control broker
    assertThat(members)
        .containsExactlyInAnyOrder(
            MemberId.from(REGION_A, 0),
            MemberId.from(REGION_A, 1),
            MemberId.from(REGION_B, 0),
            MemberId.from(REGION_B, 1),
            MemberId.from(CONTROL, 0));
  }

  @Test
  void shouldReplicateOnEveryBrokerByDefault() {
    // given
    final var brokerCfg = threeByThreeByOne(null);

    // when
    final var members = SystemPartition.members(brokerCfg);

    // then
    assertThat(members).hasSize(7).contains(MemberId.from(CONTROL, 0));
  }

  @Test
  void shouldUseLowestNodeIdsWithoutZones() {
    // given
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setClusterSize(5);
    brokerCfg.getCluster().getSystemPartition().setReplicationFactor(3);

    // when
    final var members = SystemPartition.members(brokerCfg);

    // then
    assertThat(members)
        .containsExactlyInAnyOrder(MemberId.from("0"), MemberId.from("1"), MemberId.from("2"));
  }

  @Test
  void shouldPreferControlZoneVoterAsLeader() {
    // given
    final var brokerCfg = threeByThreeByOne(5);

    // when
    final var metadata = SystemPartition.metadata(brokerCfg);

    // then - the control voter has the highest priority, and all priorities are distinct
    final var control = MemberId.from(CONTROL, 0);
    assertThat(metadata.getPrimary()).contains(control);
    assertThat(metadata.getTargetPriority()).isEqualTo(5);
    assertThat(metadata.getPriority(control)).isEqualTo(5);
    assertThat(metadata.members().stream().map(metadata::getPriority)).doesNotHaveDuplicates();
  }
}
