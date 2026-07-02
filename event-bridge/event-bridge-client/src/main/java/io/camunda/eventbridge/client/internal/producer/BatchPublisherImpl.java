/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.producer;

import io.camunda.eventbridge.api.proto.PublishResponse;
import io.camunda.eventbridge.batch.BatchBuilder;
import io.camunda.eventbridge.client.EventBridgeClient.BatchPublisher;
import io.camunda.eventbridge.client.EventBridgeException;
import io.camunda.eventbridge.client.internal.transport.HttpTransport;
import io.camunda.eventbridge.client.internal.transport.HttpTransport.BinaryResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Default {@link BatchPublisher} implementation: accumulates entries into a {@link BatchBuilder}
 * and publishes them as a single {@code application/octet-stream} POST via the shared {@link
 * HttpTransport}. The batch payload stays in the raw codec; the publish response is a protobuf
 * {@link PublishResponse} parsed here (the transport only carries bytes).
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

    final String path = "/v1/topics/" + topic + "/partitions/" + partitionId + "/records";
    return transport
        .postOctetStream(path, batchBuilder.buildSegments())
        .thenApply(this::parsePublishResponse);
  }

  private List<Long> parsePublishResponse(final BinaryResponse response) {
    if (response.statusCode() != 200) {
      throw new EventBridgeException("publish failed: HTTP " + response.statusCode());
    }
    return transport
        .parse(response.body(), PublishResponse.parser(), "publish")
        .getLogPositionsList();
  }
}
