/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
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
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.Answers;

public class SnapshotMigrationTransitionStepTest {
  final TestPartitionTransitionContext transitionContext = new TestPartitionTransitionContext();
  private final SnapshotAfterMigrationTransitionStep step =
      new SnapshotAfterMigrationTransitionStep();
  private final AsyncSnapshotDirector snapshotDirector =
      mock(AsyncSnapshotDirector.class, Answers.RETURNS_DEEP_STUBS);
  private final TestConcurrencyControl concurrencyControl = new TestConcurrencyControl(true);

  @BeforeEach
  void setup() {
    transitionContext.setSnapshotDirector(snapshotDirector);
    transitionContext.setConcurrencyControl(concurrencyControl);

    when(snapshotDirector.componentName()).thenReturn(AsyncSnapshotDirector.actorName(1));
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

    checkMetricsAre(HealthStatus.UNHEALTHY);
    concurrencyControl.runAll();

    checkMetricsAre(HealthStatus.UNHEALTHY);

    // then
    // when
    step.prepareTransition(transitionContext, 0, Role.INACTIVE);

    // then
    assertThat(transitionContext.getPartitionStartupMeterRegistry().getMeters()).isEmpty();
    assertThat(concurrencyControl.scheduledTasks()).isZero();
    verify(snapshotDirector.migrationSnapshotListener())
        .onHealthReport(argThat(HealthReport::isDead));
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
    transitionContext.markMigrationsDone();
    when(snapshotDirector.forceSnapshot())
        .thenReturn(CompletableActorFuture.completed(mock(PersistedSnapshot.class)));

    // when
    step.prepareTransition(transitionContext, 0, role).join();
    step.transitionTo(transitionContext, 0, role).join();

    concurrencyControl.runAll();
    verify(snapshotDirector, times(1)).forceSnapshot();
    assertThat(step.isSnapshotTaken()).isTrue();
    verify(snapshotDirector.migrationSnapshotListener())
        .onHealthReport(argThat(HealthReport::isHealthy));

    step.prepareTransition(transitionContext, 0, Role.LEADER).join();
    step.transitionTo(transitionContext, 0, Role.LEADER).join();
    concurrencyControl.runAll();

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
    // given
    transitionContext.markMigrationsDone();
    when(snapshotDirector.forceSnapshot())
        .thenReturn(CompletableActorFuture.completed(mock(PersistedSnapshot.class)));

    // when
    step.prepareTransition(transitionContext, 0, role).join();
    step.transitionTo(transitionContext, 0, role).join();

    assertThat(concurrencyControl.scheduledTasks()).isNotZero();
    concurrencyControl.runAll();

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
    transitionContext.markMigrationsDone();
    when(snapshotDirector.forceSnapshot()).thenReturn(CompletableActorFuture.completed(null));

    // when
    step.prepareTransition(transitionContext, 0, role).join();
    step.transitionTo(transitionContext, 0, role).join();

    assertThat(concurrencyControl.scheduledTasks()).isNotZero();
    concurrencyControl.runAll();
    concurrencyControl.runAll();
    // then
    // TestConcurrencyControl runs immediately when scheduled instead of waiting
    Awaitility.await("until snapshot is retried 2 times")
        .untilAsserted(() -> verify(snapshotDirector, atLeast(2)).forceSnapshot());
    verify(snapshotDirector.migrationSnapshotListener())
        .onHealthReport(argThat(HealthReport::isUnhealthy));

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
    transitionContext.markMigrationsDone();
    when(snapshotDirector.forceSnapshot()).thenReturn(CompletableActorFuture.completed(null));

    // when
    step.prepareTransition(transitionContext, 0, role).join();
    step.transitionTo(transitionContext, 0, role).join();

    concurrencyControl.runAll();
    // then
    verify(snapshotDirector, atLeastOnce()).forceSnapshot();
    assertThat(concurrencyControl.scheduledTasks()).isNotZero();

    // when
    step.prepareTransition(transitionContext, 0, Role.FOLLOWER);

    assertThat(concurrencyControl.scheduledTasks()).isZero();
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      mode = Mode.EXCLUDE,
      names = {"INACTIVE"})
  public void shouldRescheduleASnapshotAfterANewTransition(final Role role) {
    // given
    transitionContext.markMigrationsDone();
    when(snapshotDirector.forceSnapshot()).thenReturn(CompletableActorFuture.completed(null));

    // when
    step.prepareTransition(transitionContext, 0, role).join();
    step.transitionTo(transitionContext, 0, role).join();

    concurrencyControl.runAll();
    verify(snapshotDirector, atLeastOnce()).forceSnapshot();

    step.prepareTransition(transitionContext, 0, Role.LEADER);
    step.transitionTo(transitionContext, 0, Role.LEADER);

    concurrencyControl.runAll();
    // then
    verify(snapshotDirector, atLeast(2)).forceSnapshot();
    assertThat(concurrencyControl.scheduledTasks()).isNotZero();
  }

  public void checkMetricsAre(final HealthStatus expected) {
    final var meters = transitionContext.getPartitionStartupMeterRegistry().getMeters();
    final var expectedTags =
        Tags.of(
                "id",
                MigrationSnapshotDirector.COMPONENT_NAME,
                "partition",
                String.valueOf(transitionContext.getPartitionId()),
                "path",
                String.format(
                    "%s/%s",
                    AsyncSnapshotDirector.actorName(1), MigrationSnapshotDirector.COMPONENT_NAME))
            .stream()
            .toArray(Tag[]::new);
    assertThat(meters)
        .singleElement()
        .satisfies(
            m -> {
              assertThat(m.getId().getTags()).contains(expectedTags);
              assertThat(m.measure().iterator().next().getValue())
                  .isEqualTo(HealthTreeMetrics.statusValue(expected));
            });
  }
}
