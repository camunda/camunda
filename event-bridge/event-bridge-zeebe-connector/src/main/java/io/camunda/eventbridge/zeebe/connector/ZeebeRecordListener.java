/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.zeebe.connector;

import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.TopicPartition;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes Zeebe records from the Event Bridge and dispatches them to handlers, hiding the poll
 * loop, deserialization and offset commits.
 *
 * <p>Build one with {@link #builder(EventBridgeClient)}, register handlers — a catch-all over
 * {@link Record} or typed handlers keyed by record value class — and {@link Builder#start()}.
 *
 * <p>A single thread polls and deserializes records and hands them to a {@link
 * PartitionedRecordProcessor}, which runs handlers on a worker pool: records of one partition run
 * in order, records of different partitions run in parallel. Scale handler throughput with {@link
 * Builder#workers(int)}; bound memory with {@link Builder#maxInFlight(int)}. With auto-commit on
 * (the default) the latest <em>processed</em> offset per partition is committed after each batch.
 *
 * <p>Handlers may be invoked concurrently for records of different partitions, so handlers sharing
 * mutable state must be thread-safe. {@link #close()} stops polling, drains in-flight work, commits
 * and leaves the group.
 */
public final class ZeebeRecordListener implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeRecordListener.class);

  private final ZeebeRecordConsumer consumer;
  private final PartitionedRecordProcessor processor;
  private final boolean autoCommit;
  private final int pollSize;
  private final Duration pollTimeout;
  private final Thread thread;
  private final Map<TopicPartition, Long> lastCommitted = new HashMap<>();

  private volatile boolean running = true;

  private ZeebeRecordListener(
      final ZeebeRecordConsumer consumer,
      final PartitionedRecordProcessor processor,
      final boolean autoCommit,
      final int pollSize,
      final Duration pollTimeout) {
    this.consumer = consumer;
    this.processor = processor;
    this.autoCommit = autoCommit;
    this.pollSize = pollSize;
    this.pollTimeout = pollTimeout;
    thread = new Thread(this::runLoop, "zeebe-record-listener");
    thread.setDaemon(true);
    thread.start();
  }

  public static Builder builder(final EventBridgeClient client) {
    return new Builder(client);
  }

  private void runLoop() {
    while (running) {
      final List<ZeebeRecord> batch;
      try {
        batch = consumer.poll(pollSize, pollTimeout);
      } catch (final RuntimeException e) {
        if (running) {
          LOG.warn("Poll failed; retrying", e);
        }
        continue;
      }

      try {
        processor.submit(batch);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }

      if (autoCommit) {
        commitProcessed();
      }
    }
  }

  /** Commits the latest processed offset per partition that has advanced since the last commit. */
  private void commitProcessed() {
    for (final Map.Entry<TopicPartition, ZeebeRecord> entry :
        processor.completedOffsets().entrySet()) {
      final ZeebeRecord record = entry.getValue();
      final Long committed = lastCommitted.get(entry.getKey());
      if (committed == null || record.offset() > committed) {
        consumer.commit(record).exceptionally(commitFailed(record));
        lastCommitted.put(entry.getKey(), record.offset());
      }
    }
  }

  private static Function<Throwable, Void> commitFailed(final ZeebeRecord record) {
    return error -> {
      LOG.warn("Commit failed for offset {}", record.offset(), error);
      return null;
    };
  }

  @Override
  public void close() {
    running = false;
    thread.interrupt();
    try {
      thread.join(Duration.ofSeconds(5).toMillis());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    processor.close();
    if (autoCommit) {
      commitProcessed();
    }
    consumer.close();
  }

  public static final class Builder {

    private final EventBridgeClient client;
    private final RecordDispatcher dispatcher = new RecordDispatcher();
    private String group;
    private String consumerId = "consumer-" + UUID.randomUUID();
    private List<String> topics = List.of();
    private int pollSize = 100;
    private Duration pollTimeout = Duration.ofSeconds(1);
    private boolean autoCommit = true;
    private int workers = Math.max(1, Runtime.getRuntime().availableProcessors());
    private int maxInFlight = 10_000;

    private Builder(final EventBridgeClient client) {
      this.client = client;
    }

    public Builder group(final String group) {
      this.group = group;
      return this;
    }

    public Builder consumerId(final String consumerId) {
      this.consumerId = consumerId;
      return this;
    }

    public Builder topics(final String... topics) {
      this.topics = List.of(topics);
      return this;
    }

    public Builder topics(final List<String> topics) {
      this.topics = List.copyOf(topics);
      return this;
    }

    public Builder pollSize(final int pollSize) {
      this.pollSize = pollSize;
      return this;
    }

    public Builder pollTimeout(final Duration pollTimeout) {
      this.pollTimeout = pollTimeout;
      return this;
    }

    public Builder autoCommit(final boolean autoCommit) {
      this.autoCommit = autoCommit;
      return this;
    }

    /** Worker threads processing records across partitions. Defaults to the CPU count. */
    public Builder workers(final int workers) {
      this.workers = workers;
      return this;
    }

    /** Maximum records processed concurrently before polling back-pressures. */
    public Builder maxInFlight(final int maxInFlight) {
      this.maxInFlight = maxInFlight;
      return this;
    }

    /** Registers a catch-all handler invoked for every record. */
    public Builder onRecord(final Consumer<Record<?>> handler) {
      dispatcher.onAny(handler);
      return this;
    }

    /** Registers a handler invoked only for records whose value is of the given type. */
    public <V extends RecordValue> Builder onRecord(
        final Class<V> type, final BiConsumer<Record<?>, ? super V> handler) {
      dispatcher.on(type, handler);
      return this;
    }

    /** Subscribes to the configured topics and starts the background dispatch loop. */
    public ZeebeRecordListener start() {
      final ZeebeRecordConsumer consumer =
          ZeebeRecordConsumer.subscribe(client, group, consumerId, topics).join();
      final PartitionedRecordProcessor processor =
          new PartitionedRecordProcessor(dispatcher, workers, maxInFlight);
      return new ZeebeRecordListener(consumer, processor, autoCommit, pollSize, pollTimeout);
    }
  }
}
