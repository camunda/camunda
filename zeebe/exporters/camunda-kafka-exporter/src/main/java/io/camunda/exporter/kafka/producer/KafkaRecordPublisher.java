/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.kafka.producer;

import io.camunda.exporter.kafka.config.OverflowPolicy;
import io.camunda.exporter.kafka.config.ProducerConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;

/**
 * Publishes Zeebe records to Kafka on a dedicated background flush thread, keeping the Zeebe actor
 * thread (which calls {@link #publish}) free from blocking Kafka I/O.
 *
 * <p>The Kafka producer (including {@code initTransactions()}) is initialized on the background
 * thread — not in the constructor — so that broker network latency never blocks the Zeebe actor
 * thread during {@code open()}.
 *
 * <p>Call {@link #start()} once after construction (done by {@code CamundaKafkaExporter.open()}).
 * The background thread runs the flush loop at the configured interval, or earlier when the queue
 * reaches {@code maxBatchSize}. The actor thread can read the position of the last successfully
 * flushed batch via {@link #getLastFlushedPosition()} to acknowledge it with the Zeebe controller.
 */
public final class KafkaRecordPublisher implements AutoCloseable {

  // Null until the background flush thread completes initTransactions(). Reads and the single
  // write from the flush thread are safe via volatile; flush() reads it into a local variable.
  private volatile KafkaProducer<String, String> producer;

  // Stored for async producer init in runFlushLoop(). Null in the test constructor (init skipped).
  private final ProducerConfiguration producerConfig;
  private final int partitionId;

  private final int maxBatchSize;
  private final int maxQueueSize;
  private final OverflowPolicy overflowPolicy;
  private final long flushIntervalMs;
  private final Logger logger;
  private final long closeTimeoutMs;
  private final AtomicLong lastFlushedPosition = new AtomicLong(-1);

  // Pre-allocated batch buffer — reused every flush to avoid per-flush ArrayList allocation.
  // Accessed only by the flush thread (phases 2/3) and close(), which runs after the flush
  // thread is stopped, so no concurrent access.
  private final KafkaExportRecord[] batchBuffer;

  // Pre-allocated per-flush partition-count cache — reset at the start of each flush.
  // Accessed exclusively by the flush thread and close() (same invariant as batchBuffer).
  private final Map<String, Integer> partitionCounts = new HashMap<>();

  private final Deque<KafkaExportRecord> queue = new ArrayDeque<>();
  private Thread flushThread;

  public KafkaRecordPublisher(
      final ProducerConfiguration config,
      final int partitionId,
      final int maxBatchSize,
      final int maxQueueSize,
      final OverflowPolicy overflowPolicy,
      final Duration flushInterval,
      final Logger logger) {
    this.producerConfig = config;
    this.partitionId = partitionId;
    this.maxBatchSize = maxBatchSize;
    this.maxQueueSize = maxQueueSize;
    this.overflowPolicy = overflowPolicy;
    this.flushIntervalMs = flushInterval.toMillis();
    this.logger = logger;
    this.batchBuffer = new KafkaExportRecord[maxBatchSize];
    this.closeTimeoutMs = config.closeTimeoutMs();
    // producer is intentionally null here — initialized asynchronously in runFlushLoop().
  }

  /** For testing only — accepts a pre-built producer so tests can inject a mock. */
  KafkaRecordPublisher(
      final KafkaProducer<String, String> producer,
      final int maxBatchSize,
      final int maxQueueSize,
      final OverflowPolicy overflowPolicy,
      final Duration flushInterval,
      final Logger logger,
      final long closeTimeoutMs) {
    this.producer = producer; // already initialized — async init in runFlushLoop() is skipped
    this.producerConfig = null;
    this.partitionId = -1;
    this.maxBatchSize = maxBatchSize;
    this.maxQueueSize = maxQueueSize;
    this.overflowPolicy = overflowPolicy;
    this.flushIntervalMs = flushInterval.toMillis();
    this.logger = logger;
    this.batchBuffer = new KafkaExportRecord[maxBatchSize];
    this.closeTimeoutMs = closeTimeoutMs;
  }

  /**
   * Starts the dedicated background flush thread. Must be called before {@link #publish}.
   *
   * <p>The thread initializes the Kafka producer (including {@code initTransactions()}), then
   * flushes the queue whenever the batch fills up or {@code flushInterval} elapses.
   */
  public void start() {
    if (flushThread != null) {
      throw new IllegalStateException("Publisher already started");
    }
    flushThread =
        Thread.ofPlatform().name("kafka-exporter-flush").daemon(true).start(this::runFlushLoop);
  }

