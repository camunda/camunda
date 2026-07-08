/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.EventBridgeClient.TopicInfo;
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

  /**
   * A bounded executor for the blocking per-partition sink commits (DB writes + offset commit).
   * Sized for {@code partitionCount} partitions: their frozen cuts persist on this pool in parallel
   * (streaming ADR 0005), so an undersized pool silently queues cuts and stretches each partition's
   * frozen window. Defaults to the partition count clamped to [2, 2&times;available processors];
   * override with {@code -Danalytics.sinkThreads}.
   */
  public static ExecutorService newSinkExecutor(final int partitionCount) {
    final int sinkThreads =
        Integer.getInteger(
            "analytics.sinkThreads",
            Math.clamp(partitionCount, 2, 2 * Runtime.getRuntime().availableProcessors()));
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
   * The registered partition count of {@code topic} — the {@link #newSinkExecutor(int)} input for a
   * stage whose partition count only the topic registry knows (e.g. Stage 1's source topic). A
   * topic that is not registered yet, or an unreachable registry, contributes {@code 0}; the sink
   * pool's minimum floor still applies.
   */
  public static int topicPartitions(final EventBridgeClient client, final String topic) {
    try {
      return client.listTopics().join().stream()
          .filter(info -> topic.equals(info.name()))
          .mapToInt(TopicInfo::partitionCount)
          .findFirst()
          .orElse(0);
    } catch (final RuntimeException e) {
      LOG.warn(
          "Could not resolve the partition count of topic {}; the sink pool falls back to its"
              + " minimum",
          topic,
          e);
      return 0;
    }
  }

  /**
   * Starts every member on its own thread and returns a {@link RunningPipeline} handle — the
   * non-blocking form for an embedding host (e.g. the analytics application's Spring lifecycle),
   * which stops the pipeline by {@link RunningPipeline#close() closing} the handle.
   */
  public static RunningPipeline start(
      final EventBridgeClient client,
      final ActorScheduler scheduler,
      final ExecutorService sinkExecutor,
      final List<Member> members) {
    final List<Member> started = List.copyOf(members);
    final List<Thread> threads = new ArrayList<>(started.size());
    for (final Member member : started) {
      threads.add(new Thread(member.runtime()::run, "analytics-" + member.name()));
    }
    threads.forEach(Thread::start);
    return new RunningPipeline(started, threads, client, scheduler, sinkExecutor);
  }

  /**
   * Runs every member until the JVM is asked to stop (the standalone-launcher form): start the
   * threads, register a shutdown hook that closes the pipeline, and block until they finish.
   */
  public static void run(
      final EventBridgeClient client,
      final ActorScheduler scheduler,
      final ExecutorService sinkExecutor,
      final List<Member> members) {
    final RunningPipeline running = start(client, scheduler, sinkExecutor, members);
    Runtime.getRuntime().addShutdownHook(new Thread(running::close, "analytics-shutdown"));
    running.await();
  }

  /**
   * A started pipeline: the running runtime threads plus the shared resources to release on stop.
   * Close order matters — the runtimes' final commit uses the client and scheduler, so those are
   * torn down only after every runtime thread has finished.
   */
  public static final class RunningPipeline implements AutoCloseable {

    private final List<Member> members;
    private final List<Thread> threads;
    private final EventBridgeClient client;
    private final ActorScheduler scheduler;
    private final ExecutorService sinkExecutor;

    private RunningPipeline(
        final List<Member> members,
        final List<Thread> threads,
        final EventBridgeClient client,
        final ActorScheduler scheduler,
        final ExecutorService sinkExecutor) {
      this.members = members;
      this.threads = threads;
      this.client = client;
      this.scheduler = scheduler;
      this.sinkExecutor = sinkExecutor;
    }

    /** Blocks until every runtime thread has finished (used by the standalone launchers). */
    public void await() {
      threads.forEach(PipelineRuntimes::join);
    }

    /** Stops the runtimes, waits for their final commit, then releases the shared resources. */
    @Override
    public void close() {
      members.forEach(member -> member.runtime().stop());
      threads.forEach(PipelineRuntimes::join);
      closeScheduler(scheduler);
      sinkExecutor.shutdownNow();
      client.close();
    }
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
