/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class SwimMembershipTrackerTest {
  private final AtomicLong nanos = new AtomicLong(1_000);
  private final SwimMembershipTracker tracker =
      new SwimMembershipTracker("a", Set.of("a", "b", "c"), nanos::get);

  @Test
  void shouldSeeItselfAliveAndOthersUnknown() {
    // when
    final var observations = tracker.observations();

    // then
    assertThat(observations)
        .containsExactly(
            Observation.alive("a"), Observation.unknown("b"), Observation.unknown("c"));
  }

  @Test
  void shouldNotCondemnABrokerItNeverSawAlive() {
    // given - a broker that is still starting is suspected and even removed by SWIM
    tracker.onSuspect("b");
    advance(Duration.ofMinutes(5));
    tracker.onDead("b");

    // when - then
    assertThat(tracker.observations()).contains(Observation.unknown("b"));
  }

  @Test
  void shouldMeasureTimeOutOfSightFromFirstSuspicion() {
    // given
    tracker.onAlive("b");
    advance(Duration.ofSeconds(10));
    tracker.onSuspect("b");

    // when
    advance(Duration.ofSeconds(3));

    // then
    assertThat(tracker.observations()).contains(Observation.suspect("b", 3_000));
  }

  @Test
  void shouldKeepTimeOutOfSightWhenSuspicionBecomesDeath() {
    // given
    tracker.onAlive("b");
    tracker.onSuspect("b");
    advance(Duration.ofSeconds(4));

    // when
    tracker.onDead("b");
    advance(Duration.ofSeconds(6));

    // then - the whole outage counts, not only the time since SWIM removed the broker
    assertThat(tracker.observations()).contains(Observation.dead("b", 10_000));
  }

  @Test
  void shouldCountFromRemovalWhenBrokerWasNeverSuspected() {
    // given
    tracker.onAlive("b");
    advance(Duration.ofSeconds(10));
    tracker.onDead("b");

    // when
    advance(Duration.ofSeconds(2));

    // then
    assertThat(tracker.observations()).contains(Observation.dead("b", 2_000));
  }

  @Test
  void shouldForgiveAtOnceWhenBrokerIsSeenAliveAgain() {
    // given - a suspicion SWIM refuted
    tracker.onAlive("b");
    tracker.onSuspect("b");
    advance(Duration.ofSeconds(8));
    tracker.onAlive("b");
    advance(Duration.ofSeconds(1));

    // when - a later suspicion starts over, it does not add up with the refuted one
    tracker.onSuspect("b");
    advance(Duration.ofSeconds(2));

    // then
    assertThat(tracker.observations()).contains(Observation.suspect("b", 2_000));
  }

  @Test
  void shouldIgnoreBrokersOutsideTheCluster() {
    // when
    tracker.onAlive("gateway");
    tracker.onSuspect("gateway");

    // then
    assertThat(tracker.observations()).extracting(Observation::memberId).doesNotContain("gateway");
  }

  @Test
  void shouldNeverSuspectItself() {
    // when
    tracker.onSuspect("a");
    tracker.onDead("a");

    // then
    assertThat(tracker.observations()).contains(Observation.alive("a"));
  }

  @Test
  void shouldAlignWithTheMembersSwimListsWhenReconciling() {
    // given - events about b and c were missed
    tracker.onAlive("b");
    tracker.onAlive("c");
    advance(Duration.ofSeconds(1));

    // when - b is listed as unreachable, c is not listed anymore
    tracker.reconcile(Map.of("a", true, "b", false));
    advance(Duration.ofSeconds(2));

    // then
    assertThat(tracker.observations())
        .containsExactly(
            Observation.alive("a"), Observation.suspect("b", 2_000), Observation.dead("c", 2_000));
  }

  @Test
  void shouldRecoverWhenReconcilingBrokerListedReachableAgain() {
    // given
    tracker.onAlive("b");
    tracker.onDead("b");

    // when
    tracker.reconcile(Map.of("a", true, "b", true));

    // then
    assertThat(tracker.observations()).contains(Observation.alive("b"));
  }

  private void advance(final Duration duration) {
    nanos.addAndGet(duration.toNanos());
  }
}
