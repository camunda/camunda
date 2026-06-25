/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.NONE;

import io.camunda.eventbridge.clustermetadata.state.topic.TopicQueryService;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsRequest;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsResponse;
import io.camunda.eventbridge.protocol.transport.CoordinationResponseEncoder;
import io.camunda.zeebe.scheduler.Actor;
import java.util.concurrent.CompletableFuture;

/**
 * Serves the read-only topic-admin request — list-topics — on its own actor, separate from the
 * {@link MetadataManager}'s write/control-plane path, so a registry scan never blocks the command
 * actor. It reads the replicated registry through its <em>own</em> {@link TopicQueryService} (on a
 * private {@link io.camunda.zeebe.db.ZeebeDb} context), so it shares no flyweights with the
 * manager. Mirrors {@code ConsumerGroupQueryHandler} on the coordinator group.
 *
 * <p>Like the manager, it returns the raw serialized response payload; the {@code
 * MetadataRequestHandler} frames it for the broker client.
 */
public final class MetadataQueryHandler extends Actor {

  private final int partitionId;
  private final TopicQueryService topics;

  public MetadataQueryHandler(final int partitionId, final TopicQueryService topics) {
    this.partitionId = partitionId;
    this.topics = topics;
  }

  @Override
  public String getName() {
    return "MetadataQueryHandler-" + partitionId;
  }

  /** Lists every registered topic and returns the raw serialized reply (no log write). */
  public CompletableFuture<byte[]> handleListTopics(final ListTopicsRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          try {
            result.complete(CoordinationResponseEncoder.serialize(listTopics()));
          } catch (final RuntimeException e) {
            result.completeExceptionally(e);
          }
        });
    return result;
  }

  private ListTopicsResponse listTopics() {
    final var response = new ListTopicsResponse().setErrorCode(NONE);
    topics
        .topicsSnapshot()
        .forEach(
            (name, meta) ->
                response.addTopic(
                    name,
                    meta.partitionCount(),
                    meta.replicationFactor(),
                    meta.status().name(),
                    meta.assignment()));
    return response;
  }
}
