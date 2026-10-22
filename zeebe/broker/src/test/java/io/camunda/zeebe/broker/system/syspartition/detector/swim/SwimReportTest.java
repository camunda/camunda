/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SwimReportTest {

  @Test
  void shouldRoundTripThroughItsEncoding() {
    // given
    final var report =
        new SwimReport(
            "region-b_0",
            1_700_000_000_000L,
            42,
            List.of(
                Observation.alive("region-b_0"),
                Observation.unknown("region-a_2"),
                Observation.suspect("region-a_0", 4_500),
                Observation.dead("region-a_1", 61_000)));

    // when
    final var decoded = SwimReport.decode(report.encode());

    // then
    assertThat(decoded).isEqualTo(report);
  }

  @Test
  void shouldTreatBrokerItDoesNotMentionAsUnknown() {
    // given
    final var report = new SwimReport("a", 1, 1, List.of(Observation.alive("a")));

    // when - then
    assertThat(report.observationOf("b")).isEqualTo(Observation.unknown("b"));
  }

  @Test
  void shouldBeCredibleWhenItSeesAnotherBrokerAlive() {
    // given
    final var report =
        new SwimReport(
            "a",
            1,
            1,
            List.of(Observation.alive("a"), Observation.alive("b"), Observation.dead("c", 9)));

    // when - then
    assertThat(report.isCredible()).isTrue();
  }

  @Test
  void shouldNotBeCredibleWhenItSeesOnlyItself() {
    // given - an observer that lost every other broker is more likely isolated than right
    final var report =
        new SwimReport(
            "a",
            1,
            1,
            List.of(Observation.alive("a"), Observation.suspect("b", 9), Observation.dead("c", 9)));

    // when - then
    assertThat(report.isCredible()).isFalse();
  }
}
