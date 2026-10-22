/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import static io.camunda.zeebe.broker.system.syspartition.RegionTopologyTestSupport.threeByThreeByOne;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.SwimCfg;
import io.camunda.zeebe.broker.system.syspartition.detector.MemberLivenessState;
import io.camunda.zeebe.broker.system.syspartition.detector.RegionLayout;
import io.camunda.zeebe.broker.system.syspartition.detector.RegionState;
import io.camunda.zeebe.broker.system.syspartition.detector.swim.RegionFailoverDecision.CommittedFailover;
import io.camunda.zeebe.broker.system.syspartition.detector.swim.SwimDetectorCore.Evaluation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Drives the system partition brokers of a 3x3x1 cluster, two data regions of 3 brokers and a
 * control region of 1, through a deterministic simulation: a fake clock advances in report
 * intervals, a simplified SWIM model decides what every broker sees of every other broker from a
 * matrix of cut links and stopped brokers, and the reports travel only along links that are not
 * cut. SWIM's unreliability is the point: views are local, lag, are wrong in one place but not
 * another, and brokers restart with a clean memory.
 */
final class SwimDetectorSimulationTest {
  private static final String A0 = "region-a_0";
  private static final String A1 = "region-a_1";
  private static final String A2 = "region-a_2";
  private static final String B0 = "region-b_0";
  private static final String B1 = "region-b_1";
  private static final String B2 = "region-b_2";
  private static final String C0 = "control_0";
  private static final String[] REGION_A = {A0, A1, A2};

  @Test
  void shouldFailOverAfterDetectionAndRecoveryWindowsWithEvidence() {
    // given
    final var sim = new Sim();
    sim.electLeader(B0);

    // when - region A is gone: detected after the 30s detection window, failed over from after the
    // 30s recovery window
    sim.stop(REGION_A);
    sim.run(Duration.ofSeconds(25));
    assertThat(sim.leaderEvaluation().detection()).isEmpty();
    sim.run(Duration.ofSeconds(10));

    // then - detected, but the leader waits for the region to recover
    assertThat(sim.leaderEvaluation().detection()).isPresent();
    assertThat(sim.failovers()).isEmpty();
    sim.run(Duration.ofSeconds(20));
    assertThat(sim.failovers()).isEmpty();
    sim.run(Duration.ofSeconds(15));

    // and then it fails over, with which brokers were out of sight and for how long
    assertThat(sim.failovers()).hasSize(1);
    final var failover = sim.failovers().getFirst();
    assertThat(failover.failedRegion()).isEqualTo("region-a");
    assertThat(failover.targetRegion()).isEqualTo("region-b");
    assertThat(failover.leaderId()).isEqualTo(B0);
    assertThat(failover.term()).isEqualTo(1);
    assertThat(failover.decidedAtMillis() - failover.detectedAtMillis())
        .isGreaterThanOrEqualTo(30_000);
    assertThat(failover.goneBrokers()).extracting(GoneBroker::memberId).containsExactly(A0, A1, A2);
    assertThat(failover.goneBrokers())
        .allSatisfy(
            gone -> {
              // out of sight for the detection window and the recovery window
              assertThat(gone.goneForMillis()).isGreaterThanOrEqualTo(60_000);
              // confirmed by the three system partition brokers outside the region
              assertThat(gone.confirmations()).isEqualTo(3);
            });
  }

  @Test
  void shouldNotFailOverWhenTheRegionRecoversDuringTheRecoveryWindow() {
    // given - region A is detected as gone
    final var sim = new Sim();
    sim.electLeader(B0);
    sim.stop(REGION_A);
    sim.run(Duration.ofSeconds(45));
    assertThat(sim.leaderEvaluation().detection()).isPresent();

    // when - it recovers before the recovery window is over
    sim.start(REGION_A);
    sim.run(Duration.ofSeconds(10));

    // then
    assertThat(sim.leaderEvaluation().detection()).isEmpty();
    sim.run(Duration.ofMinutes(5));
    assertThat(sim.failovers()).isEmpty();
  }

  @Test
  void shouldStartTheRecoveryWindowOverWhenTheRegionRecoversAndIsGoneAgain() {
    // given - region A was detected as gone, and recovered within the recovery window
    final var sim = new Sim();
    sim.electLeader(B0);
    sim.stop(REGION_A);
    sim.run(Duration.ofSeconds(50));
    sim.start(REGION_A);
    sim.run(Duration.ofSeconds(5));

    // when - it is gone again
    sim.stop(REGION_A);
    sim.run(Duration.ofSeconds(55));

    // then - the first outage does not count towards the second
    assertThat(sim.failovers()).isEmpty();
    sim.run(Duration.ofSeconds(15));
    assertThat(sim.failovers()).hasSize(1);
  }

