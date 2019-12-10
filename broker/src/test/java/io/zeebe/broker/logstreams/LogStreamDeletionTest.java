/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.Snapshot;
import io.zeebe.logstreams.state.SnapshotStorage;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class LogStreamDeletionTest {
  private static final long POSITION_TO_DELETE = 6L;

  private final ActorSchedulerRule actorScheduler = new ActorSchedulerRule();
  @Rule public final RuleChain chain = RuleChain.outerRule(actorScheduler);

  private LogStream mockLogStream;
  private StatePositionSupplier mockPositionSupplier;
  private LogStreamDeletionService deletionService;

  @Before
  public void setup() {
    mockLogStream = mock(LogStream.class);
    mockPositionSupplier = mock(StatePositionSupplier.class);
    final SnapshotStorage mockSnapshotStorage = mock(SnapshotStorage.class);

    deletionService =
        new LogStreamDeletionService(mockLogStream, mockSnapshotStorage, mockPositionSupplier);
    actorScheduler.submitActor(deletionService).join();
  }

  @Test
  public void shouldDeleteToLowestPosition() {
    // given
    final var snapshot = mockSnapshot(POSITION_TO_DELETE + 5);
    when(mockPositionSupplier.getLowestPosition(any())).thenReturn(POSITION_TO_DELETE);

    // when
    actorScheduler
        .submitActor(
            new Actor() {
              @Override
              protected void onActorStarted() {
                deletionService.onSnapshotsDeleted(snapshot);
              }
            })
        .join();

    // then
    verify(mockLogStream, timeout(Duration.ofSeconds(1))).delete(POSITION_TO_DELETE);
  }

  @Test
  public void shouldNotDeleteOnNegativePosition() {
    // given
    final var snapshot = mockSnapshot(-1);
    when(mockPositionSupplier.getLowestPosition(any())).thenReturn(Long.MAX_VALUE);

    // when
    actorScheduler
        .submitActor(
            new Actor() {
              @Override
              protected void onActorStarted() {
                deletionService.onSnapshotsDeleted(snapshot);
              }
            })
        .join();

    // then
    verify(mockLogStream, never()).delete(POSITION_TO_DELETE);
  }

  private Snapshot mockSnapshot(final long position) {
    final var snapshot = mock(Snapshot.class);
    when(snapshot.getPosition()).thenReturn(position);
    return snapshot;
  }
}
