/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.core.topic.TopicGroups;
import io.camunda.eventbridge.mapper.ResponseMapper;
import io.camunda.eventbridge.protocol.request.FetchResponse;
import io.camunda.eventbridge.service.FetchService;
import io.camunda.eventbridge.service.PublishService;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The topic data plane: publish/fetch addressed by {@code (topic, partition)}, routed to the
 * topic's own Raft group ({@code event-bridge-topic-<name>}). Identical to the data-partition
 * endpoints under {@code /v1/events}, but the BrokerClient resolves the partition leader from the
 * topic group's gossiped topology instead of the default data group.
 */
@RestController
@RequestMapping("/v1/topics")
public class TopicDataController {

  private final PublishService publishService;
  private final FetchService fetchService;
  private final ResponseMapper responseMapper;

  public TopicDataController(
      final PublishService publishService,
      final FetchService fetchService,
      final ResponseMapper responseMapper) {
    this.publishService = publishService;
    this.fetchService = fetchService;
    this.responseMapper = responseMapper;
  }

  @PostMapping(
      value = "/{topic}/partitions/{partitionId}",
      consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> publish(
      @PathVariable final String topic,
      @PathVariable final int partitionId,
      @RequestBody final byte[] body) {

    return publishService
        .publish(TopicGroups.name(topic), partitionId, body)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return ResponseEntity.internalServerError()
                    .body((Object) ("Publish failed: " + rootMessage(error)));
              }
              return ResponseEntity.ok((Object) responseMapper.toPublishBatchResponse(res));
            });
  }

  @GetMapping(
      value = "/{topic}/partitions/{partitionId}/fetch",
      produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public CompletableFuture<ResponseEntity<byte[]>> fetch(
      @PathVariable final String topic,
      @PathVariable final int partitionId,
      @RequestParam(defaultValue = "0") final long offset,
      @RequestParam(defaultValue = "1048576") final int maxBytes,
      @RequestParam(defaultValue = "0") final int minBytes,
      @RequestParam(defaultValue = "0") final long maxWaitMs) {

    return fetchService
        .fetch(TopicGroups.name(topic), partitionId, offset, maxBytes, minBytes, maxWaitMs)
        .handle(
            (response, error) -> {
              if (error != null) {
                return ResponseEntity.internalServerError().<byte[]>build();
              }
              return ResponseEntity.ok(toClientFetchResponse(response));
            });
  }

  /** Same binary layout the client SDK parses: firstPos|lastPos|highWatermark|len|batchBytes. */
  private static byte[] toClientFetchResponse(final FetchResponse response) {
    final byte[] data = response.getData();
    return ByteBuffer.allocate(Long.BYTES * 3 + Integer.BYTES + data.length)
        .putLong(response.getFirstPosition())
        .putLong(response.getLastPosition())
        .putLong(response.getHighWatermark())
        .putInt(data.length)
        .put(data)
        .array();
  }

  private static String rootMessage(final Throwable error) {
    final var cause = error.getCause() != null ? error.getCause() : error;
    return cause.getMessage() != null ? cause.getMessage() : cause.toString();
  }
}
