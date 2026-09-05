/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DependencyChangePlan;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.ConfigurationChangeInProgressException;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.NotCoordinatorException;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.RebalanceInProgressException;
import io.camunda.zeebe.scheduler.ActorTask;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

final class RebalanceCoordinatorTest {

  private static final MemberId LOWEST_ID_MEMBER = MemberId.from("1");
  private static final MemberId OTHER_MEMBER = MemberId.from("2");
  private static final Instant FIXED_INSTANT = Instant.parse("2024-01-01T00:00:00Z");
  private static final PartitionRebalance PLAN =
      PartitionRebalance.pending("default", 1, LOWEST_ID_MEMBER, OTHER_MEMBER);

  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final AtomicLong nextRebalanceId = new AtomicLong(100);
  private final Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
  private final PartitionBalancePlanner balancePlanner =
      new PartitionBalancePlanner(physicalTenantId -> partitionId -> Optional.empty());

  @Test
  void shouldRefuseRequestsBeforeAnyConfigurationArrives() {
    // given
    final var coordinator = startCoordinator(LOWEST_ID_MEMBER, RebalanceRunner.none());

    // when
    final var status = coordinator.getRebalanceStatus();

    // then
    assertThatThrownBy(status::join).hasCauseInstanceOf(NotCoordinatorException.class);
  }

  @Test
  void shouldRefuseRequestsOnAMemberThatIsNotTheLowestId() {
    // given
    final var coordinator = startCoordinator(OTHER_MEMBER, RebalanceRunner.none());
    configurationWithBothMembers(coordinator);

    // when
    final var triggered =
        coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // then
    assertThatThrownBy(triggered::join).hasCauseInstanceOf(NotCoordinatorException.class);
  }

  @Test
  void shouldReportTheRebalanceItStarted() {
    // given
    final var coordinator = coordinatingWith(new BlockedRunner());
    final var overrides = new RebalanceOverrides(4096L, Duration.ofSeconds(30), 5, null);

    // when
    final var triggered =
        coordinator.triggerRebalance(new TriggerRebalanceRequest(overrides, false));

    // then
    final var running = triggered.join().running();
    assertThat(running).isNotNull();
    assertThat(running.rebalanceId()).isEqualTo(100);
    assertThat(running.overrides()).isEqualTo(overrides);
    assertThat(running.dryRun()).isFalse();
    assertThat(running.cancelRequested()).isFalse();
    assertThat(triggered.join().lastCompleted()).isNull();
  }

  @Test
  void shouldRefuseASecondRebalanceWhileOneIsRunning() {
    // given
    final var coordinator = coordinatingWith(new BlockedRunner());
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    final var second =
        coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // then
    assertThatThrownBy(second::join)
        .hasCauseInstanceOf(RebalanceInProgressException.class)
        .hasMessageContaining("Rebalance 100 is already running");
  }