  /**
   * Returns the position of the last successfully flushed record, or {@code -1} if no flush has
   * completed yet. This is updated by the background thread and is safe to read from any thread.
   */
  public long getLastFlushedPosition() {
    return lastFlushedPosition.get();
  }

  /**
   * Enqueues {@code exportRecord} for the next flush. Called on the Zeebe actor thread — never
   * blocks for Kafka I/O. With {@link OverflowPolicy#BLOCK}, the caller waits until the background
   * flush thread drains enough space; with the DROP policies the record or oldest queue entry is
   * discarded instead.
   *
   * <p>If the queue reaches {@code maxBatchSize} after this enqueue, the flush thread is signalled
   * to wake early and flush without waiting for the full interval.
   */
  public synchronized void publish(final KafkaExportRecord exportRecord) {
    if (queue.size() >= maxQueueSize && !applyOverflowPolicy()) {
      return;
    }
    queue.addLast(exportRecord);
    // Wake the flush thread early when the batch is full to reduce end-to-end latency.
    if (queue.size() >= maxBatchSize) {
      notifyAll();
    }
  }

  /**
   * Flushes one batch to Kafka. Safe to call from any thread (background flush thread and {@link
   * #close}). The queue lock is held only for the dequeue step, not during Kafka I/O, so {@link
   * #publish} is never blocked by the actual network round-trip.
   *
   * <p>Returns immediately if the producer has not yet been initialized (init is async on the flush
   * thread) or if the queue is empty.
   */
  public void flush() {
    final KafkaProducer<String, String> p = this.producer;
    if (p == null) {
      return; // producer not yet initialized — records will be sent once init completes
    }

    // Phase 1: fill batchBuffer from the queue and wake any BLOCK-waiting publishers.
    final int count;
    synchronized (this) {
      if (queue.isEmpty()) {
        return;
      }
      count = Math.min(maxBatchSize, queue.size());
      for (int i = 0; i < count; i++) {
        batchBuffer[i] = queue.removeFirst();
      }
      notifyAll();
    }

    // Phase 2: send as a single atomic transaction — lock is NOT held here.
    // If beginTransaction() itself fails, transactionBegun remains false and abortTransaction() is
    // skipped (there is nothing to abort). For any failure after beginTransaction(), the entire
    // transaction is aborted and the batch re-enqueued, preventing partial visibility to consumers.
    //
    // partitionCounts is reset here and populated lazily per topic — at most one
    // partitionsFor() call per distinct topic per flush.
    boolean transactionBegun = false;
    partitionCounts.clear();
    try {
      p.beginTransaction();
      transactionBegun = true;
      long lastPosition = -1;
      for (int i = 0; i < count; i++) {
        p.send(toProducerRecord(batchBuffer[i], p));
        lastPosition = Math.max(lastPosition, batchBuffer[i].position());
      }
      p.commitTransaction();
      lastFlushedPosition.set(lastPosition);
      logger.trace("Flushed {} Kafka records", count);
    } catch (final Exception e) {
      if (transactionBegun) {
        try {
          p.abortTransaction();
        } catch (final Exception abortException) {
          logger.warn("Failed to abort Kafka transaction", abortException);
        }
      }
      // Phase 3: re-enqueue at the front in original order for next retry.
      synchronized (this) {
        for (int i = count - 1; i >= 0; i--) {
          queue.addFirst(batchBuffer[i]);
        }
      }
      logger.warn(
          "Failed while writing Kafka records, transaction aborted, will retry on next flush", e);
    } finally {
      // Null out buffer slots so flushed records are not kept alive by this array.
      Arrays.fill(batchBuffer, 0, count, null);
    }
  }

