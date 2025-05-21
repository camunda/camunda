/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class SnapshotMigrationTransitionStepTest {
  final TestPartitionTransitionContext transitionContext = new TestPartitionTransitionContext();
  private final Duration duration = Duration.ofMillis(100);
  private final SnapshotAfterMigrationTransitionStep step =
      new SnapshotAfterMigrationTransitionStep(duration);
  private final AsyncSnapshotDirector snapshotDirector = mock(AsyncSnapshotDirector.class);
  private final ConcurrencyControl concurrencyControl = mock(ConcurrencyControl.class);

  @BeforeEach
  void setup() {
    transitionContext.setSnapshotDirector(snapshotDirector);
    transitionContext.setConcurrencyControl(concurrencyControl);
    final var runnable = ArgumentCaptor.captor(Runnable.class);
    when(concurrencyControl.schedule(any(), any())).thenReturn(() -> {});
  }

  @Test
  public void shouldScheduleASnapshotImmediately() {
    // given
    transitionContext.setMigrationsPerformed(true);

    // when
    step.prepareTransition(transitionContext, 0, Role.CANDIDATE);
    step.transitionTo(transitionContext, 0, Role.CANDIDATE);

    // then
    verify(concurrencyControl).schedule(eq(duration), any());
    verify(snapshotDirector).forceSnapshot();
  }
}