  @Test
  void shouldAcceptANewRebalanceOnceTheRunningOneFinished() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    runner.finish();
    final var second =
        coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // then
    final var status = second.join();
    assertThat(status.running()).isNotNull();
    assertThat(status.running().rebalanceId()).isEqualTo(101);
    assertThat(status.lastCompleted())
        .isEqualTo(
            new RebalanceStatus.Completed(
                100, RebalanceOutcome.COMPLETED, List.of(PLAN), FIXED_INSTANT, FIXED_INSTANT));
  }

  @Test
  void shouldReportWhereTheRebalanceHasGotToWithEachPartition() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    runner.rebalance.updatePartition(0, partition -> partition.transferred());

    // then
    assertThat(coordinator.getRebalanceStatus().join().running().partitions())
        .containsExactly(PLAN.transferred());
  }

  @Test
  void shouldAnswerADryRunWithThePlanItWouldHaveCarriedOut() {
    // given
    final var coordinator = coordinatingWith(new PlanningRunner());
    final var overrides = RebalanceOverrides.none();

    // when
    final var triggered =
        coordinator.triggerRebalance(new TriggerRebalanceRequest(overrides, true));

    // then
    final var status = triggered.join();
    final var running = status.running();
    assertThat(running).isNotNull();
    assertThat(running.rebalanceId()).isEqualTo(100);
    assertThat(running.dryRun()).isTrue();
    assertThat(running.cancelRequested()).isFalse();
    assertThat(running.partitions()).containsExactly(PLAN);
    assertThat(status.lastCompleted()).isNull();

    final var afterPlanning = coordinator.getRebalanceStatus().join();
    assertThat(afterPlanning.running()).isNull();
    assertThat(afterPlanning.lastCompleted()).isNull();
  }

  @Test
  void shouldNotRetainADryRunAsTheLastCompletedRebalance() {
    // given
    final var coordinator = coordinatingWith(new PlanningRunner());
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());
    final var realCompletion = coordinator.getRebalanceStatus().join().lastCompleted();

    // when
    final var dryRun =
        coordinator
            .triggerRebalance(new TriggerRebalanceRequest(RebalanceOverrides.none(), true))
            .join();

    // then
    assertThat(dryRun.running()).isNotNull();
    assertThat(dryRun.running().rebalanceId()).isEqualTo(101);
    assertThat(dryRun.running().dryRun()).isTrue();
    assertThat(dryRun.lastCompleted()).isEqualTo(realCompletion);

    final var afterDryRun = coordinator.getRebalanceStatus().join();
    assertThat(afterDryRun.running()).isNull();
    assertThat(afterDryRun.lastCompleted()).isEqualTo(realCompletion);
  }

  @Test
  void shouldFailTheTriggerFutureWhenDryRunPlanningFails() {
    // given
    final var failure = new RuntimeException("planning blew up");
    final var coordinator = coordinatingWith(new FailingRunner(failure));

    // when
    final var triggered =
        coordinator.triggerRebalance(new TriggerRebalanceRequest(RebalanceOverrides.none(), true));

    // then
    assertThatThrownBy(triggered::join).hasCause(failure);
    final var status = coordinator.getRebalanceStatus().join();
    assertThat(status.running()).isNull();
    assertThat(status.lastCompleted()).isNull();
  }

  @Test
  void shouldNotChangeElapsedTimerCountsForADryRun() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var coordinator =
        new RebalanceCoordinator(
            LOWEST_ID_MEMBER,
            executor,
            new PlanningRunner(),
            balancePlanner,
            nextRebalanceId::getAndIncrement,
            clock,
            new ClusterRebalanceMetrics(registry));
    configurationWithBothMembers(coordinator);
    final var countsBefore = elapsedTimerCounts(registry);

    // when
    coordinator
        .triggerRebalance(new TriggerRebalanceRequest(RebalanceOverrides.none(), true))
        .join();

    // then
    assertThat(elapsedTimerCounts(registry)).isEqualTo(countsBefore);
  }

  private static Map<RebalanceOutcome, Long> elapsedTimerCounts(
      final SimpleMeterRegistry registry) {
    final Map<RebalanceOutcome, Long> counts = new EnumMap<>(RebalanceOutcome.class);
    for (final var outcome : RebalanceOutcome.values()) {
      counts.put(
          outcome,
          registry
              .get("zeebe.cluster.rebalance.elapsed")
              .tag("result", outcome.name())
              .timer()
              .count());
    }
    return counts;
  }

  @Test
  void shouldRunARebalanceAgainstTheConfigurationItWasAdmittedUnder() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);

    // when
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // then
    assertThat(runner.rebalance.configuration().getMembers())
        .containsExactlyInAnyOrder(LOWEST_ID_MEMBER, OTHER_MEMBER);
  }

  @Test
  void shouldReportARebalanceThatFailedAsFailed() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    runner.fail(new RuntimeException("no leader answered"));
    final var status = coordinator.getRebalanceStatus();

    // then
    assertThat(status.join().running()).isNull();
    assertThat(status.join().lastCompleted())
        .isEqualTo(
            new RebalanceStatus.Completed(
                100, RebalanceOutcome.FAILED, List.of(PLAN), FIXED_INSTANT, FIXED_INSTANT));
  }

  @Test
  void shouldTellTheRunningRebalanceThatAPhysicalTenantStoppedRunning() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    configurationWithADisabledDefaultPhysicalTenant(coordinator);

    // then
    assertThat(
            runner.rebalance.isPhysicalTenantDisabled(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isTrue();
  }

  @Test
  void shouldReportARebalanceThatLeftADisabledPhysicalTenantAloneAsCompleted() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());
    runner.rebalance.updatePartition(
        0, partition -> partition.completed(PartitionRebalanceOutcome.PHYSICAL_TENANT_DISABLED));

    // when
    runner.finish();

    // then
    assertThat(coordinator.getRebalanceStatus().join().lastCompleted().outcome())
        .isEqualTo(RebalanceOutcome.COMPLETED);
  }

  @Test
  void shouldStopTheRunningRebalanceOnCancellation() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    final var cancelled = coordinator.cancelRebalance();

    // then
    assertThat(cancelled.join().wasRunning()).isTrue();
    assertThat(runner.rebalance.isCancelRequested()).isTrue();
  }

  @Test
  void shouldReportACancelledRebalanceAsCancelled() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());
    coordinator.cancelRebalance();

    // when
    runner.finish();
    final var status = coordinator.getRebalanceStatus();

    // then
    assertThat(status.join().lastCompleted())
        .isEqualTo(
            new RebalanceStatus.Completed(
                100, RebalanceOutcome.CANCELLED, List.of(PLAN), FIXED_INSTANT, FIXED_INSTANT));
  }

  @Test
  void shouldCancelARunThatIsStillInFlightWhenTheRequestArrives() {
    // given
    final var runner = new DeferredRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    final var cancelled = coordinator.cancelRebalance();
    assertThat(cancelled.join().wasRunning()).isTrue();

    runner.completeAndDeliver();

    // then
    final var status = coordinator.getRebalanceStatus();
    assertThat(status.join().lastCompleted())
        .isEqualTo(
            new RebalanceStatus.Completed(
                100, RebalanceOutcome.CANCELLED, List.of(), FIXED_INSTANT, FIXED_INSTANT));
  }

  @Test
  void shouldCancelARunThatCompletedButHasNotYetBeenReportedByFinish() {
    // given
    final var runner = new DeferredRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());
    runner.complete();

    // when
    final var cancelled = coordinator.cancelRebalance();

    // then
    assertThat(cancelled.join().wasRunning()).isTrue();

    runner.deliver();
    final var status = coordinator.getRebalanceStatus();
    assertThat(status.join().lastCompleted())
        .isEqualTo(
            new RebalanceStatus.Completed(
                100, RebalanceOutcome.CANCELLED, List.of(), FIXED_INSTANT, FIXED_INSTANT));
  }

  @Test
  void shouldNotCancelARunAbandonedDuringACoordinatorHandover() {
    // given
    final var runner = new DeferredRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    configurationWithANewLowestIdMember(coordinator);
    final var cancelled = coordinator.cancelRebalance();

    // then
    assertThatThrownBy(cancelled::join).hasCauseInstanceOf(NotCoordinatorException.class);
    assertThat(runner.rebalance.isAbandoned()).isTrue();
    assertThat(runner.rebalance.isCancelRequested())
        .as("abandonment, not cancellation, is why the run stopped")
        .isFalse();
  }

  @Test
  void shouldAbandonAnInFlightRebalanceOnShutdownWithoutRequestingCancellation() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    final var shutdown = coordinator.shutdown();

    // then
    assertThat(runner.rebalance.isAbandoned()).isTrue();
    assertThat(runner.rebalance.isCancelRequested()).isFalse();
    assertThat(shutdown.isDone()).isTrue();
  }

  @Test
  void shouldRejectFurtherWorkAfterShutdown() {
    // given
    final var coordinator = coordinatingWith(RebalanceRunner.none());

    // when
    coordinator.shutdown();
    final var triggered =
        coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // then
    assertThatThrownBy(triggered::join).hasCauseInstanceOf(NotCoordinatorException.class);
  }

  @Test
  void shouldReportNothingWasRunningWhenCancellingWhileIdle() {
    // given
    final var coordinator = coordinatingWith(RebalanceRunner.none());

    // when
    final var cancelled = coordinator.cancelRebalance();

    // then
    assertThat(cancelled.join().wasRunning()).isFalse();
  }

  @Test
  void shouldDiscardItsStateWhenItIsNoLongerTheCoordinator() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    configurationWithANewLowestIdMember(coordinator);

    // then
    final var status = coordinator.getRebalanceStatus();
    assertThatThrownBy(status::join).hasCauseInstanceOf(NotCoordinatorException.class);
  }

  @Test
  void shouldAbandonTheRebalanceInFlightWhenItIsNoLongerTheCoordinator() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    configurationWithANewLowestIdMember(coordinator);

    // then
    assertThat(runner.rebalance.isAbandoned()).isTrue();
  }

  @Test
  void shouldNotReportTheAbandonedRebalanceWhenItBecomesTheCoordinatorAgain() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());
    configurationWithANewLowestIdMember(coordinator);

    // when
    configurationWithBothMembers(coordinator);
    runner.finish();

    // then
    final var status = coordinator.getRebalanceStatus();
    assertThat(status.join()).isEqualTo(RebalanceStatus.idle());
  }

  @Test
  void shouldRecordDeterministicTimestampsUnderAFixedClock() {
    // given
    final var startedAt = Instant.parse("2024-01-01T00:00:00Z");
    final var finishedAt = Instant.parse("2024-01-01T00:00:05Z");
    final var stepClock = new StepClock(startedAt);
    final var runner = new BlockedRunner();
    final var coordinator =
        new RebalanceCoordinator(
            LOWEST_ID_MEMBER,
            executor,
            runner,
            balancePlanner,
            nextRebalanceId::getAndIncrement,
            stepClock,
            new ClusterRebalanceMetrics(new SimpleMeterRegistry()));
    coordinator.onClusterConfigurationUpdated(
        CurrentClusterConfiguration.fromLegacy(
            ClusterConfiguration.init()
                .addMember(LOWEST_ID_MEMBER, MemberState.initializeAsActive(Map.of()))
                .addMember(OTHER_MEMBER, MemberState.initializeAsActive(Map.of()))));
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    stepClock.advanceTo(finishedAt);
    runner.finish();

    // then
    final var status = coordinator.getRebalanceStatus();
    assertThat(status.join().lastCompleted())
        .isEqualTo(
            new RebalanceStatus.Completed(
                100, RebalanceOutcome.COMPLETED, List.of(PLAN), startedAt, finishedAt));
  }

  @Test
  void shouldRefuseADryRunWhileAConfigurationChangeIsPending() {
    // given
    final var runner = new CountingRunner();
    final var coordinator = startCoordinator(LOWEST_ID_MEMBER, runner);
    configurationWithAPendingChange(coordinator);

    // when
    final var triggered =
        coordinator.triggerRebalance(new TriggerRebalanceRequest(RebalanceOverrides.none(), true));

    // then
    assertThatThrownBy(triggered::join)
        .hasCauseInstanceOf(ConfigurationChangeInProgressException.class);
    assertThat(runner.invocations).isZero();
    final var status = coordinator.getRebalanceStatus().join();
    assertThat(status.running()).isNull();
    assertThat(status.lastCompleted()).isNull();
  }

  @Test
  void shouldRefuseANonDryRunWhileAConfigurationChangeIsPending() {
    // given
    final var runner = new CountingRunner();
    final var coordinator = startCoordinator(LOWEST_ID_MEMBER, runner);
    configurationWithAPendingChange(coordinator);

    // when
    final var triggered =
        coordinator.triggerRebalance(new TriggerRebalanceRequest(RebalanceOverrides.none(), false));

    // then
    assertThatThrownBy(triggered::join)
        .hasCauseInstanceOf(ConfigurationChangeInProgressException.class);
    assertThat(runner.invocations).isZero();
    final var status = coordinator.getRebalanceStatus().join();
    assertThat(status.running()).isNull();
    assertThat(status.lastCompleted()).isNull();
  }

  @Test
  void shouldAcceptARequestOnceTheConfigurationChangeHasCompleted() {
    // given
    final var runner = new CountingRunner();
    final var coordinator = startCoordinator(LOWEST_ID_MEMBER, runner);
    configurationWithAPendingChange(coordinator);

    // when
    configurationWithBothMembers(coordinator);
    final var triggered =
        coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // then
    assertThat(triggered.join().running().rebalanceId()).isEqualTo(100);
    assertThat(runner.invocations).isEqualTo(1);
  }

  private RebalanceCoordinator coordinatingWith(final RebalanceRunner runner) {
    final var coordinator = startCoordinator(LOWEST_ID_MEMBER, runner);
    configurationWithBothMembers(coordinator);
    return coordinator;
  }

  private RebalanceCoordinator startCoordinator(
      final MemberId localMemberId, final RebalanceRunner runner) {
    return new RebalanceCoordinator(
        localMemberId,
        executor,
        runner,
        balancePlanner,
        nextRebalanceId::getAndIncrement,
        clock,
        new ClusterRebalanceMetrics(new SimpleMeterRegistry()));
  }

  private void configurationWithBothMembers(final RebalanceCoordinator coordinator) {
    coordinator.onClusterConfigurationUpdated(
        CurrentClusterConfiguration.fromLegacy(
            ClusterConfiguration.init()
                .addMember(LOWEST_ID_MEMBER, MemberState.initializeAsActive(Map.of()))
                .addMember(OTHER_MEMBER, MemberState.initializeAsActive(Map.of()))));
  }

  private void configurationWithADisabledDefaultPhysicalTenant(
      final RebalanceCoordinator coordinator) {
    coordinator.onClusterConfigurationUpdated(
        CurrentClusterConfiguration.fromLegacy(
                ClusterConfiguration.init()
                    .addMember(LOWEST_ID_MEMBER, MemberState.initializeAsActive(Map.of()))
                    .addMember(OTHER_MEMBER, MemberState.initializeAsActive(Map.of())))
            .updatePartitionGroupConfig(
                PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                PartitionGroupConfiguration::disable));
  }

  private void configurationWithAPendingChange(final RebalanceCoordinator coordinator) {
    final var members =
        ClusterConfiguration.init()
            .addMember(LOWEST_ID_MEMBER, MemberState.initializeAsActive(Map.of()))
            .addMember(OTHER_MEMBER, MemberState.initializeAsActive(Map.of()));
    coordinator.onClusterConfigurationUpdated(
        CurrentClusterConfiguration.fromLegacy(
            ClusterConfiguration.builder()
                .from(members)
                .pendingChanges(
                    Optional.of(
                        DependencyChangePlan.sequential(
                            members.version() + 1,
                            List.of(new PartitionLeaveOperation(OTHER_MEMBER, 1, 1)))))
                .build()));
  }

  private void configurationWithANewLowestIdMember(final RebalanceCoordinator coordinator) {
    coordinator.onClusterConfigurationUpdated(
        CurrentClusterConfiguration.fromLegacy(
            ClusterConfiguration.init()
                .addMember(MemberId.from("0"), MemberState.initializeAsActive(Map.of()))
                .addMember(LOWEST_ID_MEMBER, MemberState.initializeAsActive(Map.of()))));
  }

  private static final class CountingRunner implements RebalanceRunner {

    private int invocations;

    @Override
    public ActorFuture<Void> run(final RebalanceRun rebalance) {
      invocations++;
      rebalance.plan(List.of(PLAN));
      return CompletableActorFuture.completed();
    }
  }

  private static final class BlockedRunner implements RebalanceRunner {

    private final CompletableActorFuture<Void> completion = new CompletableActorFuture<>();
    private RebalanceRun rebalance;

    @Override
    public ActorFuture<Void> run(final RebalanceRun rebalance) {
      this.rebalance = rebalance;
      rebalance.plan(List.of(PLAN));
      return completion;
    }

    void finish() {
      completion.complete(null);
    }

    void fail(final Throwable error) {
      completion.completeExceptionally(error);
    }
  }

  private static final class DeferredRunner implements RebalanceRunner {

    private final DeferredActorFuture future = new DeferredActorFuture();
    private RebalanceRun rebalance;

    @Override
    public ActorFuture<Void> run(final RebalanceRun rebalance) {
      this.rebalance = rebalance;
      return future;
    }

    /** Marks the run finished, without yet notifying the coordinator. */
    void complete() {
      future.markDone();
    }

    /** Notifies the coordinator of the run's completion. */
    void deliver() {
      future.notifyListener();
    }

    void completeAndDeliver() {
      complete();
      deliver();
    }
  }

  private static final class DeferredActorFuture implements ActorFuture<Void> {

    private boolean done;
    private BiConsumer<Void, Throwable> listener;

    void markDone() {
      done = true;
    }

    void notifyListener() {
      listener.accept(null, null);
    }

    @Override
    public void complete(final Void value) {
      markDone();
    }

    @Override
    public void completeExceptionally(final String failure, final Throwable throwable) {
      completeExceptionally(throwable);
    }

    @Override
    public void completeExceptionally(final Throwable throwable) {
      markDone();
    }

    @Override
    public Void join() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Void join(final long timeout, final TimeUnit timeUnit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void block(final ActorTask onCompletion) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void onComplete(final BiConsumer<Void, Throwable> consumer) {
      listener = consumer;
    }

    @Override
    public void onComplete(final BiConsumer<Void, Throwable> consumer, final Executor executor) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCompletedExceptionally() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Throwable getException() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <U> ActorFuture<U> andThen(
        final Supplier<ActorFuture<U>> next, final Executor executor) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <U> ActorFuture<U> andThen(
        final Function<Void, ActorFuture<U>> next, final Executor executor) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <U> ActorFuture<U> andThen(
        final BiFunction<Void, Throwable, ActorFuture<U>> next, final Executor executor) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <U> ActorFuture<U> thenApply(final Function<Void, U> next, final Executor executor) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <U> ActorFuture<U> thenApply(final Function<Void, U> next) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCancelled() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDone() {
      return done;
    }

    @Override
    public Void get() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Void get(final long timeout, final TimeUnit unit) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class StepClock extends Clock {

    private Instant now;

    StepClock(final Instant now) {
      this.now = now;
    }

    void advanceTo(final Instant instant) {
      now = instant;
    }

    @Override
    public ZoneId getZone() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Clock withZone(final ZoneId zone) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private static final class PlanningRunner implements RebalanceRunner {

    @Override
    public ActorFuture<Void> run(final RebalanceRun rebalance) {
      rebalance.plan(List.of(PLAN));
      return CompletableActorFuture.completed();
    }
  }

  private static final class FailingRunner implements RebalanceRunner {

    private final RuntimeException failure;

    private FailingRunner(final RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public ActorFuture<Void> run(final RebalanceRun rebalance) {
      rebalance.plan(List.of(PLAN));
      throw failure;
    }
  }
}