  @Override
  public void close() {
    boolean threadTerminated = false;
    if (flushThread != null) {
      flushThread.interrupt();
      try {
        flushThread.join(closeTimeoutMs);
        threadTerminated = !flushThread.isAlive();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    } else {
      threadTerminated = true;
    }

    // Only call flush() once the thread has stopped — both share batchBuffer without a lock.
    if (threadTerminated) {
      flush();
    } else {
      logger.warn(
          "Flush thread did not terminate within {}ms; skipping final flush", closeTimeoutMs);
    }

    final KafkaProducer<String, String> p = this.producer;
    if (p != null) {
      p.close(Duration.ofMillis(closeTimeoutMs));
    }
  }

  private void runFlushLoop() {
    // Initialize the transactional Kafka producer on this background thread — NOT on the Zeebe
    // actor thread — so that initTransactions() (which waits for broker ACK) never blocks
    // the broker's partition processing.
    if (producerConfig != null) {
      KafkaProducer<String, String> p = null;
      try {
        p = new KafkaProducer<>(buildProducerProperties(producerConfig, partitionId));
        p.initTransactions();
        producer = p; // volatile write — visible to flush() and close() after this point
      } catch (final Exception e) {
        if (p != null) {
          p.close(Duration.ZERO);
        }
        logger.error(
            "Failed to initialize Kafka transactional producer; exporter will not publish records",
            e);
        return; // cannot recover without restart
      }
    }

    while (!Thread.currentThread().isInterrupted()) {
      waitForFlushSignal();
      if (Thread.currentThread().isInterrupted()) {
        break;
      }
      flush();
    }
    flush();
  }

  /**
   * Waits for at most {@code flushIntervalMs} or until {@link #publish} signals that the batch is
   * full (whichever comes first). The interrupted flag is preserved on {@link
   * InterruptedException}.
   */
  private synchronized void waitForFlushSignal() {
    try {
      wait(flushIntervalMs);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static Properties buildProducerProperties(
      final ProducerConfiguration config, final int partitionId) {
    final Properties properties = new Properties();
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.servers());
    properties.put(ProducerConfig.CLIENT_ID_CONFIG, config.clientId());
    // Stable transactional ID per (client, partition) — required for exactly-once delivery.
    // Idempotence is automatically enabled when a transactional ID is set.
    properties.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, config.clientId() + "-" + partitionId);
    properties.put(
        ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, Math.toIntExact(config.requestTimeoutMs()));
    properties.put(
        ProducerConfig.MAX_BLOCK_MS_CONFIG, Math.toIntExact(config.maxBlockingTimeoutMs()));
    properties.put(ProducerConfig.ACKS_CONFIG, "all");
    properties.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

    for (final Map.Entry<String, String> entry : config.additionalConfig().entrySet()) {
      properties.put(entry.getKey(), entry.getValue());
    }

    return properties;
  }

  private ProducerRecord<String, String> toProducerRecord(
      final KafkaExportRecord exportRecord, final KafkaProducer<String, String> p) {
    final int partitionCount =
        partitionCounts.computeIfAbsent(
            exportRecord.topic(),
            topic -> {
              final int size = p.partitionsFor(topic).size();
              if (size <= 0) {
                throw new IllegalStateException(
                    "No partitions available for topic %s".formatted(topic));
              }
              return size;
            });

    final int targetPartition = Math.floorMod(exportRecord.zeebePartitionId(), partitionCount);
    final ProducerRecord<String, String> producerRecord =
        new ProducerRecord<>(
            exportRecord.topic(), targetPartition, exportRecord.key(), exportRecord.value());
    exportRecord
        .headers()
        .forEach(
            (key, value) ->
                producerRecord.headers().add(key, value.getBytes(StandardCharsets.UTF_8)));
    return producerRecord;
  }

  private boolean applyOverflowPolicy() {
    switch (overflowPolicy) {
      case DROP_OLDEST -> {
        final KafkaExportRecord dropped = queue.pollFirst();
        if (dropped != null) {
          logger.warn(
              "Dropping oldest Kafka export record due to full queue. droppedPosition={}, maxQueueSize={}",
              dropped.position(),
              maxQueueSize);
        }
        return true;
      }
      case DROP_NEWEST -> {
        logger.warn(
            "Dropping newest Kafka export record due to full queue. maxQueueSize={}", maxQueueSize);
        return false;
      }
      case BLOCK -> {
        while (queue.size() >= maxQueueSize) {
          try {
            // Timed wait — avoids infinite block if the flush thread dies unexpectedly.
            wait(flushIntervalMs);
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn(
                "Interrupted while waiting for queue space, dropping record. maxQueueSize={}",
                maxQueueSize);
            return false;
          }
          // Guard: if the flush thread has stopped, drop rather than block forever.
          final Thread ft = flushThread;
          if (ft != null && !ft.isAlive() && queue.size() >= maxQueueSize) {
            logger.error(
                "Flush thread is no longer alive; dropping record to prevent permanent block. maxQueueSize={}",
                maxQueueSize);
            return false;
          }
        }
        return true;
      }
      default -> throw new IllegalStateException("Unexpected overflow policy: " + overflowPolicy);
    }
  }
}
