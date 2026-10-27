/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft;

import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.zeebe.ZeebeLogAppender.AppendListener;
import java.util.concurrent.CompletableFuture;

/**
 * Tracks the result of an append operation. Provides two futures, {@link #write()} and {@link
 * #commit()} that are completed when the entry is written and committed respectively.
 */
final class AppendResult implements AppendListener {
  private final CompletableFuture<Long> write = new CompletableFuture<>();
  private final CompletableFuture<Long> commit = new CompletableFuture<>();

  /**
   * @return a future that is completed with the entry index when it is committed. If the write or
   *     commit fails, the future is completed exceptionally.
   */
  CompletableFuture<Long> commit() {
    return commit;
  }

  /**
   * @return a future that is completed with the entry index when it is written. If the write fails,
   *     the future is completed exceptionally.
   */
  CompletableFuture<Long> write() {
    return write;
  }

  @Override
  public void onWrite(final IndexedRaftLogEntry indexed) {
    write.complete(indexed.index());
  }

  @Override
  public void onWriteError(final Throwable error) {
    write.completeExceptionally(error);
    // If write fails, the entry cannot be committed either.
    commit.completeExceptionally(error);
  }

  @Override
  public void onCommit(final long index, final long highestPosition) {
    commit.complete(index);
  }

  @Override
  public void onCommitError(final long index, final Throwable error) {
    commit.completeExceptionally(error);
  }
}
