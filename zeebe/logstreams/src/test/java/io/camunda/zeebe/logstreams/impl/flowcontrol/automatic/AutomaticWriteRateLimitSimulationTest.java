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
import static org.assertj.core.api.Assertions.withinPercentage;

import io.camunda.zeebe.logstreams.impl.flowcontrol.automatic.AutomaticWriteRateController.Decision;
import io.camunda.zeebe.logstreams.impl.flowcontrol.automatic.WriteRateLimitTopology.Tenant;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Scenarios from the "automatic write rate limit" solution direction, run against {@link
 * WriteRateLimitSimulation}. Each test prints the per-window timeline so the dynamics can be
 * inspected.
 *
 * <p>Unless stated otherwise: 3 brokers with 2 CPUs each, tenants with 3 partitions and RF 3,
 * default parameters (R = 10 000, LEADER = 1, FOLLOWER = 0.6). With 3 tenants each broker hosts 9
 * replicas, so the start value is 2 777.8 and the floor 2 222.2 records/s per partition. A tenant
 * without an offered rate floods: each partition gets twice what its leader can process.
 */
class AutomaticWriteRateLimitSimulationTest {

  private static final AutomaticWriteRateLimitCfg CFG = AutomaticWriteRateLimitCfg.defaults();
  private static final double START_3_TENANTS = 2_777.8;
  private static final double START_2_TENANTS = 4_166.7;
  private static final double FLOOR_3_TENANTS = 2_222.2;

  private WriteRateLimitSimulation sim;

  @AfterEach
  void printTimeline(final TestInfo testInfo) {
    if (sim != null) {
      System.out.printf("=== %s%n%s%n", testInfo.getDisplayName(), sim.timeline());
    }
  }

  @Test
  void shouldKeepCeilingAtStartWhenAllTenantsSendAtTheirLimit() {
    // given
    sim = threeTenants(2);

    // when
    sim.run(Duration.ofMinutes(2));

    // then
    assertThat(sim.samples())
        .allSatisfy(
            s -> {
              assertThat(s.throttledRatio()).isZero();
              assertThat(s.ceiling()).isCloseTo(START_3_TENANTS, within(0.1));
              assertThat(s.decision()).isEqualTo(Decision.NO_CHANGE);
            });
    assertThat(sim.lastAcceptedRate("a")).isCloseTo(3 * START_3_TENANTS, within(1.0));
  }

  @Test
  void shouldBehaveAsTodayWithSingleTenant() {
    // given
    sim = new WriteRateLimitSimulation(CFG, 3, 2);
    sim.addTenant(new Tenant("a", 3, 3)).offering(3_000);

    // when
    sim.run(Duration.ofMinutes(1));

    // then
    assertThat(sim.brokers()).allSatisfy(b -> assertThat(b.controller.isEnabled()).isFalse());
    assertThat(sim.samples()).allSatisfy(s -> assertThat(s.decision()).isEqualTo(Decision.OFF));
    assertThat(sim.lastAcceptedRate("a")).isCloseTo(9_000, within(1.0));
  }

  @Test
  void shouldLowerCeilingWhenRecordsCostMoreThanAssumedAndRecoverAfterwards() {
    // given - the hardware only does 8 000 records per CPU-second, the controller assumes 10 000
    sim = threeTenants(2);
    sim.brokers().forEach(b -> b.withTrueRecordsPerCpuSecond(8_000));

    // when
    sim.run(Duration.ofMinutes(5));
    final var overloadEnd = sim.now();
    sim.brokers().forEach(b -> b.withTrueRecordsPerCpuSecond(10_000));
    sim.run(Duration.ofMinutes(2));

    // then - the controller lowered the ceiling, never below the floor
    final var overload = sim.samples().stream().filter(s -> s.second() <= overloadEnd).toList();
    assertThat(overload).anyMatch(s -> s.decision() == Decision.DECREASE_CPU_THROTTLED);
    assertThat(overload)
        .allSatisfy(s -> assertThat(s.ceiling()).isGreaterThanOrEqualTo(FLOOR_3_TENANTS));
    // after converging, AIMD probing still makes the backlog grow, but it stays small (about one
    // second of writes) and drains completely after each decrease
    final var converged = overload.stream().filter(s -> s.second() > 120).toList();
    assertThat(converged).allSatisfy(s -> assertThat(s.backlog()).isLessThanOrEqualTo(10_000));
    assertThat(converged)
        .filteredOn(s -> s.backlog() == 0)
        .hasSizeGreaterThan(converged.size() / 3);
    // and it goes back to the start value on demand once the cost is as assumed
    assertThat(sim.samplesSince(overloadEnd + 60))
        .allSatisfy(s -> assertThat(s.ceiling()).isCloseTo(START_3_TENANTS, within(0.1)));
  }

