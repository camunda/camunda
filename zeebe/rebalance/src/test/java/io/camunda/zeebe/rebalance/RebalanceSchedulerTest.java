/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.rebalance.ClusterLeadershipStatus.State;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.NotCoordinatorException;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.util.schedule.Schedule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RebalanceSchedulerTest {

  private static final Duration INTERVAL = Duration.ofHours(1);
  private static final Duration WINDOW = Duration.ofMinutes(5);
  private static final MemberId COORDINATOR = MemberId.from("0");
  private static final MemberId OTHER = MemberId.from("1");
  private static final RebalanceStatus UNBALANCED =
      new RebalanceStatus(null, null, new ClusterLeadershipStatus(State.UNBALANCED, List.of()));

  private final ControlledInstantSource clock =
      new ControlledInstantSource(Instant.parse("2026-01-01T00:00:00Z"));
  private final RecordingExecutor executor = new RecordingExecutor();
  private final FakeCoordinator coordinator = new FakeCoordinator();
  private final FakeLoadSource loadSource = new FakeLoadSource();
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final ClusterRebalanceMetrics metrics = new ClusterRebalanceMetrics(registry);

  @Test
  void shouldStartMeasuringOneWindowBeforeTheFirstRunIsDue() {
    // given
    final var scheduler = scheduler(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 10.0));

    // when
    scheduler.start();

    // then
    assertThat(executor.delays).containsExactly(INTERVAL.minus(WINDOW));
    assertThat(coordinator.triggered).isZero();
  }

  @Test
  void shouldWaitUntilDueWhenThereIsNothingToMeasure() {
    // given
    final var scheduler = scheduler(Map.of());

    // when
    scheduler.start();

    // then
    assertThat(executor.delays).containsExactly(INTERVAL);
  }

  @Test
  void shouldSkipADueTimeTooSoonToMeasureTheWholeWindowFor() {
    // given
    final var window = INTERVAL.plus(WINDOW);
    final var scheduler = scheduler(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 10.0), window);

    // when
    scheduler.start();

    // then
    assertThat(executor.delays).containsExactly(INTERVAL.multipliedBy(2).minus(window));
  }

  @Test
  void shouldScheduleTheNextRunWhileTheCurrentOneIsStillMeasuring() {
    // given
    final var window = INTERVAL.plus(WINDOW);
    startedScheduler(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 10.0), window);
    loadSource.completes = false;
    clock.advance(INTERVAL.multipliedBy(2).minus(window));

    // when
    executor.runDue();

    // then
    assertThat(loadSource.askedMembers).isNotNull();
    assertThat(executor.pending).hasSize(1);
    assertThat(executor.delays).last().isEqualTo(INTERVAL);
  }

  @Test
  void shouldNotStartARebalanceOnceClosed() {
    // given
    final var scheduler = startedScheduler(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 10.0));
    loadSource.load = load(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 0.0), Set.of());
    loadSource.duringWindow = scheduler::close;

    // when
    executor.runDue();

    // then
    assertThat(coordinator.triggered).isZero();
  }

  @Test
  void shouldSkipWhenMembersChangedDuringTheWindow() {
    // given
    final var scheduler = startedScheduler(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 10.0));
    loadSource.load = load(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 0.0), Set.of());
    loadSource.duringWindow =
        () ->
            scheduler.onClusterConfigurationUpdated(
                clusterOf(COORDINATOR, OTHER, MemberId.from("2")));

    // when
    executor.runDue();

    // then
    assertThat(coordinator.triggered).isZero();
    assertThat(runs(ScheduledRebalanceOutcome.LOAD_UNKNOWN)).isOne();
  }

  @Test
  void shouldStartARebalanceWhenTheClusterIsQuiet() {
    // given
    final var scheduler = startedScheduler(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 10.0));
    loadSource.load = load(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 9.0), Set.of());

    // when
    executor.runDue();

    // then
    assertThat(coordinator.triggered).isOne();
    assertThat(loadSource.askedMembers).containsExactlyInAnyOrder(COORDINATOR, OTHER);
    assertThat(runs(ScheduledRebalanceOutcome.STARTED)).isOne();
  }

  @Test
  void shouldSkipWhenAnyMeasureExceedsItsThreshold() {
    // given
    final var scheduler =
        startedScheduler(
            Map.of(
                LoadMeasure.ROOT_PROCESS_INSTANCES, 10.0, LoadMeasure.PROCESSED_COMMANDS, 100.0));
    loadSource.load =
        load(
            Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 1.0, LoadMeasure.PROCESSED_COMMANDS, 101.0),
            Set.of());

    // when
    executor.runDue();

    // then
    assertThat(coordinator.triggered).isZero();
    assertThat(runs(ScheduledRebalanceOutcome.BUSY)).isOne();
  }

  @Test
  void shouldSkipWhenNotEveryBrokerReportedItsLoad() {
    // given
    final var scheduler = startedScheduler(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 10.0));
    loadSource.load = load(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 0.0), Set.of(OTHER));

    // when
    executor.runDue();

    // then
    assertThat(coordinator.triggered).isZero();
    assertThat(runs(ScheduledRebalanceOutcome.LOAD_UNKNOWN)).isOne();
  }

  @Test
  void shouldSkipWhenMembershipIsNotKnownYet() {
    // given
    final var scheduler = scheduler(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 10.0));
    scheduler.start();
    coordinator.status = UNBALANCED;
    loadSource.load = load(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 0.0), Set.of());

    // when
    executor.runDue();

    // then
    assertThat(coordinator.triggered).isZero();
    assertThat(runs(ScheduledRebalanceOutcome.LOAD_UNKNOWN)).isOne();
  }

  @Test
  void shouldSkipWhenBalancedByTheTimeItIsDue() {
    // given
    final var scheduler = startedScheduler(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 10.0));
    loadSource.load = load(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 0.0), Set.of());
    loadSource.duringWindow = () -> coordinator.status = RebalanceStatus.idle();

    // when
    executor.runDue();

    // then
    assertThat(coordinator.triggered).isZero();
    assertThat(runs(ScheduledRebalanceOutcome.ALREADY_BALANCED)).isOne();
  }

  @Test
  void shouldStartWhenUnbalancedByTheTimeItIsDue() {
    // given
    final var scheduler = startedScheduler(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 10.0));
    coordinator.status = RebalanceStatus.idle();
    loadSource.load = load(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 0.0), Set.of());
    loadSource.duringWindow = () -> coordinator.status = UNBALANCED;

    // when
    executor.runDue();

    // then
    assertThat(coordinator.triggered).isOne();
    assertThat(runs(ScheduledRebalanceOutcome.STARTED)).isOne();
  }

  @Test
  void shouldNotPublishLoadThatNotEveryBrokerReported() {
    // given
    final var scheduler = startedScheduler(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 10.0));
    loadSource.load = load(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 0.0), Set.of(OTHER));

    // when
    executor.runDue();

    // then
    assertThat(registry.find(ClusterRebalanceMetricsDoc.SCHEDULED_LOAD.getName()).gauges())
        .isEmpty();
  }

  @Test
  void shouldPublishTheMeasuredLoad() {
    // given
    final var scheduler = startedScheduler(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 10.0));
    loadSource.load = load(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 11.0), Set.of());

    // when
    executor.runDue();

    // then
    assertThat(
            registry
                .get(ClusterRebalanceMetricsDoc.SCHEDULED_LOAD.getName())
                .tag("measure", LoadMeasure.ROOT_PROCESS_INSTANCES.name())
                .gauge()
                .value())
        .isEqualTo(11.0);
  }

  @Test
  void shouldStartWithoutMeasuringWhenNoThresholdIsConfigured() {
    // given
    final var scheduler = startedScheduler(Map.of());

    // when
    executor.runDue();

    // then
    assertThat(coordinator.triggered).isOne();
    assertThat(loadSource.askedMembers).isNull();
  }

  @Test
  void shouldSkipQuietlyWhenNotTheCoordinator() {
    // given
    final var scheduler = startedScheduler(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 10.0));
    coordinator.statusError = new NotCoordinatorException("not the coordinator");

    // when
    executor.runDue();

    // then
    assertThat(coordinator.triggered).isZero();
    assertThat(loadSource.askedMembers).isNull();
    assertThat(registry.find(ClusterRebalanceMetricsDoc.SCHEDULED_RUNS.getName()).counters())
        .isEmpty();
  }

  @Test
  void shouldScheduleTheNextRunAfterEachDueRun() {
    // given
    final var scheduler = startedScheduler(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 10.0));
    loadSource.load = load(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 11.0), Set.of());

    // when
    clock.advance(INTERVAL);
    executor.runDue();

    // then
    assertThat(executor.delays).containsExactly(INTERVAL.minus(WINDOW), INTERVAL.minus(WINDOW));
  }

  @Test
  void shouldStopSchedulingOnceClosed() {
    // given
    final var scheduler = startedScheduler(Map.of(LoadMeasure.ROOT_PROCESS_INSTANCES, 10.0));

    // when
    scheduler.close();

    // then
    assertThat(executor.pending).isEmpty();
  }

  private RebalanceScheduler startedScheduler(final Map<LoadMeasure, Double> maxRates) {
    return startedScheduler(maxRates, WINDOW);
  }

  private RebalanceScheduler startedScheduler(
      final Map<LoadMeasure, Double> maxRates, final Duration window) {
    final var scheduler = scheduler(maxRates, window);
    scheduler.onClusterConfigurationUpdated(clusterOf(COORDINATOR, OTHER));
    scheduler.start();
    coordinator.status = UNBALANCED;
    return scheduler;
  }

  private RebalanceScheduler scheduler(final Map<LoadMeasure, Double> maxRates) {
    return scheduler(maxRates, WINDOW);
  }

  private RebalanceScheduler scheduler(
      final Map<LoadMeasure, Double> maxRates, final Duration window) {
    metrics.startCoordinating();
    return new RebalanceScheduler(
        executor,
        Schedule.parseSchedule(INTERVAL.toString()),
        coordinator,
        loadSource,
        maxRates,
        window,
        clock,
        metrics);
  }

  private double runs(final ScheduledRebalanceOutcome outcome) {
    return registry
        .get(ClusterRebalanceMetricsDoc.SCHEDULED_RUNS.getName())
        .tag("result", outcome.name())
        .counter()
        .count();
  }

  private static ClusterConfiguration clusterOf(final MemberId... members) {
    var configuration = ClusterConfiguration.init();
    for (final var member : members) {
      configuration = configuration.addMember(member, MemberState.initializeAsActive(Map.of()));
    }
    return configuration;
  }

  private static ClusterLoad load(
      final Map<LoadMeasure, Double> rates, final Set<MemberId> unaccounted) {
    return new ClusterLoad(rates, unaccounted);
  }

  private static final class RecordingExecutor extends TestConcurrencyControl {
    private final List<Duration> delays = new ArrayList<>();
    private final List<Runnable> pending = new ArrayList<>();

    @Override
    public ScheduledTimer schedule(final long delayMs, final Runnable runnable) {
      delays.add(Duration.ofMillis(delayMs));
      pending.add(runnable);
      return () -> pending.remove(runnable);
    }

    private void runDue() {
      assertThat(pending).hasSize(1);
      pending.removeFirst().run();
    }
  }

  private final class FakeCoordinator implements RebalanceApi {
    private RebalanceStatus status = RebalanceStatus.idle();
    private RuntimeException statusError;
    private int triggered;

    @Override
    public ActorFuture<RebalanceStatus> triggerRebalance(final TriggerRebalanceRequest request) {
      triggered++;
      return executor.completedFuture(status);
    }

    @Override
    public ActorFuture<RebalanceStatus> getRebalanceStatus() {
      return statusError != null
          ? executor.<RebalanceStatus>failedFuture(statusError)
          : executor.completedFuture(status);
    }

    @Override
    public ActorFuture<CancelRebalanceResponse> cancelRebalance() {
      return executor.completedFuture(new CancelRebalanceResponse(false));
    }
  }

  private final class FakeLoadSource implements ClusterLoadSource {
    private ClusterLoad load = load(Map.of(), Set.of());
    private Collection<MemberId> askedMembers;
    private Runnable duringWindow = () -> {};
    private boolean completes = true;

    @Override
    public ActorFuture<ClusterLoad> collect(
        final Collection<MemberId> members, final Duration window) {
      askedMembers = List.copyOf(members);
      duringWindow.run();
      return completes ? executor.completedFuture(load) : executor.createFuture();
    }
  }
}
