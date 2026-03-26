/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.publish;

import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.List;

/** Tracks a single raft entry that contains multiple batches (batch of batches). */
final class PendingAppend implements AppendListener {

  private final List<ResolvedEntry> entries;
  private final CompletableActorFuture<Void> commitFuture = new CompletableActorFuture<>();

  PendingAppend(final List<ResolvedEntry> entries) {
    this.entries = entries;
  }

  @Override
  public void onCommit(final long index, final long highestPosition) {
    commitFuture.complete(null);
  }

  List<ResolvedEntry> getEntries() {
    return entries;
  }

  CompletableActorFuture<Void> getCommitFuture() {
    return commitFuture;
  }

  /** An inflight entry with its assigned positions. Computed once during flush. */
  record ResolvedEntry(long requestId, long firstPosition, long lastPosition) {}
}
