/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import io.camunda.eventbridge.client.internal.EventBridgeClientImpl;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Java client for the Event Bridge gateway HTTP API: publish, fetch, consumer-group consumption,
 * and topic administration.
 *
 * <p>This is the public API surface. Obtain an instance via {@link #create(String)} or {@link
 * #create(String, OffsetResetPolicy)}; the implementation and all HTTP/concurrency plumbing live in
 * the {@code internal} package.
 *
 * <p>Publish/fetch usage:
 *
 * <pre>{@code
 * var client = EventBridgeClient.create("http://localhost:8080");
 *
 * // Publish a batch to a topic partition
 * var positions = client.newBatch()
 *     .add("order-1", jsonBytes)
 *     .add("order-2", jsonBytes2)
 *     .publishToTopic("orders", 1)
 *     .join();
 *
 * // Fetch raw batches from a topic partition
 * var result = client.fetchFromTopic("orders", 1, 1, 64 * 1024).join();
 * for (var entry : result.entries(1)) {
 *   System.out.println(entry.getPosition() + ": " + new String(entry.getValueCopy()));
 * }
 * }</pre>
 *
 * <p>Consumer-group usage:
 *
 * <pre>{@code
 * var consumer = client.subscribe("my-group", "consumer-1", List.of("my-topic")).join();
 * consumer.sendHeartbeat().join();        // receives an initial partition assignment
 * var events = consumer.poll(100, Duration.ofMillis(1000));
 * }</pre>
 */
public interface EventBridgeClient extends AutoCloseable {

  /**
   * Creates a client with default settings (offset reset {@link OffsetResetPolicy#EARLIEST}).
   *
   * @param gatewayUrl base URL of the Event Bridge gateway (e.g. {@code "http://localhost:8080"})
   */
  static EventBridgeClient create(final String gatewayUrl) {
    return create(gatewayUrl, OffsetResetPolicy.EARLIEST);
  }

  /**
   * Creates a client with an explicit {@link OffsetResetPolicy} for newly assigned partitions that
   * have no committed offset.
   */
  static EventBridgeClient create(
      final String gatewayUrl, final OffsetResetPolicy offsetResetPolicy) {
    return builder().gateway(gatewayUrl).offsetReset(offsetResetPolicy).build();
  }

  /**
   * Returns a {@link Builder} for configuring a client (gateway URL, offset reset policy, scheduler
   * sizing, fetch/prefetch tuning, and partitioning strategy) before {@link Builder#build()}.
   */
  static Builder builder() {
    return new EventBridgeClientImpl.BuilderImpl();
  }

  // -------------------------------------------------------------------------
  // Publish

  /** Creates a new batch builder for publishing multiple entries in a single request. */
  BatchPublisher newBatch();

  /** Publishes a single keyed entry to a partition of a topic. */
  CompletableFuture<List<Long>> publishToTopic(
      String topic, int partitionId, String key, byte[] value);

  /** Publishes a single keyless entry to a partition of a topic. */
  CompletableFuture<List<Long>> publishToTopic(String topic, int partitionId, byte[] value);

  /**
   * Publishes a single keyed entry, routing it to a partition via the configured {@link
   * Partitioner} over {@code partitionCount} partitions (partitions are 1-indexed).
   *
   * <p>Named distinctly from {@link #publishToTopic(String, int, String, byte[])} because that
   * overload treats its {@code int} argument as an explicit partition id, whereas here the {@code
   * int} is the partition <em>count</em> the partitioner maps the key into.
   *
   * @param topic the topic to publish to
   * @param partitionCount the number of partitions the topic has
   * @param key the record key used to select the partition
   * @param value the record payload
   */
  CompletableFuture<List<Long>> publishRouted(
      String topic, int partitionCount, String key, byte[] value);

  // -------------------------------------------------------------------------
  // Fetch

  /**
   * Fetches batches from a partition of a topic ({@code GET /v1/topics/{topic}/.../records}), an
   * immediate read that returns whatever is available without waiting.
   */
  CompletableFuture<FetchResult> fetchFromTopic(
      String topic, int partitionId, long offset, int maxBytes);

  /**
   * Fetches batches from a partition of a topic, optionally long-polling.
   *
   * <p>When {@code maxWaitMs > 0}, the broker <em>parks</em> the request (no gateway↔broker
   * ping-pong) until at least {@code minBytes} of new data is committed or {@code maxWaitMs}
   * elapses, then returns. With {@code minBytes = 0} this is "wait for the first record, up to
   * {@code maxWaitMs}" — the efficient replacement for client-side spin-polling. When {@code
   * maxWaitMs = 0} it is an immediate read.
   *
   * @param minBytes minimum committed bytes the broker waits to accumulate before responding
   * @param maxWaitMs maximum time the broker parks the request before returning what it has
   */
  CompletableFuture<FetchResult> fetchFromTopic(
      String topic, int partitionId, long offset, int maxBytes, int minBytes, long maxWaitMs);

  // -------------------------------------------------------------------------
  // Consume (consumer groups)

  /**
   * Returns a {@link Consumer} handle for the given group and consumer IDs.
   *
   * <p>The consumer auto-registers with the coordinator on {@link Consumer#joinGroup()} (invoked
   * here) and maintains membership via periodic heartbeats.
   *
   * @param groupId consumer group identifier
   * @param consumerId consumer identifier within the group
   * @param topics the topics the group subscribes to
   * @return a future resolving to a {@link Consumer} handle
   */
  CompletableFuture<Consumer> subscribe(String groupId, String consumerId, List<String> topics);

  /**
   * Returns a {@link ConsumerBuilder} for a managed, handler-based consumer that hides the poll
   * loop: register a {@link MessageHandler}, {@link ConsumerBuilder#start()} it, and a background
   * daemon thread polls, dispatches, and (by default) auto-commits. Delivers raw {@link Event}s
   * unless a {@link Deserializer} is configured.
   */
  ConsumerBuilder<Event> consume();

  // -------------------------------------------------------------------------
  // Topic administration

  /**
   * Creates a topic. Completes when the coordinator has accepted the request (the topic's Raft
   * group is provisioned asynchronously, so it is reported {@code CREATING} until ready).
   */
  CompletableFuture<Void> createTopic(String name, int partitionCount, int replicationFactor);

  /** Deletes a topic. Completes when the coordinator has accepted the request. */
  CompletableFuture<Void> deleteTopic(String name);

  /** Lists the registered topics. */
  CompletableFuture<List<TopicInfo>> listTopics();

  /**
   * Shuts down the client's scheduler and HTTP client. After close, scheduled consumer heartbeats
   * stop and in-flight requests are abandoned. Idempotent.
   */
  @Override
  void close();

  /** A topic as reported by the registry. */
  record TopicInfo(String name, int partitionCount, int replicationFactor, String status) {}

  /**
   * Fluent builder for an {@link EventBridgeClient}. Surfaces the previously-hardcoded consumer
   * constants (long-poll duration, fetch sizing, prefetch depth) plus scheduler sizing and the
   * partitioning strategy. Unset options fall back to the documented defaults.
   */
  interface Builder {

    /** Sets the gateway base URL (e.g. {@code "http://localhost:8080"}). Required. */
    Builder gateway(String gatewayUrl);

    /** Sets the offset reset policy for newly assigned partitions. Defaults to {@code EARLIEST}. */
    Builder offsetReset(OffsetResetPolicy offsetResetPolicy);

    /**
     * Sets the size of the scheduled executor used for heartbeat cadence and prefetch backoff. I/O
     * runs asynchronously on the JDK HTTP client's own executor, so a single thread suffices.
     * Defaults to {@code 1}.
     */
    Builder schedulerThreads(int schedulerThreads);

    /**
     * Sets how long a background fetch parks on the broker before returning empty, in milliseconds.
     * Defaults to {@code 5000}.
     */
    Builder longPollMs(long longPollMs);

    /** Sets the max bytes requested per (topic, partition) fetch. Defaults to {@code 1 << 20}. */
    Builder fetchMaxBytes(int fetchMaxBytes);

    /**
     * Sets the minimum committed bytes the broker waits to accumulate before responding to a
     * background fetch. Defaults to {@code 0} (return as soon as any record is available).
     */
    Builder fetchMinBytes(int fetchMinBytes);

    /**
     * Sets the max prefetch depth per partition (buffered batches plus in-flight fetches). Depth
     * {@code 1} (the default) keeps one in-flight fetch per partition and re-fetches only once the
     * buffer drains — correct and bounded memory. Depth {@code 2} enables fetch/process pipelining
     * (the next batch is fetched while the caller processes the current one) at roughly {@code 2x}
     * buffer memory; higher depths pipeline further at proportional memory cost.
     */
    Builder prefetchDepth(int prefetchDepth);

    /**
     * Sets the interval between scheduled heartbeats to the coordinator. Defaults to {@code 3s}. A
     * shorter interval reacts faster to reassignments at the cost of more coordinator traffic; it
     * must stay well within the coordinator's session timeout.
     */
    Builder heartbeatInterval(Duration heartbeatInterval);

    /**
     * Sets the strategy that routes a keyed record to a partition for {@link
     * EventBridgeClient#publishRouted(String, int, String, byte[])}. Defaults to {@link
     * Partitioner#defaultHash()}.
     */
    Builder partitioner(Partitioner partitioner);

    /** Builds the configured client. */
    EventBridgeClient build();
  }

  /** Fluent builder for publishing a batch of entries in a single request. */
  interface BatchPublisher {

    /** Adds a keyed entry (string key). */
    BatchPublisher add(String key, byte[] value);

    /** Adds a keyed entry (byte-array key). */
    BatchPublisher add(byte[] key, byte[] value);

    /** Adds a keyless entry. */
    BatchPublisher add(byte[] value);

    /** Adds a keyless entry from a UTF-8 string value. */
    BatchPublisher add(String value);

    /**
     * Publishes the batch to a partition of a topic ({@code POST /v1/topics/{topic}/...}).
     *
     * @return a future resolving to the log positions assigned by the broker. The POC gateway
     *     returns {@code [firstPosition, lastPosition]} for the batch rather than one position per
     *     entry.
     */
    CompletableFuture<List<Long>> publishToTopic(String topic, int partitionId);
  }
}
