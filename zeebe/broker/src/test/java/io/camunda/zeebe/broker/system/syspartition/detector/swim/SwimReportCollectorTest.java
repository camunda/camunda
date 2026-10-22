/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SwimReportCollectorTest {
  private static final long TIMEOUT = Duration.ofSeconds(5).toNanos();

  private final SwimReportCollector collector = new SwimReportCollector(Set.of("a", "b"));

  @Test
  void shouldKeepLatestReportOfEachObserver() {
    // when
    collector.onReport(report("a", 1, 1), 0);
    collector.onReport(report("a", 1, 2), 10);
    collector.onReport(report("b", 1, 1), 10);

    // then
    assertThat(collector.fresh(10, TIMEOUT))
        .extracting(SwimReport::observerId, SwimReport::sequence)
        .containsExactlyInAnyOrder(tuple("a", 2L), tuple("b", 1L));
  }

  @Test
  void shouldDropReportsFromBrokersNotRunningTheSystemPartition() {
    // when
    final var accepted = collector.onReport(report("gateway", 1, 1), 0);

    // then
    assertThat(accepted).isFalse();
    assertThat(collector.fresh(0, TIMEOUT)).isEmpty();
  }

  @Test
  void shouldDropReportsThatArriveOutOfOrder() {
    // given
    collector.onReport(report("a", 1, 5), 0);

    // when
    final var olderAccepted = collector.onReport(report("a", 1, 4), 1);
    final var duplicateAccepted = collector.onReport(report("a", 1, 5), 2);

    // then
    assertThat(olderAccepted).isFalse();
    assertThat(duplicateAccepted).isFalse();
    assertThat(collector.fresh(2, TIMEOUT)).extracting(SwimReport::sequence).containsExactly(5L);
  }

  @Test
  void shouldAcceptReportsOfRestartedObserverWhoseSequenceStartedOver() {
    // given
    collector.onReport(report("a", 1, 500), 0);

    // when - the observer restarted: new epoch, sequence from 0
    final var accepted = collector.onReport(report("a", 2, 0), 1);

    // then
    assertThat(accepted).isTrue();
    assertThat(collector.fresh(1, TIMEOUT)).extracting(SwimReport::epoch).containsExactly(2L);
  }

  @Test
  void shouldIgnoreObserversThatWentSilent() {
    // given
    collector.onReport(report("a", 1, 1), 0);
    collector.onReport(report("b", 1, 1), TIMEOUT);

    // when - a has not reported for longer than the timeout
    final var fresh = collector.fresh(TIMEOUT + 1, TIMEOUT);

    // then
    assertThat(fresh).extracting(SwimReport::observerId).containsExactly("b");
  }

  private static SwimReport report(final String observer, final long epoch, final long sequence) {
    return new SwimReport(observer, epoch, sequence, List.of(Observation.alive(observer)));
  }
}