  @ParameterizedTest
  @CsvSource({"region-b_0,false", "region-b_0,true", "control_0,false", "control_0,true"})
  void shouldNotFailOverWhenTheControlRegionIsCutOffFromTheRegionAlone(
      final String leader, final boolean indirectProbes) {
    // given - the control region can reach region B, and region B can reach region A, but the
    // control region cannot reach region A, whatever SWIM's indirect probes make of it
    final var sim = new Sim();
    sim.indirectProbes(indirectProbes);
    sim.electLeader(leader);

    // when
    for (final var broker : REGION_A) {
      sim.cut(C0, broker);
    }
    sim.run(Duration.ofMinutes(5));

    // then - region B still sees region A, so it is alive, also for the control region's leader
    assertThat(sim.failovers()).isEmpty();
    assertThat(sim.evaluationOf(leader).views().get(A0).state())
        .isEqualTo(MemberLivenessState.HEALTHY);
    assertThat(sim.evaluationOf(leader).regionStates().get("region-a"))
        .isEqualTo(RegionState.ACTIVE);
  }

  @Test
  void shouldNotFailOverWhenTheLeaderIsIsolated() {
    // given - the control broker is the preferred leader, and gets cut off from everyone
    final var sim = new Sim();
    sim.electLeader(C0);

    // when
    sim.isolate(C0);
    sim.run(Duration.ofMinutes(5));

    // then - an observer that sees nobody else is ignored, so it condemns nobody
    assertThat(sim.failovers()).isEmpty();
    assertThat(sim.evaluationOf(C0).regionStates().get("region-a"))
        .isNotEqualTo(RegionState.FAILED);
  }

  @Test
  void shouldNotFailOverFromARegionThatFlaps() {
    // given
    final var sim = new Sim();
    sim.electLeader(B0);

    // when - region A is out of sight for 20s, long enough for SWIM to remove it, then back for
    // 3s, over and over: its time out of sight never reaches the detection window
    for (int cycle = 0; cycle < 8; cycle++) {
      sim.stop(REGION_A);
      sim.run(Duration.ofSeconds(20));
      sim.start(REGION_A);
      sim.run(Duration.ofSeconds(3));
    }

    // then
    assertThat(sim.failovers()).isEmpty();
  }

  @Test
  void shouldNotFailOverBecauseOfASingleDegradedObserver() {
    // given - one broker keeps losing region A, for good, as its own link is bad
    final var sim = new Sim();
    sim.electLeader(B0);

    // when
    for (final var broker : REGION_A) {
      sim.cut(B1, broker);
    }
    sim.run(Duration.ofMinutes(5));

    // then - everybody else sees region A, and a sighting is never outvoted
    assertThat(sim.failovers()).isEmpty();
  }

  @Test
  void shouldNotFailOverWhenOnlyOneObserverHasEvidence() {
    // given - region A is gone, and then two of the three brokers that saw it restart, so they
    // have no memory of ever seeing it alive
    final var sim = new Sim();
    sim.electLeader(B0);
    sim.stop(REGION_A);
    sim.run(Duration.ofSeconds(10));
    sim.restart(B1);
    sim.restart(C0);

    // when
    sim.run(Duration.ofMinutes(10));

    // then - one observer is not enough to condemn a region, and a restarted observer abstains
    // instead of claiming a broker is gone that it never saw
    assertThat(sim.failovers()).isEmpty();
    final var view = sim.evaluationOf(B0).views().get(A0);
    assertThat(view.state()).isEqualTo(MemberLivenessState.SUSPECT);
    assertThat(view.confirmations()).isEqualTo(1);
  }

  @Test
  void shouldNotFailOverFromARegionThatWasNeverSeen() {
    // given - region A never came up, e.g. while the cluster is still starting
    final var sim = new Sim(REGION_A);
    sim.electLeader(B0);

    // when
    sim.run(Duration.ofMinutes(10));

    // then
    assertThat(sim.failovers()).isEmpty();
    assertThat(sim.evaluationOf(B0).views().get(A0).state()).isEqualTo(MemberLivenessState.UNKNOWN);
  }

  @Test
  void shouldIgnoreAnObserverWhoseReportsAreLost() {
    // given - region B's second broker is alive, but none of its reports get through
    final var sim = new Sim();
    sim.electLeader(B0);
    sim.silence(B1);

    // when
    sim.stop(REGION_A);
    sim.run(Duration.ofSeconds(100));

    // then - the two other observers confirm region A is gone, and region B is still healthy
    assertThat(sim.failovers()).hasSize(1);
    assertThat(sim.failovers().getFirst().goneBrokers())
        .allSatisfy(gone -> assertThat(gone.confirmations()).isEqualTo(2));
  }

