/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.atomix.raft.RaftCommittedEntryListener;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;

public class CommitAwaiter implements RaftCommittedEntryListener {

  // TODO: This is buggy if we want to allow multiple commitAwaiters to wait on the same
  // commitPosition. This is ok for the prototype.
  final ConcurrentSkipListMap<Long, CompletableFuture<Void>> waiters =
      new ConcurrentSkipListMap<>();
  volatile long commitPosition;

  public CompletableFuture<Void> waitForCommitPosition(final long commitPosition) {
    final var f = new CompletableFuture<Void>();
    waiters.put(commitPosition, f);
    if (this.commitPosition >= commitPosition) {
      f.complete(null);
      waiters.remove(commitPosition);
    }
    return f;
  }

  @Override
  public void onCommit(final IndexedRaftLogEntry indexedRaftLogEntry) {
    if (indexedRaftLogEntry.isApplicationEntry()) {
      commitPosition = indexedRaftLogEntry.getApplicationEntry().highestPosition();
      final var toNotify = waiters.headMap(commitPosition, true);
      toNotify.forEach(
          (c, waiter) -> {
            waiter.complete(null);
          });
      toNotify.clear();
    }
  }
}
