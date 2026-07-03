/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * The runtime's per-partition {@link Task} registry: materializes a task lazily on first use,
 * tracks each partition's restored offset baseline (records at or below it are already folded into
 * durable state — the resume-gap dedup), and releases a task on revoke. Single-writer: only the
 * runtime thread touches it, so no synchronization.
 *
 * @param <R> the decoded record type
 */
final class PartitionTasks<R> {

  private final IntFunction<Task<R>> taskFactory;
  private final Map<Integer, Task<R>> tasks = new HashMap<>();
  private final Map<Integer, Long> restored = new HashMap<>();

  PartitionTasks(final IntFunction<Task<R>> taskFactory) {
    this.taskFactory = taskFactory;
  }

  /**
   * Seeds restored baselines from runtime-managed committed offsets (a task that owns its
   * durability instead records its own baseline in {@link #taskFor}).
   */
  void seedRestored(final Map<Integer, Long> committed) {
    restored.putAll(committed);
  }

  /**
   * Materializes and initialises a task on first use; a task that owns its durability also records
   * its restored offset baseline so the runtime can dedup its resume gap.
   */
  Task<R> taskFor(final int partition) {
    return tasks.computeIfAbsent(
        partition,
        p -> {
          final Task<R> task = taskFactory.apply(p);
          task.init();
          if (task.ownsDurability()) {
            restored.put(p, task.restore());
          }
          return task;
        });
  }

  /** The already-materialized task for {@code partition}, or {@code null}. */
  Task<R> get(final int partition) {
    return tasks.get(partition);
  }

  /**
   * The offset at or below which {@code partition}'s records are already folded (dedup baseline).
   */
  long baseline(final int partition) {
    return restored.getOrDefault(partition, Task.NO_OFFSET);
  }

  /** Removes {@code partition}'s task (on revoke) and returns it, or {@code null} if none. */
  Task<R> release(final int partition) {
    restored.remove(partition);
    return tasks.remove(partition);
  }

  /** The materialized (partition, task) pairs, for punctuation and commit. */
  Set<Map.Entry<Integer, Task<R>>> entries() {
    return tasks.entrySet();
  }

  /** Closes every task on shutdown. */
  void closeAll() {
    tasks.values().forEach(Task::close);
  }
}
