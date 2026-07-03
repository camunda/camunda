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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.ToLongFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A generic stream-processing runtime over an EventBridge consumer group. It owns everything an
 * application would otherwise hand-roll: subscribing, the (long-polling) poll loop, one
 * single-threaded {@link Task} per source partition with lazy restore, the two-clock cadence, and
 * the commit barrier — leaving the application to supply only its {@link Task} logic and the
 * source/state bindings.
 *
 * <p>Inversion of control: the framework drives user code, not the other way round. It depends on
 * nothing but the EventBridge client and the four SPIs in this package, so it is reusable by any
 * consumer and knows nothing about what a task does.
 *
 * <p><b>Commit barrier — produce-before-commit, sharded by partition.</b> On each commit tick the
 * runtime (1) flushes every pending task, emitting its produced output, and makes that output
 * durable at the destination <em>before</em> any offset advances; then, <em>per partition</em>, (2)
 * persists that partition's state and offset in its own {@link TransactionRunner atomic
 * transaction} and (3) advances only that partition's source offset. Each partition is thus an
 * independent atomic cut: a crash mid-loop replays the not-yet-committed partitions rather than
 * losing them, and any re-emitted output is deduplicated downstream by idempotent overwrite.
 *
 * <p><b>Threading.</b> A runtime is single-threaded by design: {@link #run()} owns one consumer and
 * drives poll, processing, punctuation, and commit on its own thread, processing one record at a
 * time across the partitions that consumer is assigned. One task per partition therefore means
 * tasks never need locks. Horizontal parallelism does not come from fanning a consumer's partitions
 * out to worker threads — that would make every poll cycle wait for its slowest partition; it comes
 * from running several runtimes, each with its own consumer, via {@link StreamRuntimeGroup}: the
 * group coordinator splits the source partitions across the members and their loops run
 * independently, so a slow partition delays only the member that owns it. Not thread-safe; call
 * {@link #run()} from a single thread and {@link #stop()} from any thread (e.g. a shutdown hook).
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

  private final Map<Integer, Task<R>> tasks = new HashMap<>();
  private final Map<Integer, Long> pending = new HashMap<>();

  /** Per-partition stream time: the max event timestamp seen, for event-time punctuation. */
  private final Map<Integer, Long> streamTime = new HashMap<>();

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
    // Trigger the initial assignment now rather than waiting for the first scheduled heartbeat.
    consumer.sendHeartbeat().join();
    restore();
    LOG.info("Stream runtime '{}' started on topic '{}'", instanceId, sourceTopic);

    long lastCommit = System.nanoTime();
    long lastPunctuation = System.nanoTime();
    while (running) {
      try {
        final List<Event> events = consumer.poll(maxPoll, pollTimeout);
        for (final Event event : events) {
          if (!running) {
            break; // stopped (shutdown, or a fail-fast record error) — skip the rest of the batch
          }
          handleRecord(event);
          // Memory-pressure commit: when a task's bounded state is full of buffered writes, run the
          // barrier now so they flush durably (an atomic cut with the offset) and the memory frees
          // —
          // the change-log-free equivalent of a cache-full flush. Independent of the commit clock.
          final Task<R> task = tasks.get(event.partitionId());
          if (task != null && task.needsCheckpoint()) {
            commit();
            lastCommit = System.nanoTime();
          }
        }
        // Freshness clock: flush emitted output and advance event-time on the punctuation tick, so
        // latency stays bounded and closed windows finalize even for keys with no new records —
        // independent of (and more frequent than) the durable commit clock.
        if (System.nanoTime() - lastPunctuation >= punctuationIntervalNanos) {
          punctuate();
          lastPunctuation = System.nanoTime();
        }
        // Durability clock: the produce-before-commit barrier makes state + offsets one atomic cut.
        if (System.nanoTime() - lastCommit >= commitIntervalNanos) {
          commit();
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

    commit(); // flush the final batch on graceful shutdown
    tasks.values().forEach(Task::close);
    consumer.close();
    LOG.info("Stream runtime '{}' stopped", instanceId);
  }

  /**
   * Deserializes and processes one record, applying the {@link RecordExceptionHandler} policy on
   * failure: {@code SKIP} advances past the record; {@code FAIL} stops the runtime (a restart then
   * reprocesses from the last commit — no silent record loss).
   */
  private void handleRecord(final Event event) {
    final int partition = event.partitionId();
    final long offset = event.position();
    final R record;
    try {
      record = deserializer.deserialize(event.payload(), partition, offset);
    } catch (final RuntimeException e) {
      onRecordError(partition, offset, e);
      return;
    }
    try {
      taskFor(partition).process(record);
    } catch (final RuntimeException e) {
      onRecordError(partition, offset, e);
      return;
    }
    pending.merge(partition, offset, Math::max);
    if (timestampExtractor != null) {
      streamTime.merge(partition, timestampExtractor.applyAsLong(record), Math::max);
    }
  }

  /**
   * Punctuation for every materialized task on the freshness tick: flush emitted output (bounded
   * latency), fire the wall-clock tick (so idle partitions still do time-driven work), and — when a
   * timestamp extractor is configured — advance the task's stream time so closed windows finalize.
   * Does not make state durable — that is {@link #commit()}.
   */
  private void punctuate() {
    final long wallClockMs = System.currentTimeMillis();
    for (final Map.Entry<Integer, Task<R>> entry : tasks.entrySet()) {
      final Task<R> task = entry.getValue();
      task.flush();
      // Wall-clock tick: fires even for a fully idle partition (no event-time progress), so
      // time-driven work still runs. Event-time punctuation advances only as records arrive.
      task.punctuateWallClock(wallClockMs);
      if (timestampExtractor != null) {
        final Long partitionStreamTime = streamTime.get(entry.getKey());
        if (partitionStreamTime != null) {
          task.advanceStreamTime(partitionStreamTime);
        }
      }
    }
  }

  private void onRecordError(final int partition, final long offset, final RuntimeException e) {
    if (recordExceptionHandler.onError(partition, offset, e)
        == RecordExceptionHandler.Decision.SKIP) {
      LOG.warn("Skipping record {}-{} after error", partition, offset, e);
      pending.merge(
          partition, offset, Math::max); // advance past it so it commits and is not retried
      return;
    }
    LOG.error(
        "Fatal error on record {}-{}; stopping runtime (restart resumes from last commit)",
        partition,
        offset,
        e);
    running = false;
  }

  /** Requests a graceful stop; the loop observes it within one poll timeout. */
  public void stop() {
    running = false;
  }

  @Override
  public void close() {
    stop();
  }

  /** Seeks each partition to just after its last committed offset (co-committed with state). */
  private void restore() {
    final Map<Integer, Long> committed = offsets.restore();
    if (committed.isEmpty()) {
      return;
    }
    final Map<TopicPartition, Long> resume = new HashMap<>();
    committed.forEach(
        (partition, offset) -> resume.put(new TopicPartition(sourceTopic, partition), offset + 1));
    consumer.seek(resume);
    LOG.info("Stream runtime '{}' resuming from {}", instanceId, committed);
  }

  /** Materializes (constructing restores durable state) and initialises a task on first use. */
  private Task<R> taskFor(final int partition) {
    return tasks.computeIfAbsent(
        partition,
        p -> {
          final Task<R> task = taskFactory.apply(p);
          task.init();
          return task;
        });
  }

  /**
   * The produce-before-commit barrier, sharded by partition (see class javadoc). No-op when nothing
   * was processed. Each partition is an independent atomic cut: its produced output is made
   * durable, then its state and offset are persisted in that partition's own transaction, then only
   * that partition's source offset advances — so one partition's commit neither blocks nor
   * entangles another's, and a crash mid-loop simply replays the partitions not yet committed.
   */
  private void commit() {
    if (pending.isEmpty()) {
      return;
    }
    // Emit every pending partition's output, then make it durable before any offset advances.
    // Global sinks run once (for tasks whose output is published elsewhere); a self-contained
    // sharded task instead flushes its own partition's output via Task#preCommitFlush below.
    for (final int partition : pending.keySet()) {
      tasks.get(partition).flush();
    }
    preCommitFlushes.forEach(Runnable::run);
    for (final Map.Entry<Integer, Long> entry : pending.entrySet()) {
      final int partition = entry.getKey();
      final long offset = entry.getValue();
      final Task<R> task = tasks.get(partition);
      task.preCommitFlush();
      // Store the offset first, then checkpoint: if the offset store is write-back cached and
      // shares
      // the task's backing store, the checkpoint flushes it in this same transaction, so this
      // partition's state and offset land as one atomic cut.
      transactionRunner.runInTransaction(
          () -> {
            offsets.store(partition, offset);
            task.checkpoint();
          });
      consumer.commitOffset(sourceTopic, partition, offset).join();
    }
    pending.clear();
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
    private TransactionRunner transactionRunner;
    private OffsetStore offsets;
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
      Objects.requireNonNull(transactionRunner, "transactionRunner");
      Objects.requireNonNull(offsets, "offsetStore");
      return new StreamRuntime<>(this);
    }
  }
}