  @Test
  void shouldNotLowerCeilingWhenOneTenantIsLimitedByItsOwnRecords() {
    // given - enough CPU for all, but a partition of c needs 1.5 cores at the limit and a replica
    // uses at most one core
    sim = threeTenants(8);
    sim.tenant("c").withCost(1.5);

    // when
    sim.run(Duration.ofMinutes(3));

    // then
    assertThat(sim.samples())
        .allSatisfy(
            s -> {
              assertThat(s.throttledRatio()).isZero();
              assertThat(s.decision()).isEqualTo(Decision.NO_CHANGE);
            });
    assertThat(sim.lastAcceptedRate("a")).isCloseTo(3 * CFG.recordsPerCpuSecond(), within(1.0));
  }

  @Test
  void shouldNotLowerCeilingWhenTwoTenantsAreLimitedByTheirOwnRecords() {
    // given - same as above, but a and c both have expensive records; b is fine
    sim = threeTenants(8);
    sim.tenant("a").withCost(1.5);
    sim.tenant("c").withCost(1.5);

    // when
    sim.run(Duration.ofMinutes(3));

    // then - the CPU is not throttled, so b keeps its full limit
    assertThat(sim.samples())
        .allSatisfy(
            s -> {
              assertThat(s.throttledRatio()).isZero();
              assertThat(s.decision()).isEqualTo(Decision.NO_CHANGE);
            });
    assertThat(sim.lastAcceptedRate("b")).isCloseTo(3 * CFG.recordsPerCpuSecond(), within(1.0));
  }

  @Test
  void shouldKeepShareOfOtherTenantsWhenOneTenantFloods() {
    // given - a sends as much as it can, b and c want 7 500 records/s, below their limit of 8 333
    final var withoutWriteRateLimit = noisyNeighbour(10_000).withoutWriteRateLimit();
    sim = noisyNeighbour(10_000);

    // when
    withoutWriteRateLimit.run(Duration.ofMinutes(5));
    sim.run(Duration.ofMinutes(5));

    // then - backpressure alone lets a take part of the share of b and c
    assertThat(meanRate(withoutWriteRateLimit, "a")).isGreaterThan(12_000);
    assertThat(meanRate(withoutWriteRateLimit, "b")).isLessThan(7_450);
    // with the write rate limit, a is capped at its limit and b and c get all they want
    assertThat(meanRate(sim, "a")).isCloseTo(3 * START_3_TENANTS, within(1.0));
    assertThat(meanRate(sim, "b")).isCloseTo(7_500, within(1.0));
    assertThat(meanRate(sim, "c")).isCloseTo(7_500, within(1.0));
    assertThat(sim.samples()).allSatisfy(s -> assertThat(s.throttledRatio()).isZero());
  }

  @Test
  void shouldShareEquallyWhenCapacityIsLowerThanAssumed() {
    // given - the hardware does 8 000 records per CPU-second, the controller assumes 10 000. A
    // broker sustains 2 × 8 000 / 2.2 = 7 273 records/s per partition over the 3 tenants, so the
    // fair share is 7 273 records/s per tenant, less than any of them wants
    sim = noisyNeighbour(8_000);

    // when
    sim.run(Duration.ofMinutes(5));

    // then - all tenants get the same rate, close to the fair share; AIMD probing costs the rest
    final var fairShare = 2 * 8_000 / 2.2;
    assertThat(sim.samples()).anyMatch(s -> s.decision() == Decision.DECREASE_CPU_THROTTLED);
    for (final var tenant : new String[] {"a", "b", "c"}) {
      assertThat(meanRate(sim, tenant)).isBetween(0.95 * fairShare, fairShare);
    }
    assertThat(meanRate(sim, "a")).isCloseTo(meanRate(sim, "b"), withinPercentage(1));
  }

