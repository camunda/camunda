/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.monitoring.HealthTreeMetrics;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.impl.MigrationSnapshotDirector;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.health.CriticalComponentsHealthMonitor;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.agrona.LangUtil;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.Answers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapshotMigrationTransitionStepTest {
  @AutoClose private static ActorScheduler scheduler = ActorScheduler.newActorScheduler().build();

  private static final Logger LOG =
      LoggerFactory.getLogger(SnapshotMigrationTransitionStepTest.class);

  final TestPartitionTransitionContext transitionContext = new TestPartitionTransitionContext();
  @AutoClose final ActorWithControl actor = new ActorWithControl();
  private final SnapshotAfterMigrationTransitionStep step =
      new SnapshotAfterMigrationTransitionStep();
  private final AsyncSnapshotDirector snapshotDirector =
      mock(AsyncSnapshotDirector.class, Answers.RETURNS_DEEP_STUBS);

  private final io.camunda.zeebe.util.health.HealthMonitor healthMonitor =
      new CriticalComponentsHealthMonitor(
          "health-monitor",
          actor.control(),
          transitionContext.getComponentTreeListener(),
          Optional.empty(),
          LOG,
          Duration.ofMillis(100));

  private final TestConcurrencyControl concurrencyControl = new TestConcurrencyControl(true);

  @BeforeAll
  static void setupAll() {
    scheduler.start();
  }

  @BeforeEach
  void setup() {
    actor.setMonitor(healthMonitor);
    scheduler.submitActor(actor).join();
    transitionContext.setConcurrencyControl(actor);
    transitionContext.setSnapshotDirector(snapshotDirector);
    transitionContext.setConcurrencyControl(concurrencyControl);
    transitionContext.setComponentHealthMonitor(healthMonitor);
  }

  @AfterEach
  void tearDown() {
    step.prepareTransition(transitionContext, 0, Role.INACTIVE);
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      mode = Mode.EXCLUDE,
      names = {"INACTIVE"})
  public void shouldRegisterTheSnapshotMigrationDirectorToHealthTreeMetrics(final Role role) {
    // given
    transitionContext.markMigrationsDone();

    // when
    step.prepareTransition(transitionContext, 0, role).join();
    step.transitionTo(transitionContext, 0, role).join();
    // then
    checkMetricsAre(HealthStatus.UNHEALTHY);
    // when
    when(snapshotDirector.forceSnapshot())
        .thenReturn(CompletableActorFuture.completed(mock(PersistedSnapshot.class)));
    concurrencyControl.runAll();
    // then
    checkMetricsAre(HealthStatus.HEALTHY);

    // then
    // when
    step.prepareTransition(transitionContext, 0, Role.INACTIVE);

    // then
    Awaitility.await("until metric is unregistered")
        .untilAsserted(
            () ->
                assertThat(transitionContext.getPartitionStartupMeterRegistry().getMeters())
                    .singleElement()
                    .satisfies(m -> m.getId().getTag("id").equals("health-monitor")));
    assertThat(concurrencyControl.scheduledTasks()).isZero();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      mode = Mode.EXCLUDE,
      names = {"INACTIVE"})
  public void shouldNotScheduleASnapshotWhenNoMigrationWasPerformed(final Role role) {
    // when
    step.prepareTransition(transitionContext, 0, role).join();
    step.transitionTo(transitionContext, 0, role).join();

    assertThat(concurrencyControl.scheduledTasks()).isZero();
    verify(snapshotDirector, times(0)).forceSnapshot();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      mode = Mode.EXCLUDE,
      names = {"INACTIVE"})
  public void shouldNotScheduleASnapshotAfterItsDoneInAPreviousTransition(final Role role) {
    // given
    setupCommonCase(role, CompletableActorFuture.completed(mock(PersistedSnapshot.class)));
    assertThat(step.isSnapshotTaken()).isTrue();
    Awaitility.await("until report becomes true")
        .untilAsserted(
            () -> assertThat(healthMonitor.getHealthReport()).satisfies(HealthReport::isHealthy));

    // when
    step.prepareTransition(transitionContext, 0, Role.LEADER).join();
    step.transitionTo(transitionContext, 0, Role.LEADER).join();

    // then
    // no more interactions
    assertThat(concurrencyControl.scheduledTasks()).isZero();
    verify(snapshotDirector, times(1)).forceSnapshot();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      mode = Mode.EXCLUDE,
      names = {"INACTIVE"})
  public void shouldScheduleASnapshotImmediately(final Role role) {
    setupCommonCase(role, CompletableActorFuture.completed(mock(PersistedSnapshot.class)));

    // then
    verify(snapshotDirector).forceSnapshot();

    assertThat(step.isSnapshotTaken()).isTrue();
    assertThat(concurrencyControl.scheduledTasks()).isZero();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      mode = Mode.EXCLUDE,
      names = {"INACTIVE"})
  public void shouldRetryUntilSnapshotDirectorReturnsASnapshot(final Role role) {
    // given
    setupCommonCase(role, CompletableActorFuture.completed(null));

    concurrencyControl.runAll();
    // then
    // TestConcurrencyControl runs immediately when scheduled instead of waiting
    Awaitility.await("until snapshot is retried 2 times")
        .untilAsserted(() -> verify(snapshotDirector, atLeast(2)).forceSnapshot());
    Awaitility.await("until report is unhealthy")
        .untilAsserted(
            () -> assertThat(healthMonitor.getHealthReport()).satisfies(HealthReport::isUnhealthy));

    // when
    when(snapshotDirector.forceSnapshot())
        .thenReturn(CompletableActorFuture.completed(mock(PersistedSnapshot.class)));
    concurrencyControl.runAll();

    // then
    verify(snapshotDirector, atLeast(3)).forceSnapshot();

    Awaitility.await("until snapshot is taken")
        .untilAsserted(() -> assertThat(step.isSnapshotTaken()).isTrue());
    assertThat(concurrencyControl.scheduledTasks()).isZero();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      mode = Mode.EXCLUDE,
      names = {"INACTIVE"})
  public void shouldCancelScheduleTimerWhenATransitionIsDone(final Role role) {
    // given
    setupCommonCase(role, CompletableActorFuture.completed(null));
    assertThat(concurrencyControl.scheduledTasks()).isNotZero();

    // when
    step.prepareTransition(transitionContext, 0, Role.INACTIVE);

    // then
    assertThat(concurrencyControl.scheduledTasks()).isZero();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      mode = Mode.EXCLUDE,
      names = {"INACTIVE"})
  public void shouldRescheduleASnapshotAfterANewTransition(final Role role) {
    // given
    setupCommonCase(role, CompletableActorFuture.completed(null));

    // when
    step.prepareTransition(transitionContext, 0, Role.LEADER);
    step.transitionTo(transitionContext, 0, Role.LEADER);

    concurrencyControl.runAll();
    // then
    verify(snapshotDirector, atLeast(2)).forceSnapshot();
    assertThat(concurrencyControl.scheduledTasks()).isNotZero();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      mode = Mode.EXCLUDE,
      names = {"INACTIVE"})
  public void shouldRetryIOExceptions(final Role role) {
    // given
    setupCommonCase(
        role, CompletableActorFuture.completedExceptionally(new IOException("expected")));
    // when
    concurrencyControl.runAll();
    // then
    verify(snapshotDirector, atLeast(2)).forceSnapshot();
    assertThat(concurrencyControl.scheduledTasks()).isNotZero();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      mode = Mode.EXCLUDE,
      names = {"INACTIVE"})
  public void shouldRetryIOExceptionsRaisedInAFuture(final Role role) {
    // given
    setupCommonCase(
        role,
        CompletableActorFuture.completed(null)
            .thenApply(
                ignored -> {
                  try {
                    throw new IOException("expected");
                  } catch (final Exception e) {
                    LangUtil.rethrowUnchecked(e);
                  }
                  return mock(PersistedSnapshot.class);
                },
                concurrencyControl));
    // when
    concurrencyControl.runAll();
    // then
    verify(snapshotDirector, atLeast(2)).forceSnapshot();
    assertThat(concurrencyControl.scheduledTasks()).isNotZero();
  }

  private void setupCommonCase(
      final Role role, final ActorFuture<PersistedSnapshot> snapshotResult) {
    // given
    transitionContext.markMigrationsDone();
    when(snapshotDirector.forceSnapshot()).thenReturn(snapshotResult);

    // when
    step.prepareTransition(transitionContext, 0, role).join();
    step.transitionTo(transitionContext, 0, role).join();
    assertThat(concurrencyControl.scheduledTasks()).isNotZero();

    concurrencyControl.runAll();
    verify(snapshotDirector, atLeastOnce()).forceSnapshot();
  }

  public void checkMetricsAre(final HealthStatus expected) {
    Awaitility.await("until metrics are published")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              final var meters =
                  transitionContext.getPartitionTransitionMeterRegistry().getMeters();
              System.out.println(meters.stream().map(m -> m.getId().getTags()).toList());
              final var expectedTags =
                  Tags.of(
                          "id",
                          MigrationSnapshotDirector.COMPONENT_NAME,
                          "partition",
                          String.valueOf(transitionContext.getPartitionId()),
                          "path",
                          String.format(
                              "%s/%s",
                              healthMonitor.componentName(),
                              MigrationSnapshotDirector.COMPONENT_NAME))
                      .stream()
                      .toArray(Tag[]::new);
              assertThat(
                      meters.stream()
                          .filter(
                              m ->
                                  m.getId()
                                      .getTag("id")
                                      .equals(MigrationSnapshotDirector.COMPONENT_NAME))
                          .toList())
                  .singleElement()
                  .satisfies(
                      m -> {
                        assertThat(m.getId().getTags()).contains(expectedTags);
                        assertThat(m.measure().iterator().next().getValue())
                            .isEqualTo(HealthTreeMetrics.statusValue(expected));
                      });
            });
  }

  public static class ActorWithControl extends Actor {

    private HealthMonitor monitor;

    public void setMonitor(final HealthMonitor monitor) {
      this.monitor = monitor;
    }

    @Override
    protected void onActorStarted() {
      monitor.startMonitoring();
    }

    public ActorControl control() {
      return actor;
    }
  }
}
