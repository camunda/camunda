/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.admin;

import io.camunda.eventbridge.client.EventBridgeClient.TopicInfo;
import io.camunda.eventbridge.client.internal.transport.HttpTransport;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Topic administration over the gateway HTTP API: create, delete, and list topics. All requests are
 * routed through the single {@link HttpTransport}.
 */
public final class TopicAdminImpl {

  private final HttpTransport transport;

  public TopicAdminImpl(final HttpTransport transport) {
    this.transport = transport;
  }

  /**
   * Creates a topic. Completes when the coordinator has accepted the request (the topic's Raft
   * group is provisioned asynchronously, so it is reported {@code CREATING} until ready).
   */
  public CompletableFuture<Void> createTopic(
      final String name, final int partitionCount, final int replicationFactor) {
    return transport.postJson(
        "/v1/topics",
        new CreateTopicRequest(name, partitionCount, replicationFactor),
        201,
        "createTopic");
  }

  /** Deletes a topic. Completes when the coordinator has accepted the request. */
  public CompletableFuture<Void> deleteTopic(final String name) {
    return transport.deleteJson(
        "/v1/topics/" + URLEncoder.encode(name, StandardCharsets.UTF_8), 204, "deleteTopic");
  }

  /** Lists the registered topics. */
  public CompletableFuture<List<TopicInfo>> listTopics() {
    return transport.getJson("/v1/topics", TopicInfo[].class, "listTopics").thenApply(List::of);
  }

  /** Body of {@code POST /v1/topics}. */
  private record CreateTopicRequest(String name, int partitionCount, int replicationFactor) {}
}
