/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.syspartition.detector.MemberLivenessState;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

final class SwimViewConsolidatorTest {
  private static final Set<String> BROKERS =
      Set.of(
          "region-a_0",
          "region-a_1",
          "region-a_2",
          "region-b_0",
          "region-b_1",
          "region-b_2",
          "control_0");
  private static final long GONE_THRESHOLD = 30_000;

  private final SwimViewConsolidator consolidator =
      new SwimViewConsolidator(BROKERS, GONE_THRESHOLD, 2);

  @Test
  void shouldKnowNothingWithoutReports() {
    // when
    final var views = consolidator.consolidate(List.of());

    // then
    assertThat(views.values())
        .allSatisfy(view -> assertThat(view.state()).isEqualTo(MemberLivenessState.UNKNOWN));
  }

  @Test
  void shouldMarkBrokerHealthyWhenAnyObserverSeesIt() {
    // given - one observer lost region-a_0 for a minute, another still sees it
    final var reports =
        List.of(
            report("control_0", Observation.dead("region-a_0", 60_000)),
            report("region-b_0", Observation.dead("region-a_0", 60_000)),
            report("region-b_1"));

    // when
    final var view = consolidator.consolidate(reports).get("region-a_0");

    // then - suspicions never outvote a sighting: SWIM refutes its own suspicions
    assertThat(view.state()).isEqualTo(MemberLivenessState.HEALTHY);
    assertThat(view.visibleTo()).isEqualTo(1);
  }

  @Test
  void shouldKeepBrokerHealthyThatOnlyTheControlRegionCannotSee() {
    // given - the control region is cut off from region A alone: it suspects every broker of A,
    // while region B still sees them
    final var reports =
        List.of(
            report(
                "control_0",
                Observation.suspect("region-a_0", 120_000),
                Observation.suspect("region-a_1", 120_000),
                Observation.suspect("region-a_2", 120_000)),
            report("region-b_0"),
            report("region-b_1"));

    // when
    final var views = consolidator.consolidate(reports);

    // then
    assertThat(views.get("region-a_0").state()).isEqualTo(MemberLivenessState.HEALTHY);
    assertThat(views.get("region-a_1").state()).isEqualTo(MemberLivenessState.HEALTHY);
    assertThat(views.get("region-a_2").state()).isEqualTo(MemberLivenessState.HEALTHY);
  }

  @Test
  void shouldMarkBrokerDeadWhenEnoughObserversNeverSawItForLongEnough() {
    // given
    final var reports =
        List.of(
            report("control_0", Observation.dead("region-a_0", 45_000)),
            report("region-b_0", Observation.dead("region-a_0", 50_000)),
            report("region-b_1", Observation.suspect("region-a_0", 31_000)));

    // when
    final var view = consolidator.consolidate(reports).get("region-a_0");

    // then - the shortest time counts, as all of them must have lost it for that long
    assertThat(view.state()).isEqualTo(MemberLivenessState.DEAD);
    assertThat(view.goneForMillis()).isEqualTo(31_000);
    assertThat(view.confirmations()).isEqualTo(3);
  }

  @Test
  void shouldMarkBrokerSuspectWhenOneObserverSawItRecently() {
    // given - an observer that lost the broker only 12s ago postpones the verdict
    final var reports =
        List.of(
            report("control_0", Observation.dead("region-a_0", 40_000)),
            report("region-b_0", Observation.suspect("region-a_0", 12_000)));

    // when
    final var view = consolidator.consolidate(reports).get("region-a_0");

    // then
    assertThat(view.state()).isEqualTo(MemberLivenessState.SUSPECT);
    assertThat(view.goneForMillis()).isEqualTo(12_000);
  }

  @Test
  void shouldMarkBrokerSuspectWhenTooFewObserversConfirm() {
    // given - a single observer is not enough to condemn a broker, however long it has not seen it
    final var reports = List.of(report("control_0", Observation.dead("region-a_0", 300_000)));

    // when
    final var view = consolidator.consolidate(reports).get("region-a_0");

    // then
    assertThat(view.state()).isEqualTo(MemberLivenessState.SUSPECT);
    assertThat(view.confirmations()).isEqualTo(1);
  }

  @Test
  void shouldIgnoreSuspicionsOfObserverThatSeesNoOneElse() {
    // given - region-b_0 is isolated or overloaded: it suspects everybody
    final var isolated =
        new SwimReport(
            "region-b_0",
            1,
            1,
            BROKERS.stream()
                .sorted()
                .map(
                    broker ->
                        broker.equals("region-b_0")
                            ? Observation.alive(broker)
                            : Observation.suspect(broker, 90_000))
                .toList());
    final var reports =
        List.of(isolated, report("control_0", Observation.dead("region-a_0", 60_000)));

    // when
    final var view = consolidator.consolidate(reports).get("region-a_0");

    // then - only control_0 counts, which is too few to condemn region-a_0
    assertThat(view.state()).isEqualTo(MemberLivenessState.SUSPECT);
    assertThat(view.confirmations()).isEqualTo(1);
  }

  @Test
  void shouldNotCountObserversWithoutEvidenceAsConfirmations() {
    // given - control_0 never saw region-a_0 alive, so it abstains
    final var reports =
        List.of(
            report("control_0", Observation.unknown("region-a_0")),
            report("region-b_0", Observation.dead("region-a_0", 60_000)));

    // when
    final var view = consolidator.consolidate(reports).get("region-a_0");

    // then
    assertThat(view.state()).isEqualTo(MemberLivenessState.SUSPECT);
    assertThat(view.confirmations()).isEqualTo(1);
  }

  @Test
  void shouldMarkBrokerUnknownWhenNoObserverHasEvidence() {
    // given
    final var reports =
        List.of(
            report("control_0", Observation.unknown("region-a_0")),
            report("region-b_0", Observation.unknown("region-a_0")));

    // when
    final var view = consolidator.consolidate(reports).get("region-a_0");

    // then
    assertThat(view.state()).isEqualTo(MemberLivenessState.UNKNOWN);
  }

  @Test
  void shouldTreatTheReportOfABrokerAsProofOfItsLife() {
    // given - region-a_0 reaches the leader, although its peers lost sight of it
    final var reports =
        List.of(
            report("region-a_0"),
            report("control_0", Observation.dead("region-a_0", 60_000)),
            report("region-b_0", Observation.dead("region-a_0", 60_000)));

    // when
    final var view = consolidator.consolidate(reports).get("region-a_0");

    // then
    assertThat(view.state()).isEqualTo(MemberLivenessState.HEALTHY);
  }

  private static SwimReport report(final String observer, final Observation... overrides) {
    final var byMember = new TreeMap<String, Observation>();
    BROKERS.forEach(broker -> byMember.put(broker, Observation.alive(broker)));
    for (final var override : overrides) {
      byMember.put(override.memberId(), override);
    }
    return new SwimReport(observer, 1, 1, new ArrayList<>(byMember.values()));
  }
}