  @Test
  void shouldNotFailOverInAHealthyClusterWhenHalfOfTheReportsAreLost() {
    // given
    final var sim = new Sim();
    sim.reportLoss(0.5);
    sim.electLeader(B0);

    // when
    sim.run(Duration.ofMinutes(10));

    // then - a lost report only makes an observer silent, which never condemns anyone
    assertThat(sim.failovers()).isEmpty();
  }

  @Test
  void shouldStillFailOverWhenHalfOfTheReportsAreLost() {
    // given
    final var sim = new Sim();
    sim.reportLoss(0.5);
    sim.electLeader(B0);

    // when
    sim.stop(REGION_A);
    sim.run(Duration.ofMinutes(5));

    // then
    assertThat(sim.failovers()).hasSize(1);
  }

  @Test
  void shouldNotFailOverAgainAfterALeaderChangeUntilTheRegionRecoveredAndFailedAgain() {
    // given - region A was failed over from
    final var sim = new Sim();
    sim.electLeader(B0);
    sim.stop(REGION_A);
    sim.run(Duration.ofSeconds(100));
    assertThat(sim.failovers()).hasSize(1);

    // when - another broker leads, and region A is still gone
    sim.electLeader(B1);
    sim.run(Duration.ofMinutes(3));

    // then - it does not fail over again
    assertThat(sim.failovers()).hasSize(1);

    // and when region A recovers and is gone again, it does
    sim.start(REGION_A);
    sim.run(Duration.ofSeconds(20));
    sim.stop(REGION_A);
    sim.run(Duration.ofSeconds(100));
    assertThat(sim.failovers()).hasSize(2);
    assertThat(sim.failovers().getLast().leaderId()).isEqualTo(B1);
    assertThat(sim.failovers().getLast().term()).isEqualTo(2);
  }

  @Test
  void shouldWaitForFreshReportsAndTheRecoveryWindowAfterALeaderChange() {
    // given - region A has been gone for 90s, and nobody led
    final var sim = new Sim();
    sim.stop(REGION_A);
    sim.run(Duration.ofSeconds(90));

    // when - a leader is elected
    sim.electLeader(B1);
    sim.run(Duration.ofSeconds(3));

    // then - it first waits for fresh reports, which the observers measured the outage in
    assertThat(sim.leaderEvaluation().detection()).isEmpty();
    sim.run(Duration.ofSeconds(5));
    assertThat(sim.leaderEvaluation().detection()).isPresent();

    // and then for the recovery window, but not for another detection window
    sim.run(Duration.ofSeconds(25));
    assertThat(sim.failovers()).isEmpty();
    sim.run(Duration.ofSeconds(10));
    assertThat(sim.failovers()).hasSize(1);
  }

  @Test
  void shouldFailOverFromRegionBToRegionAWhenRegionBIsGone() {
    // given
    final var sim = new Sim();
    sim.electLeader(A0);

    // when
    sim.stop(B0, B1, B2);
    sim.run(Duration.ofSeconds(100));

    // then
    assertThat(sim.failovers()).hasSize(1);
    assertThat(sim.failovers().getFirst().failedRegion()).isEqualTo("region-b");
    assertThat(sim.failovers().getFirst().targetRegion()).isEqualTo("region-a");
  }

  /** The system partition brokers of a 3x3x1 cluster and a model of what SWIM makes of them. */
  private static final class Sim {
    private static final Duration STEP = Duration.ofSeconds(1);
    private static final long SWIM_FAILURE_TIMEOUT_NANOS = Duration.ofSeconds(10).toNanos();
    private static final long WALL_CLOCK_BASE_MILLIS = 1_700_000_000_000L;

    private final RegionLayout layout = RegionLayout.of(threeByThreeByOne(5));
    private final SwimCfg cfg = new SwimCfg();
    private final Set<String> observers = new TreeSet<>();
    private final Set<String> brokers;
    private final AtomicLong nanos = new AtomicLong();
    private final Map<String, SwimDetectorCore> cores = new TreeMap<>();
    private final Set<String> down = new HashSet<>();
    private final Set<Set<String>> cuts = new HashSet<>();
    private final Set<String> silenced = new HashSet<>();
    private final Map<String, Long> invisibleSinceNanos = new HashMap<>();
    private final Map<String, Evaluation> evaluations = new HashMap<>();
    private final List<RegionFailoverDecision> failovers = new ArrayList<>();
    private final Random random = new Random(42);
    private boolean indirectProbes;
    private double reportLoss;
    private long nextEpoch = 1;
    private long term;
    private @Nullable String leader;
    private @Nullable CommittedFailover latestCommitted;

