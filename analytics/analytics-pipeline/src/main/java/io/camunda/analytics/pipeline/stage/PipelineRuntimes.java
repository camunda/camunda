/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.streaming.StreamRuntime;
import io.camunda.zeebe.scheduler.ActorScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared lifecycle for the analytics pipeline stages: one right-sized actor scheduler and one sink
 * IO executor, injected into every {@link StreamRuntime} the process runs. A single stage owns its
 * own pair; the consolidated launcher shares one pair across both stages so the node runs one
 * cpu-bound pool and one sink pool instead of a per-runtime default. Both are closed only after
 * every runtime has stopped, since a runtime's final commit needs them alive.
 */
public final class PipelineRuntimes {

  private static final Logger LOG = LoggerFactory.getLogger(PipelineRuntimes.class);
  private static final long STOP_TIMEOUT_MS = 30_000;

  private PipelineRuntimes() {}

  /** A named runtime to run to shutdown. */
  public record Member(String name, StreamRuntime<?> runtime) {}

  /**
   * One EventBridge client for the whole process — the shared gateway connection that hosts every
   * stage's consumer (and Stage 1's producer). Override the gateway with {@code -Dgateway}.
   */
  public static EventBridgeClient newClient() {
    return EventBridgeClient.create(System.getProperty("gateway", "http://localhost:8080"));
  }

  /**
   * A right-sized, started actor scheduler for the partition actors. Defaults to a small cpu-bound
   * pool (the analytics source is few-partitioned); override with {@code -Danalytics.actorThreads}.
   */
  public static ActorScheduler startScheduler() {
    final int cpuThreads = Integer.getInteger("analytics.actorThreads", 2);
    final ActorScheduler scheduler =
        ActorScheduler.newActorScheduler()
            .setSchedulerName("analytics")
            .setCpuBoundActorThreadCount(cpuThreads)
            .setIoBoundActorThreadCount(1)
            .build();
    scheduler.start();
    return scheduler;
  }

  /** A bounded executor for the blocking per-partition sink commits (DB writes + offset commit). */
  public static ExecutorService newSinkExecutor() {
    final int sinkThreads = Integer.getInteger("analytics.sinkThreads", 4);
    final AtomicInteger sequence = new AtomicInteger();
    return Executors.newFixedThreadPool(
        sinkThreads,
        runnable -> {
          final Thread thread =
              new Thread(runnable, "analytics-sink-" + sequence.getAndIncrement());
          thread.setDaemon(true);
          return thread;
        });
  }

  /**
   * Runs every member on its own thread until the JVM is asked to stop, then stops the runtimes,
   * waits for them to finish their final commit, and closes the shared client, scheduler, and sink
   * executor. Close order matters: the runtimes' final commit uses the client and scheduler, so
   * those are torn down only after every runtime thread has finished.
   */
  public static void run(
      final EventBridgeClient client,
      final ActorScheduler scheduler,
      final ExecutorService sinkExecutor,
      final List<Member> members) {
    final List<Thread> threads = new ArrayList<>(members.size());
    for (final Member member : members) {
      threads.add(new Thread(member.runtime()::run, "analytics-" + member.name()));
    }
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  members.forEach(member -> member.runtime().stop());
                  threads.forEach(PipelineRuntimes::join); // runtimes finalize while resources live
                  closeScheduler(scheduler);
                  sinkExecutor.shutdownNow();
                  client.close();
                },
                "analytics-shutdown"));
    threads.forEach(Thread::start);
    threads.forEach(PipelineRuntimes::join);
  }

  private static void join(final Thread thread) {
    try {
      thread.join(STOP_TIMEOUT_MS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void closeScheduler(final ActorScheduler scheduler) {
    try {
      scheduler.close();
    } catch (final Exception e) {
      LOG.warn("Failed to close analytics actor scheduler", e);
    }
  }
}
