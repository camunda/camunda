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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A generic stream-processing runtime over an EventBridge consumer group. It owns everything an
 * application would otherwise hand-roll: subscribing, the (long-polling) poll loop, one
 * single-threaded {@link Task} per source partition with lazy restore, the two-clock cadence, and
 * the commit barrier — leaving the application to supply only its {@link Task} logic and the
 * source/state bindings.
 *
 * <p>Modeled on the Kafka Streams {@code StreamThread} / Samza {@code RunLoop}: the framework
 * drives user code, not the other way round. It depends on nothing but the EventBridge client and
 * the four SPIs in this package, so it is reusable by any consumer and knows nothing about what a
 * task does.
 *
 * <p><b>Commit barrier — produce-before-commit.</b> On each commit tick the runtime (1) flushes
 * each task, which emits its produced output; (2) runs the {@code preCommitFlushes} so that output
 * is durable at the destination <em>before</em> any offset advances; (3) persists task state and
 * the consumed offsets in one {@link TransactionRunner atomic transaction}; and only then (4)
 * commits the source offset. A crash therefore replays rather than loses, and any re-emitted output
 * is deduplicated downstream by idempotent overwrite.
 *
 * <p><b>Threading.</b> {@link #run()} drives processing, punctuation, and commit on its own thread;
 * one task per partition means tasks never need locks. Not thread-safe; call {@link #run()} from a
 * single thread and {@link #stop()} from any thread (e.g. a shutdown hook).
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
  private final int maxPoll;
  private final Duration pollTimeout;
  private final long commitIntervalNanos;
  private final long errorBackoffMs;

  private final Map<Integer, Task<R>> tasks = new HashMap<>();
  private final Map<Integer, Long> pending = new HashMap<>();

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
    maxPoll = builder.maxPoll;
    pollTimeout = builder.pollTimeout;
    commitIntervalNanos = builder.commitInterval.toNanos();
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
    while (running) {
      try {
        final List<Event> events = consumer.poll(maxPoll, pollTimeout);
        for (final Event event : events) {
          final R record =
              deserializer.deserialize(event.payload(), event.partitionId(), event.position());
          taskFor(event.partitionId()).process(record);
          pending.merge(event.partitionId(), event.position(), Math::max);
        }
        if (System.nanoTime() - lastCommit >= commitIntervalNanos) {
          commit();
          lastCommit = System.nanoTime();
        }
      } catch (final RuntimeException e) {
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

  /** The produce-before-commit barrier (see class javadoc). No-op when nothing was processed. */
  private void commit() {
    if (pending.isEmpty()) {
      return;
    }
    for (final int partition : pending.keySet()) {
      tasks.get(partition).flush();
    }
    preCommitFlushes.forEach(Runnable::run);
    transactionRunner.runInTransaction(
        () ->
            pending.forEach(
                (partition, offset) -> {
                  // Store the offset first, then checkpoint: if the offset store is write-back
                  // cached
                  // and shares the task's backing store, the checkpoint flushes it in this same
                  // transaction, so state and offset land as one atomic cut.
                  offsets.store(partition, offset);
                  tasks.get(partition).checkpoint();
                }));
    pending.forEach(
        (partition, offset) -> consumer.commitOffset(sourceTopic, partition, offset).join());
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
    private int maxPoll = 5000;
    private Duration pollTimeout = Duration.ofMillis(500);
    private Duration commitInterval = Duration.ofSeconds(1);
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
