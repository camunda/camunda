/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs several {@link StreamRuntime} members in parallel, one per thread — the runtime's unit of
 * horizontal scaling.
 *
 * <p>A single {@link StreamRuntime} is deliberately single-threaded: it owns one consumer and
 * processes one record at a time across the partitions that consumer is assigned. Parallelism does
 * not come from fanning those partitions out to worker threads (which would force every poll cycle
 * to wait for its slowest partition); it comes from running several members, each with its own
 * consumer and its own poll/process/commit loop. All members join the same consumer group under
 * distinct instance ids, so the group coordinator splits the source partitions across them. Each
 * member is then a single-writer over its own partitions — no locks, no shared state — and the
 * loops are fully independent: a slow partition delays only the member that owns it, never the
 * others. The effective parallelism is therefore bounded by the source partition count (extra
 * members simply get no assignment).
 *
 * <p>The caller supplies a factory that builds a fully-configured member for each index in {@code
 * [0, parallelism)}. Each member must be given a distinct {@link StreamRuntime.Builder#instanceId
 * instance id} and its own state/offset backends; the framework does not partition those for you
 * (member-local state is what keeps the members lock-free). This group owns only thread lifecycle:
 * {@link #start()} launches every member on a named daemon thread, and {@link #close()} stops them
 * all and waits for the loops to drain their final commit.
 */
public final class StreamRuntimeGroup implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(StreamRuntimeGroup.class);

  private final List<StreamRuntime<?>> members;
  private final List<Thread> threads = new ArrayList<>();

  private StreamRuntimeGroup(final List<StreamRuntime<?>> members) {
    this.members = members;
  }

  /**
   * Builds {@code parallelism} members via {@code memberFactory} (called once per index in {@code
   * [0, parallelism)}). Nothing runs until {@link #start()}.
   */
  public static StreamRuntimeGroup of(
      final int parallelism, final IntFunction<StreamRuntime<?>> memberFactory) {
    if (parallelism < 1) {
      throw new IllegalArgumentException("parallelism must be >= 1, was " + parallelism);
    }
    final List<StreamRuntime<?>> members = new ArrayList<>(parallelism);
    for (int i = 0; i < parallelism; i++) {
      members.add(memberFactory.apply(i));
    }
    return new StreamRuntimeGroup(members);
  }

  /** Starts every member on its own thread. Returns once the threads are launched, not joined. */
  public void start() {
    if (!threads.isEmpty()) {
      throw new IllegalStateException("already started");
    }
    for (int i = 0; i < members.size(); i++) {
      final StreamRuntime<?> member = members.get(i);
      final Thread thread = new Thread(member::run, "eb-stream-" + i);
      thread.setDaemon(true);
      threads.add(thread);
      thread.start();
    }
    LOG.info("Stream runtime group started with {} member(s)", members.size());
  }

  /** Signals every member to stop and waits for each loop to finish its final commit and exit. */
  @Override
  public void close() {
    members.forEach(StreamRuntime::stop);
    for (final Thread thread : threads) {
      try {
        thread.join();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        LOG.warn("Interrupted while awaiting stream runtime group shutdown");
        return;
      }
    }
    LOG.info("Stream runtime group stopped");
  }
}
