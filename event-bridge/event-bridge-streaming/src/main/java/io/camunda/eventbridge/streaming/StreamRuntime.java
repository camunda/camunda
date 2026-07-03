/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.TopicPartition;
import io.camunda.eventbridge.streaming.internals.CommitBarrier;
import io.camunda.eventbridge.streaming.internals.PartitionTasks;
import io.camunda.eventbridge.streaming.internals.PunctuationDriver;
import io.camunda.eventbridge.streaming.internals.RebalanceCoordinator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.ToLongFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A generic stream-processing runtime over an EventBridge consumer group. It owns the poll loop,
 * the two-clock cadence, restore, and shutdown, and wires four cohesive collaborators: {@link
 * PartitionTasks} (the per-partition task registry + dedup baseline), {@link CommitBarrier} (the
 * per-partition produce-before-commit), {@link PunctuationDriver} (the freshness/event-time ticks),
 * and {@link RebalanceCoordinator} (assignment deltas → acquire/release). The application supplies
 * only its {@link Task} logic and the source/state bindings.
 *
 * <p>Inversion of control: the framework drives user code, not the other way round. It depends on
 * nothing but the EventBridge client and the SPIs in this package, so it is reusable by any
 * consumer and knows nothing about what a task does.
 *
 * <p><b>Commit barrier — produce-before-commit, sharded by partition.</b> On each commit tick the
 * runtime flushes every pending task, makes that output durable <em>before</em> any offset
 * advances, then per partition persists that partition's state and offset in its own {@link
 * TransactionRunner atomic transaction} and advances only that partition's source offset (see
 * {@link CommitBarrier}). Each partition is an independent atomic cut: a crash mid-loop replays the
 * not-yet-committed partitions rather than losing them, and any re-emitted output is deduplicated
 * downstream.
 *
 * <p><b>Threading.</b> Single-threaded by design: {@link #run()} owns one consumer and drives poll,
 * processing, punctuation, and commit on its own thread; one task per partition means tasks never
 * need locks. Horizontal parallelism comes from running several runtimes, each with its own
 * consumer, via {@link StreamRuntimeGroup}. Rebalance callbacks fire on the client's heartbeat
 * thread but only record the assignment delta; the run loop applies it (see {@link
 * RebalanceCoordinator}) so all task state stays single-threaded. Not thread-safe; call {@link
 * #run()} from a single thread and {@link #stop()} from any thread.
 *
 * @param <R> the decoded record type
 */
public final class StreamRuntime<R> implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(StreamRuntime.class);

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
  private final long commitIntervalNanos;
  private final long punctuationIntervalNanos;
  private final long errorBackoffMs;

  // Collaborators, constructed in run() once the consumer exists.
  private PartitionTasks<R> tasks;
  private CommitBarrier<R> commitBarrier;
  private PunctuationDriver<R> punctuator;
  private RebalanceCoordinator<R> rebalance;

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
    commitIntervalNanos = builder.commitInterval.toNanos();
    punctuationIntervalNanos = builder.punctuationInterval.toNanos();
    errorBackoffMs = builder.errorBackoff.toMillis();
  }

  public static <R> Builder<R> builder() {
    return new Builder<>();
  }

  /**
   * Subscribes, restores committed offsets, and runs the poll/process/commit loop on the calling
   * thread until {@link #stop()}. Runs a final commit and closes the tasks and consumer on exit.
   */
  public void run() {
    running = true;
    consumer = client.subscribe(group, instanceId, List.of(sourceTopic)).join();
    tasks = new PartitionTasks<>(taskFactory);
    punctuator = new PunctuationDriver<>(tasks, timestampExtractor);
    commitBarrier =
        new CommitBarrier<>(
            tasks, consumer, sourceTopic, transactionRunner, offsets, preCommitFlushes);
    rebalance =
        new RebalanceCoordinator<>(
            tasks, commitBarrier, punctuator, consumer, sourceTopic, instanceId);
    // Register before the first heartbeat so the initial assignment is observed. The callback runs
    // on the heartbeat thread and only records the delta; the run loop applies it
    // (single-threaded).
    rebalance.register();
    // Trigger the initial assignment now rather than waiting for the first scheduled heartbeat.
    consumer.sendHeartbeat().join();
    restore();
    LOG.info("Stream runtime '{}' started on topic '{}'", instanceId, sourceTopic);

    long lastCommit = System.nanoTime();
    long lastPunctuation = System.nanoTime();
    while (running) {
      try {
        rebalance.apply();
        final List<Event> events = consumer.poll(maxPoll, pollTimeout);
        // Group the poll batch by partition and process each partition's records contiguously, so a
        // partition's task and its state cache stay hot rather than ping-ponging between partitions
        // per record. Within a partition, records keep their offset order; across partitions the
        // order is irrelevant (each is an independent shard).
        final Map<Integer, List<Event>> byPartition = new LinkedHashMap<>();
        for (final Event event : events) {
          byPartition.computeIfAbsent(event.partitionId(), p -> new ArrayList<>()).add(event);
        }
        for (final Map.Entry<Integer, List<Event>> partitionBatch : byPartition.entrySet()) {
          if (!running) {
            break; // stopped (shutdown, or a fail-fast record error) — skip the rest of the batch
          }
          for (final Event event : partitionBatch.getValue()) {
            if (!running) {
              break;
            }
            handleRecord(event);
          }
          // Memory-pressure commit: when this partition's bounded state is full of buffered writes,
          // run the barrier now so they flush durably (an atomic cut with the offset) and the
          // memory
          // frees — the change-log-free equivalent of a cache-full flush, independent of the clock.
          final Task<R> task = tasks.get(partitionBatch.getKey());
          if (task != null && task.needsCheckpoint()) {
            commitBarrier.commit();
            lastCommit = System.nanoTime();
          }
        }
        // Freshness clock: flush emitted output and advance event-time on the punctuation tick, so
        // latency stays bounded and closed windows finalize even for keys with no new records —
        // independent of (and more frequent than) the durable commit clock.
        if (System.nanoTime() - lastPunctuation >= punctuationIntervalNanos) {
          punctuator.punctuate();
          lastPunctuation = System.nanoTime();
        }
        // Durability clock: the produce-before-commit barrier makes state + offsets one atomic cut.
        if (System.nanoTime() - lastCommit >= commitIntervalNanos) {
          commitBarrier.commit();
          lastCommit = System.nanoTime();
        }
      } catch (final RuntimeException e) {
        // Transient infrastructure failure (poll/commit): back off and retry. Record-level errors
        // are handled per record by the RecordExceptionHandler, not here.
        LOG.warn(
            "Stream runtime '{}' loop failed; backing off {}ms", instanceId, errorBackoffMs, e);
        sleep(errorBackoffMs);
      }
    }

    commitBarrier.commit(); // flush the final batch on graceful shutdown
    tasks.closeAll();
    consumer.close();
    LOG.info("Stream runtime '{}' stopped", instanceId);
  }

  /**
   * Deserializes and processes one record, applying the {@link RecordExceptionHandler} policy on
   * failure: {@code SKIP} advances past the record; {@code FAIL} stops the runtime (a restart then
   * reprocesses from the last commit — no silent record loss). Records at or below the partition's
   * restored baseline are skipped (resume-gap dedup for a self-owning shard).
   */
  private void handleRecord(final Event event) {
    final int partition = event.partitionId();
    final long offset = event.position();
    final Task<R> task = tasks.taskFor(partition);
    if (offset <= tasks.baseline(partition)) {
      return;
    }
    final R record;
    try {
      record = deserializer.deserialize(event.payload(), partition, offset);
    } catch (final RuntimeException e) {
      onRecordError(partition, offset, e);
      return;
    }
    try {
      task.process(record);
    } catch (final RuntimeException e) {
      onRecordError(partition, offset, e);
      return;
    }
    commitBarrier.recordProcessed(partition, offset);
    punctuator.observe(partition, record);
  }

  private void onRecordError(final int partition, final long offset, final RuntimeException e) {
    if (recordExceptionHandler.onError(partition, offset, e)
        == RecordExceptionHandler.Decision.SKIP) {
      LOG.warn("Skipping record {}-{} after error", partition, offset, e);
      commitBarrier.recordProcessed(
          partition, offset); // advance past it so it commits, not retries
      return;
    }
    LOG.error(
        "Fatal error on record {}-{}; stopping runtime (restart resumes from last commit)",
        partition,
        offset,
        e);
    running = false;
  }

  /**
   * Seeds the restored baselines and seeks each managed partition to just after its committed
   * offset.
   */
  private void restore() {
    final Map<Integer, Long> committed = offsets.restore();
    // Remember the baseline so per-record dedup never re-folds an already-committed record. A task
    // that owns its durability restores its own baseline lazily in PartitionTasks#taskFor instead.
    tasks.seedRestored(committed);
    if (committed.isEmpty()) {
      return;
    }
    final Map<TopicPartition, Long> resume = new HashMap<>();
    committed.forEach(
        (partition, offset) -> resume.put(new TopicPartition(sourceTopic, partition), offset + 1));
    consumer.seek(resume);
    LOG.info("Stream runtime '{}' resuming from {}", instanceId, committed);
  }

  /** Requests a graceful stop; the loop observes it within one poll timeout. */
  public void stop() {
    running = false;
  }

  @Override
  public void close() {
    stop();
  }

  private static void sleep(final long millis) {
    try {
      Thread.sleep(millis);
    } catch (final InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
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
     * A producer flush to run before offsets advance (produce-before-commit). May be added zero+
     * times.
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