  @Test
  void shouldKeepWeightsWhenCapacityIsLowerThanAssumed() {
    // given - weights 2:1:1, all tenants send as much as they can, the hardware does 8 000 records
    // per CPU-second, the controller assumes 10 000
    final var withoutWriteRateLimit = weightedOverload().withoutWriteRateLimit();
    sim = weightedOverload();

    // when
    withoutWriteRateLimit.run(Duration.ofMinutes(5));
    sim.run(Duration.ofMinutes(5));

    // then - backpressure alone ignores the weights
    assertThat(meanRate(withoutWriteRateLimit, "a"))
        .isCloseTo(meanRate(withoutWriteRateLimit, "b"), withinPercentage(1));
    // with the write rate limit, a gets twice the rate of b and c
    assertThat(meanRate(sim, "a") / meanRate(sim, "b")).isCloseTo(2, within(0.1));
    assertThat(meanRate(sim, "b")).isCloseTo(meanRate(sim, "c"), withinPercentage(1));
  }

  @Test
  void shouldLowerCeilingForAllWhenOneTenantSendsExpensiveRecords() {
    // given - known gap: the limit counts records, not CPU
    sim = threeTenants(2);
    sim.tenant("c").withCost(3);

    // when
    sim.run(Duration.ofMinutes(3));

    // then - the CPU guard lowers the ceiling of every tenant
    assertThat(sim.samples()).anyMatch(s -> s.decision() == Decision.DECREASE_CPU_THROTTLED);
    assertThat(sim.lastAcceptedRate("a")).isLessThan(3 * START_3_TENANTS);
  }

  @Test
  void shouldNotLowerCeilingWhenExporterOfOneTenantIsSlow() {
    // given
    sim = threeTenants(2);
    sim.tenant("c").exportingAt(500);

    // when
    sim.run(Duration.ofMinutes(3));

    // then - c is held back by its exporter throttle, the others keep their limit
    assertThat(sim.samples())
        .allSatisfy(s -> assertThat(s.ceiling()).isCloseTo(START_3_TENANTS, within(0.1)));
    assertThat(sim.lastAcceptedRate("c")).isCloseTo(3 * 500, within(1.0));
    assertThat(sim.lastAcceptedRate("a")).isCloseTo(3 * START_3_TENANTS, within(1.0));
  }

  @Test
  void shouldHoldCeilingWhenAllExportersAreDown() {
    // given - first lower the ceiling through an overload
    sim = threeTenants(2);
    sim.brokers().forEach(b -> b.withTrueRecordsPerCpuSecond(8_000));
    sim.run(Duration.ofMinutes(2));
    sim.brokers().forEach(b -> b.withTrueRecordsPerCpuSecond(10_000));
    sim.tenant("a").exportingAt(0);
    sim.tenant("b").exportingAt(0);
    sim.tenant("c").exportingAt(0);
    sim.run(Duration.ofSeconds(30));
    final var ceilings = sim.brokers().stream().map(b -> b.controller.ceiling()).toList();
    final var outageStart = sim.now();

    // when
    sim.run(Duration.ofMinutes(2));

    // then - no data: the ceiling neither goes up nor down
    assertThat(sim.samplesSince(outageStart))
        .allSatisfy(
            s -> {
              assertThat(s.decision()).isEqualTo(Decision.HOLD_NO_DATA);
              assertThat(s.ceiling()).isEqualTo(ceilings.get(s.brokerId()));
            });
  }

  @Test
  void shouldHoldStartValueDuringRollingRestartWhenFailoverIsBalanced() {
    // given - 6 partitions per tenant: two rounds, so the secondaries alternate and the leaders of
    // a stopped broker spread evenly over the other two. Start value: 10 000 × 2 / (18 × 0.8)
    sim = new WriteRateLimitSimulation(CFG, 3, 2);
    sim.addTenant(new Tenant("a", 6, 3));
    sim.addTenant(new Tenant("b", 6, 3));
    sim.addTenant(new Tenant("c", 6, 3));
    sim.run(Duration.ofMinutes(1));
    final var restartStart = sim.now();

    // when
    rollingRestart();

    // then
    assertThat(sim.samplesSince(restartStart))
        .allSatisfy(
            s -> {
              assertThat(s.throttledRatio()).isZero();
              assertThat(s.decision()).isEqualTo(Decision.NO_CHANGE);
              assertThat(s.ceiling()).isCloseTo(1_388.9, within(0.1));
            });
  }

