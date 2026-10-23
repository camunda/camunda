/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol.automatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.zeebe.logstreams.impl.flowcontrol.automatic.WriteRateLimitTopology.Tenant;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class WriteRateLimitBoundsTest {

  private static final AutomaticWriteRateLimitCfg CFG = AutomaticWriteRateLimitCfg.defaults();

  @Test
  void shouldDeriveFloorAndStartFromTopology() {
    // given - 3 brokers, 2 tenants with 3 partitions and RF 3 => 6 replicas per broker
    final var topology =
        new WriteRateLimitTopology(3, List.of(new Tenant("a", 3, 3), new Tenant("b", 3, 3)));

    // when
    final var bounds = WriteRateLimitBounds.of(topology, 2, CFG);

    // then - floor: λ = 1 => 10 000 × 2 / (6 × 1)
    assertThat(bounds.floor()).isCloseTo(3_333.3, within(0.1));
    // start: λ = 3 / (2 × 3) = 0.5 => cost 0.8 => 10 000 × 2 / (6 × 0.8)
    assertThat(bounds.start()).isCloseTo(4_166.7, within(0.1));
    assertThat(bounds.cap()).isEqualTo(bounds.start());
  }

  @Test
  void shouldUseFloorAsStartWithSingleBroker() {
    // given
    final var topology =
        new WriteRateLimitTopology(1, List.of(new Tenant("a", 2, 1), new Tenant("b", 2, 1)));

    // when
    final var bounds = WriteRateLimitBounds.of(topology, 2, CFG);

    // then
    assertThat(bounds.floor()).isCloseTo(5_000, within(0.1));
    assertThat(bounds.start()).isEqualTo(bounds.floor());
  }

  @Test
  void shouldNotExceedRecordsPerCpuWhenCpuIsPlentiful() {
    // given
    final var topology =
        new WriteRateLimitTopology(3, List.of(new Tenant("a", 3, 3), new Tenant("b", 3, 3)));

    // when
    final var bounds = WriteRateLimitBounds.of(topology, 64, CFG);

    // then
    assertThat(bounds.floor()).isEqualTo(CFG.recordsPerCpuSecond());
    assertThat(bounds.start()).isEqualTo(CFG.recordsPerCpuSecond());
  }

  @Test
  void shouldLowerStartWhenTenantIsAdded() {
    // given
    final var twoTenants =
        new WriteRateLimitTopology(3, List.of(new Tenant("a", 3, 3), new Tenant("b", 3, 3)));
    final var threeTenants =
        new WriteRateLimitTopology(
            3, List.of(new Tenant("a", 3, 3), new Tenant("b", 3, 3), new Tenant("c", 3, 3)));

    // when
    final var before = WriteRateLimitBounds.of(twoTenants, 2, CFG);
    final var after = WriteRateLimitBounds.of(threeTenants, 2, CFG);

    // then - 6 → 9 replicas per broker
    assertThat(after.start()).isCloseTo(before.start() * 6 / 9, within(0.1));
  }

  @Test
  void shouldSplitCeilingByWeightWithoutChangingCpuDemand() {
    // given - same partitions, a has twice the weight of b => w̄ = 1.5
    final var topology =
        new WriteRateLimitTopology(3, List.of(new Tenant("a", 3, 3, 2), new Tenant("b", 3, 3, 1)));
    final var bounds = WriteRateLimitBounds.of(topology, 2, CFG);
    final var ceiling = 3_000;

    // when
    final var limitA = bounds.limitFor(ceiling, 2);
    final var limitB = bounds.limitFor(ceiling, 1);

    // then
    assertThat(limitA).isCloseTo(4_000, within(0.1));
    assertThat(limitB).isCloseTo(2_000, within(0.1));
    // the sum over all replicas is the same as with equal weights
    assertThat(3 * limitA + 3 * limitB).isCloseTo(6.0 * ceiling, within(0.1));
  }

  @Test
  void shouldGiveTenantWithMorePartitionsMoreTotalThroughput() {
    // given
    final var topology =
        new WriteRateLimitTopology(3, List.of(new Tenant("a", 6, 3), new Tenant("b", 3, 3)));
    final var bounds = WriteRateLimitBounds.of(topology, 2, CFG);

    // when
    final var totalA = 6 * bounds.limitFor(bounds.start(), 1);
    final var totalB = 3 * bounds.limitFor(bounds.start(), 1);

    // then
    assertThat(totalA).isCloseTo(2 * totalB, within(0.1));
  }

  @Test
  void shouldBeOffWithSingleTenant() {
    // given
    final var topology = new WriteRateLimitTopology(3, List.of(new Tenant("a", 3, 3)));

    // when
    final var controller = new AutomaticWriteRateController(CFG, topology, OptionalDouble.of(2));

    // then
    assertThat(controller.isEnabled()).isFalse();
    assertThat(controller.limitFor(1)).isEmpty();
  }

  @Test
  void shouldBeOffWithoutCpuLimit() {
    // given
    final var topology =
        new WriteRateLimitTopology(3, List.of(new Tenant("a", 3, 3), new Tenant("b", 3, 3)));

    // when
    final var controller = new AutomaticWriteRateController(CFG, topology, OptionalDouble.empty());

    // then
    assertThat(controller.isEnabled()).isFalse();
    assertThat(controller.limitFor(1)).isEmpty();
  }
}
