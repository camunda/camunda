/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.ConsumerMetrics;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.streaming.changelog.PartitionRoleControllerFactory;
import io.camunda.eventbridge.streaming.internals.CutMetrics;
import io.camunda.eventbridge.streaming.internals.Partition;
import io.camunda.eventbridge.streaming.internals.PartitionActor;
import io.camunda.eventbridge.streaming.internals.PartitionCommitter;
import io.camunda.eventbridge.streaming.internals.SourceLoop;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
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
 * <p><b>Commit barrier — every commit is a cut.</b> A partition is committed as an independent
 * atomic cut: the actor freezes the cut at the barrier ({@link Task#freezeCut}), then the cut's
 * output is published, its state and offset persisted, and only then does that partition's source
 * offset advance (see {@link PartitionCommitter}). The publish and persist run on a bounded IO
 * executor while the partition's actor keeps folding, so a DB sink round-trip never blocks folding
 * on any partition; on shutdown the final cut runs inline on the actor thread with the offset
 * commit joined.
 *
 * <p><b>Threading.</b> {@link #run()} drives the source stage on the calling thread (so the
 * caller's thread is the one blocked in poll and interruptible on shutdown). Partition actors run
 * on the actor scheduler; the cuts publish and persist on the sink IO executor. Rebalance callbacks
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
  private final RecordFilter recordFilter;
  private final ToLongFunction<byte[]> payloadTimestamps;
  private final TaskFactory<R> taskFactory;
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
  private final MeterRegistry meterRegistry;
  private final String stageLabel;
  private final ActorScheduler injectedScheduler;
  private final ExecutorService injectedSinkExecutor;
  private final ThreadFactory sinkThreadFactory;
  private final PartitionRoleControllerFactory<R> roleControllerFactory;
  // One instance for the whole runtime's lifetime (not per subscribe attempt): its epoch gauge is
  // backed by an AtomicLong registered once, and re-registering the same gauge id+tags on a
  // resubscribe would be silently ignored by Micrometer (see MicrometerConsumerMetrics's javadoc).
  private final ConsumerMetrics consumerMetrics;

  private volatile boolean running;
  private volatile Consumer consumer;

  private StreamRuntime(final Builder<R> builder) {
    client = builder.client;
    group = builder.group;
    instanceId = builder.instanceId;
    sourceTopic = builder.sourceTopic;
    deserializer = builder.deserializer;
    recordFilter = builder.recordFilter;
    payloadTimestamps = builder.payloadTimestamps;
    taskFactory = builder.taskFactory;
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
    meterRegistry = builder.meterRegistry;
    stageLabel = builder.stageLabel;
    injectedScheduler = builder.actorScheduler;
    injectedSinkExecutor = builder.sinkExecutor;
    sinkThreadFactory =
        builder.sinkThreadFactory != null
            ? builder.sinkThreadFactory
            : defaultSinkThreadFactory(instanceId);
    roleControllerFactory = builder.roleControllerFactory;
    consumerMetrics =
        meterRegistry == null
            ? ConsumerMetrics.noop()
            : new MicrometerConsumerMetrics(meterRegistry, group);
  }

  public static <R> Builder<R> builder() {
    return new Builder<>();
  }

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
        final var subscribed = client.subscribe(group, instanceId, List.of(sourceTopic)).join();
        // Set as early as this handle allows — before the caller's first explicit heartbeat in
        // run() — so every epoch/rebalance update from then on is observed. The very first join's
        // own epoch (set inside subscribe(), before this line runs) is the one gap, same as the
        // pre-existing RebalanceListener wiring below in run().
        subscribed.metrics(consumerMetrics);
        return subscribed;
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

  /**
   * Subscribes and runs the source stage on the calling thread until {@link #stop()}. Each
   * partition restores its own baseline offset (from its task's {@link Task#restore()}) when it is
   * materialized. On exit it asks every partition actor to make its final cut and close, then tears
   * down any owned actor scheduler / sink executor and the consumer.
   */
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

    final PartitionCommitter<R> committer = new PartitionCommitter<>(consumer, sourceTopic);
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
                  CutMetrics.of(meterRegistry, partition.id(), stageLabel),
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
            recordFilter,
            payloadTimestamps,
            taskFactory,
            partitionActorFactory,
            partitionQueueCapacity,
            maxPoll,
            pollTimeout,
            errorBackoffMs,
            () -> running,
            roleControllerFactory);

    // Register before the first heartbeat so the initial assignment is observed. The callback runs
    // on the heartbeat thread and only records the delta; the source stage applies it.
    source.registerRebalanceListener();
    // Trigger the initial assignment now rather than waiting for the first scheduled heartbeat.
    consumer.sendHeartbeat().join();
    LOG.info("Stream runtime '{}' started on topic '{}'", instanceId, sourceTopic);

    source.run();

    shutdown(source, ownsScheduler ? scheduler : null, ownsSinkExecutor ? sinkExecutor : null);
    LOG.info("Stream runtime '{}' stopped", instanceId);
  }

  private void shutdown(
      final SourceLoop<R> source,
      final ActorScheduler ownedScheduler,
      final ExecutorService ownedSinkExecutor) {
    final Collection<PartitionActor<R>> partitionActors = source.partitionActors();
    // Ask each actor to make its final cut and close, then wait — the actors need the scheduler
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
    // Any surviving role controller (event-bridge-streaming ADR 0009 decision 6) — an ACTIVE
    // shard just stopped above, or a still-warming standby — is torn down fully here: this is
    // process shutdown, not a role flip, so nothing demotes; every controller simply closes.
    source.closeRoleControllers();
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
    private RecordFilter recordFilter = RecordFilter.ACCEPT_ALL;
    private ToLongFunction<byte[]> payloadTimestamps;
    private TaskFactory<R> taskFactory;
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
    private MeterRegistry meterRegistry;
    private String stageLabel;
    private ActorScheduler actorScheduler;
    private ExecutorService sinkExecutor;
    private ThreadFactory sinkThreadFactory;
    private PartitionRoleControllerFactory<R> roleControllerFactory;

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

    /**
     * An optional pre-deserialize filter: records it rejects are skipped without decoding or
     * enqueuing. Defaults to {@link RecordFilter#ACCEPT_ALL}.
     */
    public Builder<R> recordFilter(final RecordFilter recordFilter) {
      this.recordFilter = recordFilter;
      return this;
    }

    /**
     * An optional peek of a raw payload's event time, used only for filter-rejected records so
     * their (coalesced) advance carries the run's event time and stream time keeps moving during
     * filtered-only stretches. Without it a filtered run advances the commit position but not
     * stream time. Must be cheap — it runs on the source thread before any decode.
     */
    public Builder<R> payloadTimestamps(final ToLongFunction<byte[]> payloadTimestamps) {
      this.payloadTimestamps = payloadTimestamps;
      return this;
    }

    /** One {@link Task} per source partition. {@code partitionId -> task}. */
    public Builder<R> taskFactory(final IntFunction<Task<R>> taskFactory) {
      this.taskFactory = (partition, epoch) -> taskFactory.apply(partition);
      return this;
    }

    /**
     * One {@link Task} per source partition, additionally handed the runtime's {@link
     * OwnershipEpoch} — the coordinator fencing token to stamp onto external data writes.
     */
    public Builder<R> taskFactory(final TaskFactory<R> taskFactory) {
      this.taskFactory = taskFactory;
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
     * The executor that publishes and persists the per-partition commit cuts (the DB sink write +
     * offset commit), kept off the actor threads. Optional: when omitted, the runtime creates and
     * owns a bounded pool of {@link #sinkIoThreads} threads (named via {@link #sinkThreadFactory})
     * and shuts it down on stop.
     */
    public Builder<R> sinkExecutor(final ExecutorService sinkExecutor) {
      this.sinkExecutor = sinkExecutor;
      return this;
    }

    /**
     * The registry for the runtime's cut instrumentation: per-partition freeze/persist timers and
     * retry/write-stall counters, tagged by partition id only. Optional: when omitted, every
     * recording is a zero-allocation no-op — the runtime deliberately does not fall back to a
     * global registry.
     */
    public Builder<R> meterRegistry(final MeterRegistry meterRegistry) {
      this.meterRegistry = meterRegistry;
      return this;
    }

    /**
     * An observability label naming this runtime's stage (e.g. {@code "projection"}) on meters
     * whose partition tag alone would collide across runtimes sharing one registry — today the
     * {@code eb.streaming.cut.early} counter. Optional; when unset the counter carries only its
     * partition tag.
     */
    public Builder<R> stageLabel(final String stageLabel) {
      this.stageLabel = stageLabel;
      return this;
    }

    /** Size of the owned sink IO executor — the cap on concurrently persisting cuts. Default 4. */
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

    /**
     * Opts this runtime into role-aware dispatch (event-bridge-streaming ADR 0009 decision 6 /
     * consumer-groups ADR 0006 decision 1): partitions in this member's standby target warm by
     * tailing their changelog instead of folding the source, and an ACTIVE assignment promotes a
     * warmed standby's controller in place rather than tearing down and rebuilding — see {@link
     * io.camunda.eventbridge.streaming.internals.SourceLoop}'s class javadoc for the exact
     * dispatch. Optional; when unset (the default) every partition materializes straight through
     * {@link #taskFactory}, exactly as before this option existed.
     */
    public Builder<R> roleControllerFactory(
        final PartitionRoleControllerFactory<R> roleControllerFactory) {
      this.roleControllerFactory = roleControllerFactory;
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
