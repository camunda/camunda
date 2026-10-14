/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.atomix.raft.zeebe.ZeebeLogAppender.AppendListener;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Appends puts to the system partition's Raft log during a single leadership. Each put is a single
 * Raft application entry with its own position, one after the last position in the log, so
 * positions stay contiguous as the entry validator requires. It runs on the {@link
 * SystemPartitionRoleController}'s actor, which closes it when the leadership ends.
 */
final class SystemPartitionWriter implements SystemKeyValueWriter {
  private final RaftPartition raftPartition;
  private final ZeebeLogAppender appender;
  private final ConcurrencyControl concurrencyControl;
  // appends awaiting commit, by position
  private final Map<Long, CompletableActorFuture<Long>> pendingAppends = new HashMap<>();
  private final long initialLastPosition;
  private long nextPosition;
  // read by callers of put, which may run after the actor has closed and no longer runs jobs
  private volatile boolean closed;

  SystemPartitionWriter(
      final RaftPartition raftPartition,
      final ZeebeLogAppender appender,
      final ConcurrencyControl concurrencyControl) {
    this.raftPartition = raftPartition;
    this.appender = appender;
    this.concurrencyControl = concurrencyControl;
    initialLastPosition = lastPosition();
    nextPosition = initialLastPosition + 1;
  }

  /** Returns the position of the last entry in the log when this leadership started, or 0. */
  long initialLastPosition() {
    return initialLastPosition;
  }

  @Override
  public ActorFuture<Long> put(final SystemPartitionKeyType key, final BufferWriter value) {
    // copied, as Raft serializes the entry later and the caller may reuse the value
    final var entry =
        new SystemPartitionEntry().setKey(key.id()).setValue(BufferUtil.createCopy(value));
    final var result = new CompletableActorFuture<Long>();
    if (closed) {
      failClosed(result);
    } else {
      concurrencyControl.run(() -> append(entry, result));
    }
    return result;
  }

  /** Fails pending and later puts; called when the leadership ends. */
  void close() {
    closed = true;
    pendingAppends
        .values()
        .forEach(
            append ->
                append.completeExceptionally(
                    new IllegalStateException(
                        "Lost system partition leadership before the put was committed")));
    pendingAppends.clear();
  }

  private void append(final SystemPartitionEntry entry, final CompletableActorFuture<Long> result) {
    if (closed) {
      failClosed(result);
      return;
    }
    final var position = nextPosition++;
    pendingAppends.put(position, result);
    appender.appendEntry(position, position, entry, new Listener(position));
  }

  private static void failClosed(final CompletableActorFuture<Long> result) {
    result.completeExceptionally(
        new IllegalStateException(
            "Expected to put as the system partition leader, but the leadership has ended"));
  }

  /** Returns the position of the last entry in the log, committed or not, or 0 if none. */
  private long lastPosition() {
    try (final var reader = raftPartition.getServer().openUncommittedReader()) {
      reader.seekToAsqn(Long.MAX_VALUE);
      long lastPosition = 0;
      while (reader.hasNext()) {
        final var entry = reader.next();
        if (entry.isApplicationEntry()) {
          lastPosition = entry.getApplicationEntry().highestPosition();
        }
      }
      return lastPosition;
    }
  }

  private final class Listener implements AppendListener {
    private final long position;

    private Listener(final long position) {
      this.position = position;
    }

    @Override
    public void onWriteError(final Throwable error) {
      concurrencyControl.run(
          () -> {
            final var append = pendingAppends.remove(position);
            if (append != null) {
              append.completeExceptionally(error);
            }
            // the entry was not appended; continue after the last one that was
            if (!closed) {
              nextPosition = lastPosition() + 1;
            }
          });
    }

    @Override
    public void onCommit(final long index, final long highestPosition) {
      concurrencyControl.run(
          () -> {
            final var append = pendingAppends.remove(position);
            if (append != null) {
              append.complete(position);
            }
          });
    }
  }
}
