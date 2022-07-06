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
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotChunkReader;
import io.camunda.zeebe.snapshots.SnapshotReservation;
import java.nio.file.Path;
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
    final Set<PersistedSnapshot> availableSnapshots =
        Set.of(
            new TestPersistedSnapshot(10),
            new TestPersistedSnapshot(5),
            new TestPersistedSnapshot(11));
    final var actorFuture = CompletableActorFuture.completed(availableSnapshots);
    when(snapshotStore.getAvailableSnapshots()).thenReturn(actorFuture);

    // when
    service.onNewSnapshot(new TestPersistedSnapshot(11));

    // then
    verify(compactor, timeout(1000).times(1)).compactLog(5);
  }

  static class TestPersistedSnapshot implements PersistedSnapshot {

    private final long compactionBound;

    TestPersistedSnapshot(final long compactionBound) {
      this.compactionBound = compactionBound;
    }

    @Override
    public int version() {
      return 0;
    }

    @Override
    public long getIndex() {
      return 0;
    }

    @Override
    public long getTerm() {
      return 0;
    }

    @Override
    public SnapshotChunkReader newChunkReader() {
      return null;
    }

    @Override
    public Path getPath() {
      return null;
    }

    @Override
    public long getCompactionBound() {
      return compactionBound;
    }

    @Override
    public String getId() {
      return null;
    }

    @Override
    public long getChecksum() {
      return 0;
    }

    @Override
    public ActorFuture<SnapshotReservation> reserve() {
      return null;
    }

    @Override
    public void close() {}
  }
}
