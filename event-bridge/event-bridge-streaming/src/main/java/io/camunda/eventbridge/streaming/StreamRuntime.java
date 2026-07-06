/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.TopicPartition;
import io.camunda.eventbridge.streaming.internals.Partition;
import io.camunda.eventbridge.streaming.internals.PartitionActor;
import io.camunda.eventbridge.streaming.internals.PartitionCommitter;
import io.camunda.eventbridge.streaming.internals.SourceLoop;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.SchedulingHints;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToLongFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A generic stream-processing runtime over an EventBridge consumer group. It runs a source stage
 * that polls, decodes, and routes records into a bounded per-partition queue, and processes each
 * partition on its own actor — several partitions folding across cores at once, each single-writer.
 * The application supplies only its {@link Task} logic and the source/state bindings.
 *
 * <p>Inversion of control: the framework drives user code, not the other way round. It depends on
 * the EventBridge client, the SPIs in this package, and the actor scheduler.
 *
 * <p><b>Parallelism with single-writer safety.</b> Each source partition is an independent shard
 * (own task, state, and offset) processed by one {@link PartitionActor}. An actor never runs two of
 * its jobs at once, so a partition's task is single-writer without any lock; the actor scheduler
 * multiplexes all partition actors onto a bounded thread pool, so partition count does not imply
 * thread count. Decoding runs on the source stage ahead of folding, so even a single partition
 * overlaps its decode with its processing. Horizontal scale beyond one consumer comes from running
 * several runtimes via {@link StreamRuntimeGroup}, optionally sharing one actor scheduler.
 *
 * <p><b>Commit barrier — produce-before-commit, async.</b> A partition is committed as an
 * independent atomic cut: its output is flushed and made durable, then its state and offset are
 * persisted, then only that partition's source offset advances (see {@link PartitionCommitter}).
 * The blocking commit runs on a bounded IO executor while the partition's actor is suspended, so a
 * DB sink round-trip never blocks folding on other partitions; the offset advances only after the
 * sink write is durable.
 *
 * <p><b>Threading.</b> {@link #run()} drives the source stage on the calling thread (so the
 * caller's thread is the one blocked in poll and interruptible on shutdown). Partition actors run
 * on the actor scheduler; the blocking commits run on the sink IO executor. Rebalance callbacks
 * fire on the client's heartbeat thread but only record the assignment delta; the source stage
 * applies it. Call {@link #run()} from a single thread and {@link #stop()} from any thread.
 *
 * @param <R> the decoded record type
 */
public final class StreamRuntime<R> implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(StreamRuntime.class);
  private static final long SHUTDOWN_TIMEOUT_MS = 30_000;

  private final EventBridgeClient client;
  private final String group;
  private final String instanceId;
  private final String sourceTopic;
  private final MessageDeserializer<R> deserializer;
  private final IntFunction<Task<R>> taskFactory;
  private final TransactionRunner transactionRunner;
  private final OffsetStore offsets;
  private final List<Runnable> preCommitFlushes;
  private final RecordExceptionHandler recordExceptionHandler;
  private final ToLongFunction<R> timestampExtractor;
  private final int maxPoll;
  private final Duration pollTimeout;
  private final Duration punctuationInterval;
  private final long commitIntervalNanos;
  private final long errorBackoffMs;
  private final int processorThreads;
  private final int partitionQueueCapacity;
  private final int maxProcessBatch;
  private final int sinkIoThreads;
  private final ActorScheduler injectedScheduler;
  private final ExecutorService injectedSinkExecutor;
  private final ThreadFactory sinkThreadFactory;

  private volatile boolean running;
  private volatile Consumer consumer;

  private StreamRuntime(final Builder<R> builder) {
    client = builder.client;
    group = builder.group;
    instanceId = builder.instanceId;
    sourceTopic = builder.sourceTopic;
    deserializer = builder.deserializer;
    taskFactory = builder.taskFactory;
    transactionRunner = builder.transactionRunner;
    offsets = builder.offsets;
    preCommitFlushes = List.copyOf(builder.preCommitFlushes);
    recordExceptionHandler = builder.recordExceptionHandler;
    timestampExtractor = builder.timestampExtractor;
    maxPoll = builder.maxPoll;
    pollTimeout = builder.pollTimeout;
    punctuationInterval = builder.punctuationInterval;
    commitIntervalNanos = builder.commitInterval.toNanos();
    errorBackoffMs = builder.errorBackoff.toMillis();
    processorThreads = builder.processorThreads;
    partitionQueueCapacity = builder.partitionQueueCapacity;
    maxProcessBatch = builder.maxProcessBatch;
    sinkIoThreads = builder.sinkIoThreads;
    injectedScheduler = builder.actorScheduler;
    injectedSinkExecutor = builder.sinkExecutor;
    sinkThreadFactory =
        builder.sinkThreadFactory != null
            ? builder.sinkThreadFactory
            : defaultSinkThreadFactory(instanceId);
  }

  public static <R> Builder<R> builder() {
    return new Builder<>();
  }

  /**
   * Subscribes, restores committed offsets, and runs the source stage on the calling thread until
   * {@link #stop()}. On exit it asks every partition actor to make a final commit and close, then
   * tears down any owned actor scheduler / sink executor and the consumer.
   */
  /**
   * Subscribes at startup, retrying on failure with the configured error backoff. A freshly created
   * source topic — or one whose partition is mid-election — can reject the initial JoinGroup until
   * its coordinator is ready (e.g. a downstream stage joining a topic an upstream stage just
   * created). Rather than crash the stage on that transient race, retry until the join succeeds,
   * {@link #stop()} is called, or the attempt budget is exhausted.
   */
  private Consumer subscribeWithRetry() {
    final int maxAttempts = 30;
    RuntimeException last = null;
    for (int attempt = 1; running && attempt <= maxAttempts; attempt++) {
      try {
        return client.subscribe(group, instanceId, List.of(sourceTopic)).join();
      } catch (final CompletionException | CancellationException e) {
        last = e;
        final Throwable cause = e.getCause() != null ? e.getCause() : e;
        LOG.warn(
            "Stream runtime '{}' could not join group '{}' on topic '{}' (attempt {}/{}); retrying in {}ms: {}",
            instanceId,
            group,
            sourceTopic,
            attempt,
            maxAttempts,
            errorBackoffMs,
            cause.getMessage());
        try {
          Thread.sleep(errorBackoffMs);
        } catch (final InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(
              "Interrupted while subscribing '%s' to '%s'".formatted(instanceId, sourceTopic),
              interrupted);
        }
      }
    }
    throw new IllegalStateException(
        "Stream runtime '%s' failed to subscribe to topic '%s' after %d attempts"
            .formatted(instanceId, sourceTopic, maxAttempts),
        last);
  }

  public void run() {
    running = true;
    consumer = subscribeWithRetry();

    final boolean ownsScheduler = injectedScheduler == null;
    final ActorScheduler scheduler = ownsScheduler ? buildScheduler() : injectedScheduler;
    if (ownsScheduler) {
      scheduler.start();
    }
    final boolean ownsSinkExecutor = injectedSinkExecutor == null;
    final ExecutorService sinkExecutor =
        ownsSinkExecutor
            ? Executors.newFixedThreadPool(sinkIoThreads, sinkThreadFactory)
            : injectedSinkExecutor;

    final PartitionCommitter<R> committer =
        new PartitionCommitter<>(
            consumer, sourceTopic, transactionRunner, offsets, preCommitFlushes);
    final Map<Integer, Long> restoredBaselines = new HashMap<>();
    final Function<Partition<R>, PartitionActor<R>> partitionActorFactory =
        partition -> {
          final PartitionActor<R> partitionActor =
              new PartitionActor<>(
                  partition,
                  committer,
                  sinkExecutor,
                  recordExceptionHandler,
                  timestampExtractor,
                  punctuationInterval,
                  commitIntervalNanos,
                  maxProcessBatch,
                  () -> running,
                  this::stop);
          scheduler.submitActor(partitionActor.actor(), SchedulingHints.cpuBound()).join();
          try {
            // Submitting does not guarantee the start handler has registered the actor's
            // conditions;
            // wait for it so the first signalWork/requestStop cannot race ahead of them.
            partitionActor.awaitStarted();
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return partitionActor;
        };
    final SourceLoop<R> source =
        new SourceLoop<>(
            consumer,
            sourceTopic,
            instanceId,
            deserializer,
            taskFactory,
            partitionActorFactory,
            restoredBaselines,
            partitionQueueCapacity,
            maxPoll,
            pollTimeout,
            errorBackoffMs,
            () -> running);

    // Register before the first heartbeat so the initial assignment is observed. The callback runs
    // on the heartbeat thread and only records the delta; the source stage applies it.
    source.registerRebalanceListener();
    // Trigger the initial assignment now rather than waiting for the first scheduled heartbeat.
    consumer.sendHeartbeat().join();
    restore(restoredBaselines);
    LOG.info("Stream runtime '{}' started on topic '{}'", instanceId, sourceTopic);

    source.run();

    shutdown(
        source.partitionActors(),
        ownsScheduler ? scheduler : null,
        ownsSinkExecutor ? sinkExecutor : null);
    LOG.info("Stream runtime '{}' stopped", instanceId);
  }

  private void shutdown(
      final Collection<PartitionActor<R>> partitionActors,
      final ActorScheduler ownedScheduler,
      final ExecutorService ownedSinkExecutor) {
    // Ask each actor to make its final commit and close, then wait — the actors need the scheduler
    // and sink executor alive to do it, so tear those down only afterwards.
    partitionActors.forEach(PartitionActor::requestStop);
    for (final PartitionActor<R> partitionActor : partitionActors) {
      try {
        if (!partitionActor.awaitStopped(SHUTDOWN_TIMEOUT_MS)) {
          LOG.warn(
              "Partition {} did not stop within {}ms", partitionActor.id(), SHUTDOWN_TIMEOUT_MS);
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    if (ownedScheduler != null) {
      try {
        ownedScheduler.close();
      } catch (final Exception e) {
        LOG.warn("Failed to close actor scheduler for '{}'", instanceId, e);
      }
    }
    if (ownedSinkExecutor != null) {
      ownedSinkExecutor.shutdownNow();
    }
    consumer.close();
  }

  private ActorScheduler buildScheduler() {
    return ActorScheduler.newActorScheduler()
        .setSchedulerName("eb-stream-" + instanceId)
        .setCpuBoundActorThreadCount(processorThreads)
        .setIoBoundActorThreadCount(1)
        .build();
  }

  /**
   * Seeds the restored baselines and seeks each managed partition to just after its committed
   * offset. A task that owns its durability restores its own baseline when it is materialized.
   */
  private void restore(final Map<Integer, Long> restoredBaselines) {
    final Map<Integer, Long> committed = offsets.restore();
    restoredBaselines.putAll(committed);
    if (committed.isEmpty()) {
      return;
    }
    final Map<TopicPartition, Long> resume = new HashMap<>();
    committed.forEach(
        (partition, offset) -> resume.put(new TopicPartition(sourceTopic, partition), offset + 1));
    consumer.seek(resume);
    LOG.info("Stream runtime '{}' resuming from {}", instanceId, committed);
  }

  /** Requests a graceful stop; the source loop observes it within one poll timeout. */
  public void stop() {
    running = false;
  }

  @Override
  public void close() {
    stop();
  }

  private static ThreadFactory defaultSinkThreadFactory(final String instanceId) {
    final String prefix = "eb-sink-" + instanceId + "-";
    final AtomicInteger sequence = new AtomicInteger();
    return runnable -> {
      final Thread thread = new Thread(runnable, prefix + sequence.getAndIncrement());
      thread.setDaemon(true);
      return thread;
    };
  }

  /** Fluent builder; all collaborators are required except the tunables, which have defaults. */
  public static final class Builder<R> {

    private EventBridgeClient client;
    private String group;
    private String instanceId;
    private String sourceTopic;
    private MessageDeserializer<R> deserializer;
    private IntFunction<Task<R>> taskFactory;
    // Runtime-managed durability. Optional: a task that owns its durability (ownsDurability()) uses
    // its own transaction and offset store instead, so these stay at their no-op defaults.
    private TransactionRunner transactionRunner = Runnable::run;
    private OffsetStore offsets =
        new OffsetStore() {
          @Override
          public Map<Integer, Long> restore() {
            return Map.of();
          }

          @Override
          public void store(final int partition, final long offset) {}
        };
    private final List<Runnable> preCommitFlushes = new ArrayList<>();
    private RecordExceptionHandler recordExceptionHandler = RecordExceptionHandler.FAIL_FAST;
    private ToLongFunction<R> timestampExtractor;
    private int maxPoll = 5000;
    private Duration pollTimeout = Duration.ofMillis(500);
    private Duration commitInterval = Duration.ofSeconds(1);
    private Duration punctuationInterval = Duration.ofMillis(500);
    private Duration errorBackoff = Duration.ofSeconds(1);
    private int processorThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
    private int partitionQueueCapacity = 10_000;
    private int maxProcessBatch = 2_000;
    private int sinkIoThreads = 4;
    private ActorScheduler actorScheduler;
    private ExecutorService sinkExecutor;
    private ThreadFactory sinkThreadFactory;

    private Builder() {}

    public Builder<R> client(final EventBridgeClient client) {
      this.client = client;
      return this;
    }

    public Builder<R> group(final String group) {
      this.group = group;
      return this;
    }

    public Builder<R> instanceId(final String instanceId) {
      this.instanceId = instanceId;
      return this;
    }

    public Builder<R> sourceTopic(final String sourceTopic) {
      this.sourceTopic = sourceTopic;
      return this;
    }

    public Builder<R> deserializer(final MessageDeserializer<R> deserializer) {
      this.deserializer = deserializer;
      return this;
    }

    /** One {@link Task} per source partition. {@code partitionId -> task}. */
    public Builder<R> taskFactory(final IntFunction<Task<R>> taskFactory) {
      this.taskFactory = taskFactory;
      return this;
    }

    public Builder<R> transactionRunner(final TransactionRunner transactionRunner) {
      this.transactionRunner = transactionRunner;
      return this;
    }

    public Builder<R> offsetStore(final OffsetStore offsets) {
      this.offsets = offsets;
      return this;
    }

    /**
     * A producer flush to run before offsets advance (produce-before-commit), for the
     * runtime-managed durability path where partitions share an output sink. May be added zero+
     * times. A task that owns its durability flushes its own sink via {@link Task#preCommitFlush}
     * instead.
     */
    public Builder<R> preCommitFlush(final Runnable flush) {
      preCommitFlushes.add(flush);
      return this;
    }

    /** Policy for a record that fails to deserialize/process. Defaults to fail-fast. */
    public Builder<R> recordExceptionHandler(final RecordExceptionHandler handler) {
      recordExceptionHandler = handler;
      return this;
    }

    /**
     * Extracts a record's event timestamp so the runtime can advance per-partition stream time and
     * drive event-time punctuation ({@link Task#advanceStreamTime}). Optional: without it the
     * punctuation tick still flushes for freshness, but windows finalize only as new records
     * arrive.
     */
    public Builder<R> timestampExtractor(final ToLongFunction<R> timestampExtractor) {
      this.timestampExtractor = timestampExtractor;
      return this;
    }

    public Builder<R> maxPoll(final int maxPoll) {
      this.maxPoll = maxPoll;
      return this;
    }

    public Builder<R> pollTimeout(final Duration pollTimeout) {
      this.pollTimeout = pollTimeout;
      return this;
    }

    public Builder<R> commitInterval(final Duration commitInterval) {
      this.commitInterval = commitInterval;
      return this;
    }

    /**
     * The freshness/punctuation cadence: how often the runtime flushes emitted output and advances
     * stream time, independent of the durable {@link #commitInterval}. Defaults to {@code 500ms}.
     */
    public Builder<R> punctuationInterval(final Duration punctuationInterval) {
      this.punctuationInterval = punctuationInterval;
      return this;
    }

    public Builder<R> errorBackoff(final Duration errorBackoff) {
      this.errorBackoff = errorBackoff;
      return this;
    }

    /**
     * The cpu-bound actor thread count of the actor scheduler this runtime creates and owns when no
     * {@link #actorScheduler} is supplied — the cap on how many partitions fold in parallel.
     * Ignored when an actor scheduler is injected. Defaults to the available processor count.
     */
    public Builder<R> processorThreads(final int processorThreads) {
      if (processorThreads < 1) {
        throw new IllegalArgumentException(
            "processorThreads must be >= 1, was " + processorThreads);
      }
      this.processorThreads = processorThreads;
      return this;
    }

    /**
     * The actor scheduler that runs the partition actors. Optional: supply a shared scheduler so
     * several runtimes (or the whole node) use one cpu-bound pool. When omitted, the runtime
     * creates and owns a scheduler sized by {@link #processorThreads} and closes it on stop.
     */
    public Builder<R> actorScheduler(final ActorScheduler actorScheduler) {
      this.actorScheduler = actorScheduler;
      return this;
    }

    /**
     * The executor that runs the blocking per-partition commits (the DB sink write + offset
     * commit), kept off the actor threads. Optional: when omitted, the runtime creates and owns a
     * bounded pool of {@link #sinkIoThreads} threads (named via {@link #sinkThreadFactory}) and
     * shuts it down on stop.
     */
    public Builder<R> sinkExecutor(final ExecutorService sinkExecutor) {
      this.sinkExecutor = sinkExecutor;
      return this;
    }

    /** Size of the owned sink IO executor — the cap on concurrent blocking commits. Default 4. */
    public Builder<R> sinkIoThreads(final int sinkIoThreads) {
      if (sinkIoThreads < 1) {
        throw new IllegalArgumentException("sinkIoThreads must be >= 1, was " + sinkIoThreads);
      }
      this.sinkIoThreads = sinkIoThreads;
      return this;
    }

    /**
     * The thread factory for the owned sink IO executor, letting the caller own that thread policy
     * (name, daemon status, priority, or a virtual-thread factory). Ignored when a {@link
     * #sinkExecutor} is injected. Defaults to named daemon platform threads.
     */
    public Builder<R> sinkThreadFactory(final ThreadFactory sinkThreadFactory) {
      this.sinkThreadFactory = sinkThreadFactory;
      return this;
    }

    /**
     * The per-partition decoded-record queue capacity — the buffer the source fills ahead of the
     * actor, and the point at which back-pressure kicks in. Defaults to {@code 10000}.
     */
    public Builder<R> partitionQueueCapacity(final int partitionQueueCapacity) {
      if (partitionQueueCapacity < 1) {
        throw new IllegalArgumentException(
            "partitionQueueCapacity must be >= 1, was " + partitionQueueCapacity);
      }
      this.partitionQueueCapacity = partitionQueueCapacity;
      return this;
    }

    /**
     * The maximum number of records an actor drains and folds per job before yielding, so one busy
     * partition cannot starve timers or other partitions on the same thread. Defaults to {@code
     * 2000}.
     */
    public Builder<R> maxProcessBatch(final int maxProcessBatch) {
      if (maxProcessBatch < 1) {
        throw new IllegalArgumentException("maxProcessBatch must be >= 1, was " + maxProcessBatch);
      }
      this.maxProcessBatch = maxProcessBatch;
      return this;
    }

    public StreamRuntime<R> build() {
      Objects.requireNonNull(client, "client");
      Objects.requireNonNull(group, "group");
      Objects.requireNonNull(instanceId, "instanceId");
      Objects.requireNonNull(sourceTopic, "sourceTopic");
      Objects.requireNonNull(deserializer, "deserializer");
      Objects.requireNonNull(taskFactory, "taskFactory");
      return new StreamRuntime<>(this);
    }
  }
}
