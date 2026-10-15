/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector;

import static io.camunda.zeebe.broker.system.syspartition.RegionTopologyTestSupport.CONTROL;
import static io.camunda.zeebe.broker.system.syspartition.RegionTopologyTestSupport.REGION_A;
import static io.camunda.zeebe.broker.system.syspartition.RegionTopologyTestSupport.REGION_B;
import static io.camunda.zeebe.broker.system.syspartition.RegionTopologyTestSupport.threeByThreeByOne;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RegionLayoutTest {

  @Test
  void shouldGroupSystemPartitionMembersByRegion() {
    // given
    final var brokerCfg = threeByThreeByOne(5);

    // when
    final var layout = RegionLayout.of(brokerCfg);

    // then
    assertThat(layout.members())
        .containsExactly(
            Map.entry(REGION_A, Set.of("region-a_0", "region-a_1")),
            Map.entry(REGION_B, Set.of("region-b_0", "region-b_1")),
            Map.entry(CONTROL, Set.of("control_0")));
    assertThat(layout.dataRegions()).containsExactly(REGION_A, REGION_B);
    assertThat(layout.isDataRegion(CONTROL)).isFalse();
  }

  @Test
  void shouldBeEmptyWithoutZones() {
    // when
    final var layout = RegionLayout.of(new BrokerCfg());

    // then
    assertThat(layout.isEmpty()).isTrue();
    assertThat(layout.dataRegions()).isEqualTo(List.of());
  }

  @Test
  void shouldAggregateRegionStatesFromMemberStates() {
    // given
    final var layout = RegionLayout.of(threeByThreeByOne(5));
    final var states =
        Map.of(
            "region-a_0", MemberLivenessState.DEAD,
            "region-a_1", MemberLivenessState.DEAD,
            "region-b_0", MemberLivenessState.HEALTHY,
            "region-b_1", MemberLivenessState.SUSPECT,
            "control_0", MemberLivenessState.HEALTHY);

    // when
    final var regionStates = layout.regionStates(states::get);

    // then
    assertThat(regionStates)
        .containsExactly(
            Map.entry(REGION_A, RegionState.FAILED),
            Map.entry(REGION_B, RegionState.SUSPECT),
            Map.entry(CONTROL, RegionState.ACTIVE));
  }
}
