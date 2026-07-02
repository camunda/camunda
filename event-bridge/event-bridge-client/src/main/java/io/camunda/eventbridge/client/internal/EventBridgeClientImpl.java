/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.FetchResult;
import io.camunda.eventbridge.client.OffsetResetPolicy;
import io.camunda.eventbridge.client.internal.admin.TopicAdminImpl;
import io.camunda.eventbridge.client.internal.consumer.ConsumerImpl;
import io.camunda.eventbridge.client.internal.consumer.Fetcher;
import io.camunda.eventbridge.client.internal.producer.BatchPublisherImpl;
import io.camunda.eventbridge.client.internal.transport.HttpTransport;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Default {@link EventBridgeClient} implementation. Owns the shared {@link HttpTransport} (the sole
 * HTTP entry point) and a scheduled executor, wiring publish (via {@link BatchPublisherImpl}),
 * fetch (via this class implementing {@link Fetcher}), topic administration (via {@link
 * TopicAdminImpl}), and consumer-group consumption (via {@link ConsumerImpl}).
 */
public final class EventBridgeClientImpl implements EventBridgeClient, Fetcher {

  private final ScheduledExecutorService executor;
  private final OffsetResetPolicy offsetResetPolicy;
  private final HttpTransport transport;
  private final TopicAdminImpl topicAdmin;

  public EventBridgeClientImpl(final String gatewayUrl, final OffsetResetPolicy offsetResetPolicy) {
    final var normalizedUrl =
        gatewayUrl.endsWith("/") ? gatewayUrl.substring(0, gatewayUrl.length() - 1) : gatewayUrl;
    this.offsetResetPolicy = offsetResetPolicy;
    executor = Executors.newScheduledThreadPool(4);
    transport = new HttpTransport(normalizedUrl);
    topicAdmin = new TopicAdminImpl(transport);
  }

  // -------------------------------------------------------------------------
  // Publish

  @Override
  public BatchPublisher newBatch() {
    return new BatchPublisherImpl(transport);
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
            + "/fetch?offset="
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
        new ConsumerImpl(this, transport, executor, offsetResetPolicy, groupId, topics, consumerId);
    return consumer.joinGroup().handle((ignore, error) -> consumer);
  }

  // -------------------------------------------------------------------------
  // Topic administration

  @Override
  public CompletableFuture<Void> createTopic(
      final String name, final int partitionCount, final int replicationFactor) {
    return topicAdmin.createTopic(name, partitionCount, replicationFactor);
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
    executor.shutdownNow();
    transport.close();
  }
}
