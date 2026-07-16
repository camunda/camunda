/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.ConsumerBuilder;
import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.FetchResult;
import io.camunda.eventbridge.client.OffsetResetPolicy;
import io.camunda.eventbridge.client.Partitioner;
import io.camunda.eventbridge.client.internal.admin.TopicAdminImpl;
import io.camunda.eventbridge.client.internal.consumer.ConsumerBuilderImpl;
import io.camunda.eventbridge.client.internal.consumer.ConsumerImpl;
import io.camunda.eventbridge.client.internal.consumer.Fetcher;
import io.camunda.eventbridge.client.internal.consumer.ManagedConsumer;
import io.camunda.eventbridge.client.internal.producer.BatchPublisherImpl;
import io.camunda.eventbridge.client.internal.producer.PublishBudget;
import io.camunda.eventbridge.client.internal.transport.HttpTransport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Default {@link EventBridgeClient} implementation. Owns the shared {@link HttpTransport} (the sole
 * HTTP entry point) and a scheduled executor, wiring publish (via {@link BatchPublisherImpl}),
 * fetch (via this class implementing {@link Fetcher}), topic administration (via {@link
 * TopicAdminImpl}), and consumer-group consumption (via {@link ConsumerImpl}).
 *
 * <p>With coordinator and fetch I/O now issued asynchronously on the JDK HTTP client's own
 * executor, the scheduled executor here only drives heartbeat cadence and prefetch backoff; it
 * defaults to a single thread (configurable via {@link Builder#schedulerThreads(int)}).
 *
 * <p>A second, shared {@link #consumerExecutor} runs the poll loops of every {@link
 * ManagedConsumer} created through {@link #consume()}. It is a thread-per-task executor backed by
 * virtual threads (not a reused pool): each loop spends almost all its time parked on the
 * long-poll, and a parked virtual thread releases its carrier. Owning it here — rather than per
 * managed consumer — gives one lifecycle and consistent thread naming; it is shut down once in
 * {@link #close()}.
 */
public final class EventBridgeClientImpl implements EventBridgeClient, Fetcher {

  private final ClientConfig config;
  private final ScheduledExecutorService executor;
  private final ExecutorService consumerExecutor;
  private final HttpTransport transport;
  private final TopicAdminImpl topicAdmin;
  private final PublishBudget publishBudget;

  /** Constructs the client from a fully-resolved {@link ClientConfig}. */
  public EventBridgeClientImpl(final ClientConfig config) {
    this.config = config;
    executor = Executors.newScheduledThreadPool(Math.max(1, config.schedulerThreads()));
    consumerExecutor =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("eb-consumer-", 0).factory());
    transport = new HttpTransport(config.gatewayUrl());
    topicAdmin = new TopicAdminImpl(transport);
    publishBudget = new PublishBudget(config.maxPublishBytes());
  }

  // -------------------------------------------------------------------------
  // Publish

  @Override
  public BatchPublisher newBatch() {
    return new BatchPublisherImpl(transport, publishBudget);
  }

  @Override
  public CompletableFuture<List<Long>> publishToTopic(
      final String topic, final int partitionId, final String key, final byte[] value) {
    return newBatch().add(key, value).publishToTopic(topic, partitionId);
  }

  @Override
  public CompletableFuture<List<Long>> publishToTopic(
      final String topic, final int partitionId, final byte[] value) {
    return newBatch().add(value).publishToTopic(topic, partitionId);
  }

  @Override
  public CompletableFuture<List<Long>> publishRouted(
      final String topic, final int partitionCount, final String key, final byte[] value) {
    final byte[] keyBytes = key == null ? null : key.getBytes(StandardCharsets.UTF_8);
    final int partition = config.partitioner().partition(topic, keyBytes, partitionCount);
    return newBatch().add(key, value).publishToTopic(topic, partition);
  }

  // -------------------------------------------------------------------------
  // Fetch

  @Override
  public CompletableFuture<FetchResult> fetchFromTopic(
      final String topic, final int partitionId, final long offset, final int maxBytes) {
    return fetchFromTopic(topic, partitionId, offset, maxBytes, 0, 0L);
  }

  @Override
  public CompletableFuture<FetchResult> fetchFromTopic(
      final String topic,
      final int partitionId,
      final long offset,
      final int maxBytes,
      final int minBytes,
      final long maxWaitMs) {
    final String path =
        "/v1/topics/"
            + topic
            + "/partitions/"
            + partitionId
            + "/records?offset="
            + offset
            + "&maxBytes="
            + maxBytes
            + "&minBytes="
            + minBytes
            + "&maxWaitMs="
            + maxWaitMs;
    return transport.getBinary(path, maxWaitMs).thenApply(EventBridgeClientImpl::toFetchResult);
  }

  /** Maps a raw fetch HTTP response to a semantic {@link FetchResult} (transport concern). */
  private static FetchResult toFetchResult(final HttpTransport.BinaryResponse response) {
    return switch (response.statusCode()) {
      case 200 -> FetchResult.parse(response.body());
      // A from-the-start / below-earliest offset: the caller resets and retries.
      case 416 -> FetchResult.outOfRange();
      default -> FetchResult.failed("Fetch failed: HTTP " + response.statusCode());
    };
  }

  // -------------------------------------------------------------------------
  // Consume (consumer groups)

  @Override
  public CompletableFuture<Consumer> subscribe(
      final String groupId, final String consumerId, final List<String> topics) {
    final var consumer =
        new ConsumerImpl(this, transport, executor, config, groupId, topics, consumerId);
    // Propagate a failed join instead of handing back an un-joined consumer (null memberId), which
    // would only surface later as a cryptic NPE on the first heartbeat.
    return consumer.joinGroup().thenApply(ignore -> consumer);
  }

  @Override
  public ConsumerBuilder<Event> consume() {
    return new ConsumerBuilderImpl<>(this, consumerExecutor);
  }

  // -------------------------------------------------------------------------
  // Topic administration

  @Override
  public CompletableFuture<Void> createTopic(
      final String name, final int partitionCount, final int replicationFactor) {
    return topicAdmin.createTopic(name, partitionCount, replicationFactor);
  }

  @Override
  public CompletableFuture<Void> createTopic(
      final String name,
      final int partitionCount,
      final int replicationFactor,
      final String cleanupPolicy) {
    return topicAdmin.createTopic(name, partitionCount, replicationFactor, cleanupPolicy);
  }

  @Override
  public CompletableFuture<Void> deleteTopic(final String name) {
    return topicAdmin.deleteTopic(name);
  }

  @Override
  public CompletableFuture<List<TopicInfo>> listTopics() {
    return topicAdmin.listTopics();
  }

  @Override
  public void close() {
    consumerExecutor.shutdownNow();
    executor.shutdownNow();
    publishBudget.close();
    transport.close();
  }

  /**
   * Default {@link Builder} implementation. Holds the resolved defaults and produces an immutable
   * {@link ClientConfig}: scheduler size {@code 1} (I/O is async on the HTTP client's executor),
   * {@code 5s} long-poll, {@code 1 MiB} fetch, {@code 0} min-bytes, prefetch depth {@code 1},
   * {@code 64 MiB} max buffered, {@code 32 MiB} max in-flight publish, {@code 3s} heartbeat, and
   * the default hash {@link Partitioner}.
   */
  public static final class BuilderImpl implements Builder {

    private String gatewayUrl;
    private OffsetResetPolicy offsetResetPolicy = OffsetResetPolicy.EARLIEST;
    private int schedulerThreads = 1;
    private long longPollMs = Long.getLong("eventbridge.consumer.longPollMs", 5_000L);
    private int fetchMaxBytes = 1 << 20;
    private int fetchMinBytes = 0;
    private int prefetchDepth = 1;
    private long maxBufferedBytes = 64L << 20;
    private long maxPublishBytes = 32L << 20;
    private long heartbeatIntervalMs = 3_000L;
    private Partitioner partitioner = Partitioner.defaultHash();

    @Override
    public Builder gateway(final String gatewayUrl) {
      this.gatewayUrl = gatewayUrl;
      return this;
    }

    @Override
    public Builder offsetReset(final OffsetResetPolicy offsetResetPolicy) {
      this.offsetResetPolicy = offsetResetPolicy;
      return this;
    }

    @Override
    public Builder schedulerThreads(final int schedulerThreads) {
      this.schedulerThreads = schedulerThreads;
      return this;
    }

    @Override
    public Builder longPollMs(final long longPollMs) {
      this.longPollMs = longPollMs;
      return this;
    }

    @Override
    public Builder fetchMaxBytes(final int fetchMaxBytes) {
      this.fetchMaxBytes = fetchMaxBytes;
      return this;
    }

    @Override
    public Builder fetchMinBytes(final int fetchMinBytes) {
      this.fetchMinBytes = fetchMinBytes;
      return this;
    }

    @Override
    public Builder prefetchDepth(final int prefetchDepth) {
      this.prefetchDepth = prefetchDepth;
      return this;
    }

    @Override
    public Builder maxBufferedBytes(final long maxBufferedBytes) {
      this.maxBufferedBytes = maxBufferedBytes;
      return this;
    }

    @Override
    public Builder maxPublishBytes(final long maxPublishBytes) {
      this.maxPublishBytes = maxPublishBytes;
      return this;
    }

    @Override
    public Builder heartbeatInterval(final Duration heartbeatInterval) {
      heartbeatIntervalMs = heartbeatInterval.toMillis();
      return this;
    }

    @Override
    public Builder partitioner(final Partitioner partitioner) {
      this.partitioner = partitioner;
      return this;
    }

    @Override
    public EventBridgeClient build() {
      Objects.requireNonNull(gatewayUrl, "gateway URL is required");
      final var normalizedUrl =
          gatewayUrl.endsWith("/") ? gatewayUrl.substring(0, gatewayUrl.length() - 1) : gatewayUrl;
      return new EventBridgeClientImpl(
          new ClientConfig(
              normalizedUrl,
              offsetResetPolicy,
              schedulerThreads,
              longPollMs,
              fetchMaxBytes,
              fetchMinBytes,
              prefetchDepth,
              maxBufferedBytes,
              maxPublishBytes,
              heartbeatIntervalMs,
              partitioner));
    }
  }
}