  /**
   * Known gap. With 3 partitions per tenant on 3 brokers every partition is in the first round of
   * {@code PartitionPriorityAssigner}, so the second priority is always the next broker on the
   * ring. When a broker stops, the next broker takes all its leaders: it leads 6 of its 9 replicas
   * (λ = 2/3), while the start value assumes λ = 1/2. That broker is CPU throttled and its
   * controller lowers its ceiling. The other broker does not decrease. After the restart the
   * leadership goes back and the ceiling goes back to the start value on demand.
   */
  @Test
  void shouldLowerCeilingOfTakeoverBrokerDuringRollingRestartWhenFailoverIsUnbalanced() {
    // given
    sim = threeTenants(2);
    sim.run(Duration.ofMinutes(1));
    final var restartStart = sim.now();

    // when
    rollingRestart();
    sim.run(Duration.ofMinutes(1));

    // then - only the broker that took over all leaders (6 of them) is throttled and decreases
    final var duringRestart = sim.samplesSince(restartStart);
    assertThat(duringRestart)
        .filteredOn(s -> s.leaders() == 6)
        .anyMatch(s -> s.decision() != Decision.NO_CHANGE);
    assertThat(duringRestart)
        .filteredOn(s -> s.leaders() == 3)
        .allSatisfy(s -> assertThat(s.decision()).isEqualTo(Decision.NO_CHANGE));
    assertThat(duringRestart)
        .allSatisfy(s -> assertThat(s.ceiling()).isGreaterThanOrEqualTo(FLOOR_3_TENANTS));
    assertThat(sim.brokers())
        .allSatisfy(
            b -> assertThat(b.controller.ceiling()).isCloseTo(START_3_TENANTS, within(0.1)));
  }

  @Test
  void shouldLowerLimitsOfExistingTenantsWhenTenantIsAdded() {
    // given
    sim = new WriteRateLimitSimulation(CFG, 3, 2);
    sim.addTenant(new Tenant("a", 3, 3));
    sim.addTenant(new Tenant("b", 3, 3));
    sim.run(Duration.ofMinutes(1));
    final var rateBefore = sim.lastAcceptedRate("a");

    // when
    sim.addTenant(new Tenant("c", 3, 3));
    sim.run(Duration.ofMinutes(1));

    // then
    assertThat(rateBefore).isCloseTo(3 * START_2_TENANTS, within(1.0));
    assertThat(sim.lastAcceptedRate("a")).isCloseTo(3 * START_3_TENANTS, within(1.0));
    assertThat(sim.samples()).allSatisfy(s -> assertThat(s.throttledRatio()).isZero());
  }

  @Test
  void shouldLimitFirstTenantWhenSecondTenantIsAdded() {
    // given - alone, a has no limit
    sim = new WriteRateLimitSimulation(CFG, 3, 2);
    sim.addTenant(new Tenant("a", 3, 3)).offering(5_000);
    sim.run(Duration.ofMinutes(1));
    final var rateAlone = sim.lastAcceptedRate("a");

    // when
    sim.addTenant(new Tenant("b", 3, 3)).offering(1_000);
    sim.run(Duration.ofMinutes(1));

    // then
    assertThat(rateAlone).isCloseTo(15_000, within(1.0));
    assertThat(sim.lastAcceptedRate("a")).isCloseTo(3 * START_2_TENANTS, within(1.0));
  }

  @Test
  void shouldSplitThroughputByWeight() {
    // given - a has weight 2, b weight 1 => w̄ = 1.5
    sim = new WriteRateLimitSimulation(CFG, 3, 2);
    sim.addTenant(new Tenant("a", 3, 3, 2));
    sim.addTenant(new Tenant("b", 3, 3, 1));

    // when
    sim.run(Duration.ofMinutes(2));

    // then
    assertThat(sim.lastAcceptedRate("a")).isCloseTo(3 * START_2_TENANTS * 4 / 3, within(1.0));
    assertThat(sim.lastAcceptedRate("b")).isCloseTo(3 * START_2_TENANTS * 2 / 3, within(1.0));
    assertThat(sim.samples()).allSatisfy(s -> assertThat(s.throttledRatio()).isZero());
  }

  @Test
  void shouldNotLowerCeilingWhenRejectionsCostLittleCpu() {
    // given - each broker rejects about 17 000 records/s of a; at ρ = 0.1 this is 0.17 CPU, and
    // the CPU has 0.29 left at the start value
    sim = noisyNeighbourWithRejectionCost(0.1);

    // when
    sim.run(Duration.ofMinutes(5));

    // then
    assertThat(sim.samples())
        .allSatisfy(
            s -> {
              assertThat(s.throttledRatio()).isZero();
              assertThat(s.rejectionCpuRatio()).isPositive();
            });
    assertThat(meanRate(sim, "a")).isCloseTo(3 * START_3_TENANTS, within(1.0));
    assertThat(meanRate(sim, "b")).isCloseTo(7_500, within(1.0));
  }

