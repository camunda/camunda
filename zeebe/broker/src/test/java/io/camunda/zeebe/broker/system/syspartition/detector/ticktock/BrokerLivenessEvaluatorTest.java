/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.ticktock;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.syspartition.detector.MemberLivenessState;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class BrokerLivenessEvaluatorTest {
  private static final int SUSPECT_THRESHOLD = 2;
  private static final int DEAD_THRESHOLD = 4;

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final TickTockMetrics metrics = new TickTockMetrics(meterRegistry);
  private final BrokerLivenessEvaluator evaluator =
      new BrokerLivenessEvaluator(
          Set.of("0", "1", "2"), SUSPECT_THRESHOLD, DEAD_THRESHOLD, metrics);

  @AfterEach
  void tearDown() {
    metrics.close();
  }

  @Test
  void shouldConsiderBrokerNeverSeenUnknown() {
    // when
    evaluator.evaluate(100, Map.of("0", 100L));

    // then
    assertThat(evaluator.stateOf("1")).isEqualTo(MemberLivenessState.UNKNOWN);
    assertThat(state("1")).isEqualTo(MemberLivenessState.UNKNOWN.code());
  }

  @Test
  void shouldConsiderBrokerHealthyOnceSeen() {
    // given
    evaluator.evaluate(1, Map.of("0", 1L));

    // when
    evaluator.evaluate(2, Map.of("0", 2L, "1", 1L));

    // then
    assertThat(evaluator.stateOf("1")).isEqualTo(MemberLivenessState.HEALTHY);
  }

  @Test
  void shouldMoveFromHealthyToSuspectToDeadAsTicksAreMissed() {
    // given
    final var lastSeen = Map.of("0", 10L, "1", 10L, "2", 10L);

    // when - then
    evaluator.evaluate(10 + SUSPECT_THRESHOLD, lastSeen);
    assertThat(evaluator.stateOf("2")).isEqualTo(MemberLivenessState.HEALTHY);

    evaluator.evaluate(10 + SUSPECT_THRESHOLD + 1, lastSeen);
    assertThat(evaluator.stateOf("2")).isEqualTo(MemberLivenessState.SUSPECT);
    assertThat(state("2")).isEqualTo(MemberLivenessState.SUSPECT.code());

    evaluator.evaluate(10 + DEAD_THRESHOLD, lastSeen);
    assertThat(evaluator.stateOf("2")).isEqualTo(MemberLivenessState.SUSPECT);

    evaluator.evaluate(10 + DEAD_THRESHOLD + 1, lastSeen);
    assertThat(evaluator.stateOf("2")).isEqualTo(MemberLivenessState.DEAD);
    assertThat(state("2")).isEqualTo(MemberLivenessState.DEAD.code());
    assertThat(missedTicks("2")).isEqualTo(DEAD_THRESHOLD + 1);
  }

  @Test
  void shouldConsiderSuspectBrokerHealthyAgainOnceSeen() {
    // given
    evaluator.evaluate(10, Map.of("0", 10L, "1", 10L, "2", 7L));
    assertThat(evaluator.stateOf("2")).isEqualTo(MemberLivenessState.SUSPECT);

    // when
    evaluator.evaluate(11, Map.of("0", 11L, "1", 10L, "2", 10L));

    // then
    assertThat(evaluator.stateOf("2")).isEqualTo(MemberLivenessState.HEALTHY);
  }

  @Test
  void shouldConsiderDeadBrokerHealthyAgainOnceSeen() {
    // given
    evaluator.evaluate(20, Map.of("0", 20L, "1", 19L, "2", 1L));
    assertThat(evaluator.stateOf("2")).isEqualTo(MemberLivenessState.DEAD);

    // when
    evaluator.evaluate(21, Map.of("0", 21L, "1", 20L, "2", 20L));

    // then
    assertThat(evaluator.stateOf("2")).isEqualTo(MemberLivenessState.HEALTHY);
    assertThat(missedTicks("2")).isOne();
  }

  @Test
  void shouldRemoveMetersWhenClosed() {
    // given
    evaluator.evaluate(1, Map.of("0", 1L));

    // when
    metrics.close();

    // then
    assertThat(meterRegistry.getMeters()).isEmpty();
  }

  private double missedTicks(final String broker) {
    return meterRegistry
        .get(TickTockMetricsDoc.MISSED_TICKS.getName())
        .tag("broker", broker)
        .gauge()
        .value();
  }

  private double state(final String broker) {
    return meterRegistry
        .get(TickTockMetricsDoc.BROKER_STATE.getName())
        .tag("broker", broker)
        .gauge()
        .value();
  }
}
