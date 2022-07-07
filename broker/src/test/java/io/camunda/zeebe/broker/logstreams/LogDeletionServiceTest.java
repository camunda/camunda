/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.logstreams;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.ActorScheduler.ActorSchedulerBuilder;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogDeletionServiceTest {

  private PersistedSnapshotStore snapshotStore;
  private LogCompactor compactor;
  private LogDeletionService service;
  private ActorScheduler actorScheduler;

  @BeforeEach
  void before() {
    actorScheduler = new ActorSchedulerBuilder().build();
    actorScheduler.start();
    snapshotStore = mock(PersistedSnapshotStore.class);
    compactor = mock(LogCompactor.class);

    service = new LogDeletionService(1, 1, compactor, snapshotStore);
    actorScheduler.submitActor(service).join();
  }

  @AfterEach
  void after() throws Exception {
    service.close();
    actorScheduler.close();
  }

  @Test
  void shouldCompactOldestSnapshotIndex() {
    // given
    final Set<PersistedSnapshot> availableSnapshots = getAvailableSnapshotsWithIndices(10, 5, 11);
    final var actorFuture = CompletableActorFuture.completed(availableSnapshots);
    when(snapshotStore.getAvailableSnapshots()).thenReturn(actorFuture);

    // when
    service.onNewSnapshot(mock(PersistedSnapshot.class));

    // then
    verify(compactor, timeout(1000).times(1)).compactLog(5);
  }

  private Set<PersistedSnapshot> getAvailableSnapshotsWithIndices(final long... indices) {
    final var snapshots = new HashSet<PersistedSnapshot>();
    for (final long index : indices) {
      final PersistedSnapshot snapshot = mock(PersistedSnapshot.class);
      when(snapshot.getCompactionBound()).thenReturn(index);
      snapshots.add(snapshot);
    }
    return snapshots;
  }
}
