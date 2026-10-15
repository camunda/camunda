/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.ticktock;

import static io.camunda.zeebe.broker.system.syspartition.RegionTopologyTestSupport.REGION_A;
import static io.camunda.zeebe.broker.system.syspartition.RegionTopologyTestSupport.REGION_B;
import static io.camunda.zeebe.broker.system.syspartition.RegionTopologyTestSupport.threeByThreeByOne;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.syspartition.detector.RegionLayout;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class RegionFailoverDeciderTest {
  private static final long TICK = 100;
  private static final LivenessThresholds THRESHOLDS = new LivenessThresholds(2, 4);

  private final RegionFailoverDecider decider =
      new RegionFailoverDecider(RegionLayout.of(threeByThreeByOne(5)), THRESHOLDS);

  @Test
  void shouldFailOverFromFailedRegionToActiveRegion() {
    // given - both brokers of region A missed more than 4 ticks
    final var view =
        Map.of(
            "region-a_0", TICK - 5,
            "region-a_1", TICK - 6,
            "region-b_0", TICK - 1,
            "region-b_1", TICK - 1,
            "control_0", TICK - 1);

    // when
    final var failover = decider.decide(TICK, view, "region-b_0");

    // then
    assertThat(failover)
        .hasValueSatisfying(
            value -> {
              assertThat(value.getFailedRegion()).isEqualTo(REGION_A);
              assertThat(value.getTargetRegion()).isEqualTo(REGION_B);
              assertThat(value.getTickId()).isEqualTo(TICK);
              assertThat(value.getLeaderId()).isEqualTo("region-b_0");
              assertThat(value.getTocks()).isEqualTo(view);
            });
  }

  @Test
  void shouldNotFailOverWhileRegionHasLiveBroker() {
    // given - only one broker of region A is dead
    final var view =
        Map.of(
            "region-a_0", TICK - 5,
            "region-a_1", TICK - 1,
            "region-b_0", TICK - 1,
            "region-b_1", TICK - 1,
            "control_0", TICK - 1);

    // when - then
    assertThat(decider.decide(TICK, view, "region-b_0")).isEmpty();
  }

  @Test
  void shouldNotFailOverToRegionWhichIsNotActive() {
    // given - region A failed, but a broker of region B is suspect
    final var view =
        Map.of(
            "region-a_0", TICK - 5,
            "region-a_1", TICK - 5,
            "region-b_0", TICK - 1,
            "region-b_1", TICK - 3,
            "control_0", TICK - 1);

    // when - then
    assertThat(decider.decide(TICK, view, "region-b_0")).isEmpty();
  }

  @Test
  void shouldNotFailOverWhenOnlyControlRegionFailed() {
    // given
    final var view =
        Map.of(
            "region-a_0", TICK - 1,
            "region-a_1", TICK - 1,
            "region-b_0", TICK - 1,
            "region-b_1", TICK - 1,
            "control_0", TICK - 5);

    // when - then
    assertThat(decider.decide(TICK, view, "region-b_0")).isEmpty();
  }

  @Test
  void shouldNotFailOverRegionNeverSeen() {
    // given - region A brokers never acknowledged a tick, e.g. still starting
    final var view = Map.of("region-b_0", TICK - 1, "region-b_1", TICK - 1, "control_0", TICK - 1);

    // when - then
    assertThat(decider.decide(TICK, view, "region-b_0")).isEmpty();
  }

  @Test
  void shouldNotFailOverWithoutRegions() {
    // given
    final var withoutRegions = new RegionFailoverDecider(RegionLayout.NONE, THRESHOLDS);

    // when - then
    assertThat(withoutRegions.decide(TICK, Map.of("0", 1L), "1")).isEmpty();
  }

  @Test
  void shouldRoundTripFailoverValue() {
    // given
    final var failover =
        new RegionFailoverValue()
            .setFailedRegion(REGION_A)
            .setTargetRegion(REGION_B)
            .setTickId(TICK)
            .setLeaderId("control_0")
            .setTocks(Map.of("region-b_0", TICK - 1));
    final var read = new RegionFailoverValue();

    // when
    read.wrap(BufferUtil.createCopy(failover));

    // then
    assertThat(read.getFailedRegion()).isEqualTo(REGION_A);
    assertThat(read.getTargetRegion()).isEqualTo(REGION_B);
    assertThat(read.getTickId()).isEqualTo(TICK);
    assertThat(read.getLeaderId()).isEqualTo("control_0");
    assertThat(read.getTocks()).containsExactly(Map.entry("region-b_0", TICK - 1));
  }
}
