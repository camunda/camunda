/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.producer;

import io.camunda.eventbridge.batch.BatchBuilder;
import io.camunda.eventbridge.client.EventBridgeClient.BatchPublisher;
import io.camunda.eventbridge.client.EventBridgeException;
import io.camunda.eventbridge.client.internal.transport.HttpTransport;
import io.camunda.eventbridge.client.internal.transport.HttpTransport.SyncResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Default {@link BatchPublisher} implementation: accumulates entries into a {@link BatchBuilder}
 * and publishes them as a single {@code application/octet-stream} POST via the shared {@link
 * HttpTransport}. The publish-response body is parsed here (the transport only carries bytes).
 */
public final class BatchPublisherImpl implements BatchPublisher {

  private final HttpTransport transport;
  private final BatchBuilder batchBuilder = new BatchBuilder();

  public BatchPublisherImpl(final HttpTransport transport) {
    this.transport = transport;
  }

  @Override
  public BatchPublisher add(final String key, final byte[] value) {
    batchBuilder.add(key, value);
    return this;
  }

  @Override
  public BatchPublisher add(final byte[] key, final byte[] value) {
    batchBuilder.add(key, value);
    return this;
  }

  @Override
  public BatchPublisher add(final byte[] value) {
    batchBuilder.add(value);
    return this;
  }

  @Override
  public BatchPublisher add(final String value) {
    batchBuilder.add(value.getBytes(StandardCharsets.UTF_8));
    return this;
  }

  @Override
  public CompletableFuture<List<Long>> publishToTopic(final String topic, final int partitionId) {
    if (batchBuilder.entryCount() == 0) {
      return CompletableFuture.failedFuture(new IllegalStateException("Batch is empty"));
    }

    final String path = "/v1/topics/" + topic + "/partitions/" + partitionId;
    return transport
        .postOctetStream(path, batchBuilder.build())
        .thenApply(this::parsePublishResponse);
  }

  private List<Long> parsePublishResponse(final SyncResponse response) {
    if (response.statusCode() != 200) {
      throw new EventBridgeException(
          "publish failed: HTTP " + response.statusCode() + " — " + response.body());
    }
    return transport.readBody(response.body(), PublishResponse.class, "publish").logPositions();
  }

  /** Body of a publish response ({@code logPositions} assigned by the broker). */
  private record PublishResponse(List<Long> logPositions) {}
}
