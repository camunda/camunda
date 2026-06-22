/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.mapper.ResponseMapper;
import io.camunda.eventbridge.service.FetchService;
import io.camunda.eventbridge.service.PublishService;
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

/** Handles event publish and fetch requests under {@code /v1/events}. */
@RestController
@RequestMapping("/v1/events")
public class PublishController {

  private final PublishService publishService;
  private final FetchService fetchService;
  private final ResponseMapper responseMapper;

  public PublishController(
      final PublishService publishService,
      final FetchService fetchService,
      final ResponseMapper responseMapper) {
    this.publishService = publishService;
    this.fetchService = fetchService;
    this.responseMapper = responseMapper;
  }

  /**
   * Reads complete batches from a partition (zero-copy on the broker egress). Routes to the
   * partition leader over the broker transport and returns the binary fetch response format:
   *
   * <pre>firstPosition(8) | lastPosition(8) | highWatermark(8) | dataLength(4) | batchBytes</pre>
   */
  @GetMapping(value = "/{partitionId}/fetch", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public CompletableFuture<ResponseEntity<byte[]>> fetch(
      @PathVariable final int partitionId,
      @RequestParam(defaultValue = "0") final long offset,
      @RequestParam(defaultValue = "1048576") final int maxBytes,
      @RequestParam(defaultValue = "0") final int minBytes,
      @RequestParam(defaultValue = "0") final long maxWaitMs) {

    return fetchService
        .fetch(partitionId, offset, maxBytes, minBytes, maxWaitMs)
        .handle(
            (response, error) -> {
              if (error != null) {
                return ResponseEntity.internalServerError().<byte[]>build();
              }
              return ResponseEntity.ok(toClientFetchResponse(response));
            });
  }

  /**
   * Serialises the broker {@code FetchResponse} into the binary layout the client SDK parses:
   *
   * <pre>firstPosition(8) | lastPosition(8) | highWatermark(8) | dataLength(4) | batchBytes</pre>
   *
   * (big-endian, matching the client's {@code FetchResult} parser).
   */
  private static byte[] toClientFetchResponse(
      final io.camunda.eventbridge.protocol.request.FetchResponse response) {
    final byte[] data = response.getData();
    return java.nio.ByteBuffer.allocate(Long.BYTES * 3 + Integer.BYTES + data.length)
        .putLong(response.getFirstPosition())
        .putLong(response.getLastPosition())
        .putLong(response.getHighWatermark())
        .putInt(data.length)
        .put(data)
        .array();
  }

  @PostMapping(
      value = "/{partitionId}",
      consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> publish(
      @PathVariable final int partitionId, @RequestBody final byte[] body) {

    return publishService
        .publish(partitionId, body)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return ResponseEntity.internalServerError()
                    .body((Object) ("Publish failed: " + rootMessage(error)));
              }
              final var response = responseMapper.toPublishBatchResponse(res);
              return ResponseEntity.ok((Object) response);
            });
  }

  private static String rootMessage(final Throwable error) {
    final var cause = error.getCause() != null ? error.getCause() : error;
    return cause.getMessage() != null ? cause.getMessage() : cause.toString();
  }
}
