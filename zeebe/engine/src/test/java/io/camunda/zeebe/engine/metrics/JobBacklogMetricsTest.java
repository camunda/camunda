/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Recorder-level unit tests: verifies that {@link JobBacklogMetrics} exposes the activatable job
 * backlog and the activation lag under the documented names and tags, and that the lag is computed
 * from the registry clock when the gauge is read.
 */
final class JobBacklogMetricsTest {

  private static final String BACKLOG_METRIC = "zeebe.job.activatable.count";
  private static final String LAG_METRIC = "zeebe.job.activation.lag";
  private static final double LAG_TOLERANCE = 0.001;

  private MockClock clock;
  private SimpleMeterRegistry registry;
  private JobBacklogMetrics metrics;

  @BeforeEach
  void setUp() {
    clock = new MockClock();
    registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    metrics = new JobBacklogMetrics(registry);
  }

  @Test
  void shouldIncrementBacklogPerType() {
    // given
    final var type = "type-increment";

    // when
    metrics.incrementActivatable(type);
    metrics.incrementActivatable(type);
    metrics.incrementActivatable(type);

    // then
    assertThat(backlog(type)).isEqualTo(3.0);
  }

  @Test
  void shouldDecrementBacklogPerType() {
    // given
    final var type = "type-decrement";
    metrics.incrementActivatable(type);
    metrics.incrementActivatable(type);
    metrics.incrementActivatable(type);

    // when
    metrics.decrementActivatable(type);

    // then
    assertThat(backlog(type)).isEqualTo(2.0);
  }

  @Test
  void shouldIgnoreDecrementsOfDrainedType() {
    // given
    final var type = "type-floor";
    metrics.incrementActivatable(type);

    // when - decrements beyond the drained level are ignored, they do not go negative
    metrics.decrementActivatable(type);
    metrics.decrementActivatable(type);
    metrics.decrementActivatable(type);

    // then
    assertThat(backlogOrZero(type)).isZero();
  }

  @Test
  void shouldNotFailWhenDecrementingTypeThatWasNeverIncremented() {
    // given
    final var type = "type-never-seen";

    // when
    final ThrowingCallable decrement = () -> metrics.decrementActivatable(type);

    // then
    assertThatCode(decrement).doesNotThrowAnyException();
  }

  @Test
  void shouldStartFreshEntryWhenIncrementingAfterExtraDecrements() {
    // given - decrements of a drained type are ignored and leave no offset behind
    final var type = "type-floor-recover";
    metrics.incrementActivatable(type);
    metrics.decrementActivatable(type);
    metrics.decrementActivatable(type);

    // when
    metrics.incrementActivatable(type);

    // then
    assertThat(backlog(type)).isEqualTo(1.0);
  }

  @Test
  void shouldTrackBacklogOfDifferentTypesIndependently() {
    // given
    final var typeA = "type-independent-a";
    final var typeB = "type-independent-b";

    // when
    metrics.incrementActivatable(typeA);
    metrics.incrementActivatable(typeA);
    metrics.incrementActivatable(typeB);
    metrics.decrementActivatable(typeA);

    // then
    assertThat(backlog(typeA)).isEqualTo(1.0);
    assertThat(backlog(typeB)).isEqualTo(1.0);
  }

  @Test
  void shouldSetBacklogOfGivenTypesOnReset() {
    // given
    final var typeA = "type-reset-a";
    final var typeB = "type-reset-b";
    metrics.incrementActivatable(typeA);

    // when
    metrics.resetActivatable(Map.of(typeA, 5L, typeB, 2L));

    // then
    assertThat(backlog(typeA)).isEqualTo(5.0);
    assertThat(backlog(typeB)).isEqualTo(2.0);
  }

  @Test
  void shouldZeroKnownTypesMissingFromReset() {
    // given
    final var stale = "type-reset-stale";
    final var kept = "type-reset-kept";
    metrics.incrementActivatable(stale);
    metrics.incrementActivatable(stale);
    metrics.incrementActivatable(kept);

    // when - the re-seed is authoritative, so a type absent from the persisted state has no jobs
    metrics.resetActivatable(Map.of(kept, 4L));

    // then
    assertThat(backlogOrZero(stale)).isZero();
    assertThat(backlog(kept)).isEqualTo(4.0);
  }

  @Test
  void shouldDiscardAccumulatedLevelOnReset() {
    // given - mutations replayed before the recovery hook runs
    final var type = "type-reset-discard";
    for (int i = 0; i < 10; i++) {
      metrics.incrementActivatable(type);
    }

    // when
    metrics.resetActivatable(Map.of(type, 3L));

    // then
    assertThat(backlog(type)).isEqualTo(3.0);
  }

  @Test
  void shouldReportZeroLagWithoutBacklog() {
    // given
    final var type = "type-lag-empty";
    metrics.incrementActivatable(type);
    metrics.decrementActivatable(type);

    // when
    clock.add(Duration.ofSeconds(100));

    // then
    assertThat(lag(type)).isZero();
  }

  @Test
  void shouldReportLagGrowingWithClockWhileBacklogExists() {
    // given
    final var type = "type-lag-grows";
    metrics.incrementActivatable(type);

    // when
    clock.add(Duration.ofSeconds(10));
    final var lagAfterTen = lag(type);
    clock.add(Duration.ofSeconds(5));
    final var lagAfterFifteen = lag(type);

    // then
    assertThat(lagAfterTen).isCloseTo(10.0, within(LAG_TOLERANCE));
    assertThat(lagAfterFifteen).isCloseTo(15.0, within(LAG_TOLERANCE));
  }