    /**
     * @param downFromStart the brokers that never come up
     */
    Sim(final String... downFromStart) {
      layout.members().values().forEach(observers::addAll);
      brokers = layout.allBrokers();
      observers.forEach(observer -> cores.put(observer, newCore(observer)));
      down.addAll(List.of(downFromStart));
      run(Duration.ofSeconds(10));
    }

    void indirectProbes(final boolean indirectProbes) {
      this.indirectProbes = indirectProbes;
    }

    void reportLoss(final double reportLoss) {
      this.reportLoss = reportLoss;
    }

    void silence(final String observer) {
      silenced.add(observer);
    }

    void stop(final String... members) {
      down.addAll(List.of(members));
    }

    void start(final String... members) {
      down.removeAll(List.of(members));
    }

    /** Starts the broker with a clean memory. */
    void restart(final String member) {
      down.remove(member);
      if (cores.containsKey(member)) {
        cores.put(member, newCore(member));
      }
      if (member.equals(leader)) {
        leader = null;
      }
      invisibleSinceNanos.keySet().removeIf(key -> key.startsWith(member + "|"));
    }

    void cut(final String a, final String b) {
      cuts.add(Set.of(a, b));
    }

    void isolate(final String member) {
      brokers.stream().filter(other -> !other.equals(member)).forEach(other -> cut(member, other));
    }

    void electLeader(final String member) {
      if (leader != null) {
        cores.get(leader).stepDown();
      }
      leader = member;
      cores.get(member).becomeLeader(++term, latestCommitted);
    }

    List<RegionFailoverDecision> failovers() {
      return failovers;
    }

    Evaluation evaluationOf(final String member) {
      return evaluations.get(member);
    }

    Evaluation leaderEvaluation() {
      return evaluations.get(leader);
    }

    void run(final Duration duration) {
      for (long i = 0; i < duration.toSeconds(); i++) {
        step();
      }
    }

    private void step() {
      nanos.addAndGet(STEP.toNanos());
      updateViews();
      deliverReports();
      evaluate();
    }

    private SwimDetectorCore newCore(final String member) {
      return new SwimDetectorCore(member, observers, brokers, layout, cfg, nanos::get, nextEpoch++);
    }

    /** What SWIM lists on every running broker: reachable, unreachable, or removed. */
    private void updateViews() {
      final var now = nanos.get();
      cores.forEach(
          (observer, core) -> {
            if (down.contains(observer)) {
              return;
            }
            final var listed = new HashMap<String, Boolean>();
            for (final var member : brokers) {
              final var key = observer + "|" + member;
              if (visible(observer, member)) {
                invisibleSinceNanos.remove(key);
                listed.put(member, true);
              } else {
                final var since = invisibleSinceNanos.computeIfAbsent(key, ignored -> now);
                if (now - since < SWIM_FAILURE_TIMEOUT_NANOS) {
                  listed.put(member, false);
                }
              }
            }
            core.reconcile(listed);
          });
    }

    private boolean visible(final String observer, final String member) {
      if (observer.equals(member)) {
        return true;
      }
      if (down.contains(observer) || down.contains(member)) {
        return false;
      }
      if (connected(observer, member)) {
        return true;
      }
      // SWIM asks other brokers to probe on its behalf
      return indirectProbes
          && brokers.stream()
              .anyMatch(
                  relay ->
                      !relay.equals(observer)
                          && !relay.equals(member)
                          && !down.contains(relay)
                          && connected(observer, relay)
                          && connected(relay, member));
    }

    private boolean connected(final String a, final String b) {
      return !cuts.contains(Set.of(a, b));
    }

    private void deliverReports() {
      cores.forEach(
          (observer, core) -> {
            if (down.contains(observer)) {
              return;
            }
            final var report = core.report();
            if (silenced.contains(observer)) {
              return;
            }
            cores.forEach(
                (peer, peerCore) -> {
                  if (!peer.equals(observer)
                      && !down.contains(peer)
                      && connected(observer, peer)
                      && random.nextDouble() >= reportLoss) {
                    peerCore.onReport(report);
                  }
                });
          });
    }

    private void evaluate() {
      final var wallMillis = WALL_CLOCK_BASE_MILLIS + TimeUnit.NANOSECONDS.toMillis(nanos.get());
      cores.forEach(
          (member, core) -> {
            if (down.contains(member)) {
              return;
            }
            final var evaluation = core.evaluate(wallMillis);
            evaluations.put(member, evaluation);
            if (member.equals(leader) && evaluation.failover().isPresent()) {
              commit(evaluation.failover().get());
            }
          });
    }

    private void commit(final RegionFailoverDecision decision) {
      failovers.add(decision);
      latestCommitted = decision.committed();
      cores.values().forEach(core -> core.onFailoverCommitted(latestCommitted));
    }
  }
}