  /**
   * Known gap. The rejections of the flooding tenant use the CPU that b and c need. The CPU guard
   * cannot tell this from an overload, so it lowers the ceiling of every tenant to the floor, where
   * it stays while a floods. Lowering the ceiling adds rejections, but the loop does not run away:
   * one record less saves L + (RF - 1) × F = 2.2 and costs ρ in CPU per broker.
   */
  @Test
  void shouldLowerCeilingOfAllTenantsWhenRejectionsOfFloodingTenantThrottleCpu() {
    // given
    sim = noisyNeighbourWithRejectionCost(0.3);

    // when
    sim.run(Duration.ofMinutes(5));

    // then
    assertThat(sim.samplesSince(sim.now() - 120))
        .allSatisfy(
            s -> {
              assertThat(s.throttledRatio()).isOne();
              assertThat(s.ceiling()).isCloseTo(FLOOR_3_TENANTS, within(0.1));
            });
    assertThat(meanRate(sim, "b")).isCloseTo(3 * FLOOR_3_TENANTS, within(1.0));
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.1, 0.2, 0.3, 0.6, 1.0})
  void shouldGiveOtherTenantsMoreThanRequestLimiterAloneWhenRejectionsCostCpu(final double rho) {
    // given
    final var withoutWriteRateLimit = noisyNeighbourWithRejectionCost(rho).withoutWriteRateLimit();
    sim = noisyNeighbourWithRejectionCost(rho);

    // when
    withoutWriteRateLimit.run(Duration.ofMinutes(5));
    sim.run(Duration.ofMinutes(5));

    // then
    assertThat(meanRate(sim, "b")).isGreaterThan(meanRate(withoutWriteRateLimit, "b"));
    assertThat(meanRate(sim, "a")).isLessThan(meanRate(withoutWriteRateLimit, "a"));
  }

  /** Each broker is down for one minute, then up for one minute. */
  private void rollingRestart() {
    for (int broker = 0; broker < 3; broker++) {
      sim.stopBroker(broker);
      sim.run(Duration.ofMinutes(1));
      sim.startBroker(broker);
      sim.run(Duration.ofMinutes(1));
    }
  }

  /** a sends as much as it can, b and c send 2 500 records/s per partition. */
  private static WriteRateLimitSimulation noisyNeighbour(final double trueRecordsPerCpuSecond) {
    final var simulation = threeTenants(2);
    simulation.tenant("b").offering(2_500);
    simulation.tenant("c").offering(2_500);
    simulation.brokers().forEach(b -> b.withTrueRecordsPerCpuSecond(trueRecordsPerCpuSecond));
    return simulation;
  }

  /**
   * Same as {@link #noisyNeighbour(double)} with R_true = R; a rejected record of any tenant costs
   * ρ × the CPU of a record on a leader. REST has a higher ρ than gRPC.
   */
  private static WriteRateLimitSimulation noisyNeighbourWithRejectionCost(final double rho) {
    final var simulation = noisyNeighbour(10_000);
    simulation.tenant("a").withRejectionCost(rho);
    simulation.tenant("b").withRejectionCost(rho);
    simulation.tenant("c").withRejectionCost(rho);
    return simulation;
  }

  private static WriteRateLimitSimulation weightedOverload() {
    final var simulation = new WriteRateLimitSimulation(CFG, 3, 2);
    simulation.addTenant(new Tenant("a", 3, 3, 2));
    simulation.addTenant(new Tenant("b", 3, 3, 1));
    simulation.addTenant(new Tenant("c", 3, 3, 1));
    simulation.brokers().forEach(b -> b.withTrueRecordsPerCpuSecond(8_000));
    return simulation;
  }

  /** Mean accepted rate of a tenant over the last two minutes. */
  private static double meanRate(final WriteRateLimitSimulation simulation, final String tenant) {
    return simulation.samplesSince(simulation.now() - 120).stream()
        .filter(s -> s.tenantRates().containsKey(tenant))
        .mapToDouble(s -> s.tenantRates().get(tenant))
        .average()
        .orElseThrow();
  }

  private static WriteRateLimitSimulation threeTenants(final double cpuLimit) {
    final var simulation = new WriteRateLimitSimulation(CFG, 3, cpuLimit);
    simulation.addTenant(new Tenant("a", 3, 3));
    simulation.addTenant(new Tenant("b", 3, 3));
    simulation.addTenant(new Tenant("c", 3, 3));
    return simulation;
  }
}