  @Test
  void shouldReportZeroLagWhenBacklogDrainsWithoutActivation() {
    // given
    final var type = "type-lag-drained";
    metrics.incrementActivatable(type);
    clock.add(Duration.ofSeconds(10));

    // when - e.g. the last job is cancelled
    metrics.decrementActivatable(type);
    clock.add(Duration.ofSeconds(10));

    // then
    assertThat(lag(type)).isZero();
  }

  @Test
  void shouldMeasureLagFromWhenBacklogBecamePositive() {
    // given - the type sat empty for a long time before the first job arrived
    final var type = "type-lag-since-positive";
    metrics.incrementActivatable(type);
    metrics.decrementActivatable(type);
    clock.add(Duration.ofSeconds(100));

    // when
    metrics.incrementActivatable(type);
    clock.add(Duration.ofSeconds(4));

    // then - idle time without backlog does not count as lag
    assertThat(lag(type)).isCloseTo(4.0, within(LAG_TOLERANCE));
  }

  @Test
  void shouldResetLagOnActivation() {
    // given
    final var type = "type-lag-activation";
    metrics.incrementActivatable(type);
    metrics.incrementActivatable(type);
    clock.add(Duration.ofSeconds(10));

    // when - one of two jobs is activated, so the backlog stays positive
    metrics.recordActivation(type);
    metrics.decrementActivatable(type);
    clock.add(Duration.ofSeconds(3));

    // then
    assertThat(lag(type)).isCloseTo(3.0, within(LAG_TOLERANCE));
  }

  @Test
  void shouldReportZeroLagWhenActivationDrainsBacklog() {
    // given
    final var type = "type-lag-activation-drain";
    metrics.incrementActivatable(type);
    clock.add(Duration.ofSeconds(10));

    // when
    metrics.recordActivation(type);
    metrics.decrementActivatable(type);
    clock.add(Duration.ofSeconds(10));

    // then
    assertThat(backlog(type)).isZero();
    assertThat(lag(type)).isZero();
  }

  @Test
  void shouldReportCorrectBacklogAfterTypeDrainedAndReturned() {
    // given
    final var type = "type-drained-and-returned";
    metrics.incrementActivatable(type);
    metrics.incrementActivatable(type);
    final var whileFilled = backlog(type);

    // when - the type drains completely
    metrics.decrementActivatable(type);
    metrics.decrementActivatable(type);
    final var whileDrained = backlog(type);

    // and a job of that type shows up again
    metrics.incrementActivatable(type);
    final var afterReturn = backlog(type);

    // then
    assertThat(whileFilled).isEqualTo(2.0);
    assertThat(whileDrained).isZero();
    assertThat(afterReturn).isEqualTo(1.0);
  }

  @Test
  void shouldStartLagAtBacklogNotAtEarlierActivationWhenTypeWasIdle() {
    // given - an activation recorded while the type had no backlog
    final var type = "type-lag-idle-activation";
    metrics.recordActivation(type);
    clock.add(Duration.ofSeconds(100));

    // when
    metrics.incrementActivatable(type);
    clock.add(Duration.ofSeconds(10));

    // then - lag counts from the job's arrival, not from the old activation
    assertThat(lag(type)).isCloseTo(10.0, within(LAG_TOLERANCE));
  }

  @Test
  void shouldReadZeroLagForDrainedType() {
    // given
    final var type = "type-lag-drained-zero";
    metrics.incrementActivatable(type);
    clock.add(Duration.ofSeconds(10));

    // when
    metrics.decrementActivatable(type);
    clock.add(Duration.ofSeconds(50));

    // then
    assertThat(backlog(type)).isZero();
    assertThat(lag(type)).isZero();
  }

  @Test
  void shouldTrackLagOfDifferentTypesIndependently() {
    // given
    final var activated = "type-lag-independent-activated";
    final var starved = "type-lag-independent-starved";
    metrics.incrementActivatable(activated);
    metrics.incrementActivatable(starved);
    clock.add(Duration.ofSeconds(10));

    // when - only one type is activated
    metrics.recordActivation(activated);
    clock.add(Duration.ofSeconds(2));

    // then
    assertThat(lag(activated)).isCloseTo(2.0, within(LAG_TOLERANCE));
    assertThat(lag(starved)).isCloseTo(12.0, within(LAG_TOLERANCE));
  }

  @Test
  void shouldTagMetersWithType() {
    // given
    final var type = "type-tag";

    // when
    metrics.incrementActivatable(type);

    // then
    assertThat(registry.get(BACKLOG_METRIC).tag("type", type).gauge()).isNotNull();
    assertThat(registry.get(LAG_METRIC).tag("type", type).gauge()).isNotNull();
  }

  private double backlog(final String type) {
    return registry.get(BACKLOG_METRIC).tag("type", type).gauge().value();
  }

  /** An absent series means zero: a type without backlog may have no gauge after a reset. */
  private double backlogOrZero(final String type) {
    final var gauge = registry.find(BACKLOG_METRIC).tag("type", type).gauge();
    return gauge == null ? 0.0 : gauge.value();
  }

  private double lag(final String type) {
    return registry.get(LAG_METRIC).tag("type", type).gauge().value();
  }
}
