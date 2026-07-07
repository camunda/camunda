/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import static io.camunda.eventbridge.gateway.controller.GatewayResponses.JSON;
import static io.camunda.eventbridge.gateway.controller.GatewayResponses.PROTOBUF;

import com.google.protobuf.ByteString;
import io.camunda.eventbridge.api.proto.FetchEntry;
import io.camunda.eventbridge.api.proto.FetchResponseJson;
import io.camunda.eventbridge.api.proto.PublishRequest;
import io.camunda.eventbridge.api.proto.PublishResponse;
import io.camunda.eventbridge.batch.BatchBuilder;
import io.camunda.eventbridge.batch.BatchReader;
import io.camunda.eventbridge.core.topic.TopicGroups;
import io.camunda.eventbridge.protocol.request.FetchResponse;
import io.camunda.eventbridge.protocol.request.PublishBatchResponse;
import io.camunda.eventbridge.service.FetchService;
import io.camunda.eventbridge.service.PublishService;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
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
 * topic's own Raft group ({@code event-bridge-topic-<name>}).
 *
 * <p>Two representations coexist per verb:
 *
 * <ul>
 *   <li><b>Raw batch</b> — {@code application/octet-stream}: the client SDK path, carrying a
 *       pre-encoded batch (publish) or the compact fetch layout (fetch). The bulk record payloads
 *       stay in the purpose-built batch codec, which is why this path is <em>not</em> routed
 *       through protobuf.
 *   <li><b>Negotiated</b> — {@code application/json} / {@code application/x-protobuf}: the
 *       human-friendly path using generated {@link PublishRequest}/{@link PublishResponse} and
 *       {@link FetchResponseJson} messages, where the gateway encodes/decodes the batch itself.
 * </ul>
 */
@RestController
@RequestMapping("/v1/topics")
public class TopicDataController {

  private final PublishService publishService;
  private final FetchService fetchService;

  public TopicDataController(final PublishService publishService, final FetchService fetchService) {
    this.publishService = publishService;
    this.fetchService = fetchService;
  }

  // -------------------------------------------------------------------------
  // Publish

  /**
   * Raw-batch publish (client SDK): the body is a pre-encoded batch. Responds with the assigned log
   * positions, negotiated between JSON and protobuf so the SDK can request the binary
   * representation.
   */
  @PostMapping(
      value = "/{topic}/partitions/{partitionId}/records",
      consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
      produces = {JSON, PROTOBUF})
  public CompletableFuture<ResponseEntity<?>> publish(
      @PathVariable final String topic,
      @PathVariable final int partitionId,
      @RequestBody final byte[] body) {
    return publishBatch(topic, partitionId, body);
  }

  /**
   * Negotiated publish (humans/Postman): the body is a {@link PublishRequest} of entries; the
   * gateway encodes them into a batch via {@link BatchBuilder} before publishing.
   */
  @PostMapping(
      value = "/{topic}/partitions/{partitionId}/records",
      consumes = {JSON, PROTOBUF},
      produces = {JSON, PROTOBUF})
  public CompletableFuture<ResponseEntity<?>> publishEntries(
      @PathVariable final String topic,
      @PathVariable final int partitionId,
      @RequestBody final PublishRequest request) {
    final var builder = new BatchBuilder();
    request
        .getEntriesList()
        .forEach(
            entry -> builder.add(entry.getKey().toByteArray(), entry.getValue().toByteArray()));
    return publishBatch(topic, partitionId, builder.build());
  }

  private CompletableFuture<ResponseEntity<?>> publishBatch(
      final String topic, final int partitionId, final byte[] batch) {
    return publishService
        .publish(TopicGroups.name(topic), partitionId, batch)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return ResponseEntity.internalServerError()
                    .body("Publish failed: " + rootMessage(error));
              }
              return ResponseEntity.ok(toPublishResponse(res));
            });
  }

  private static PublishResponse toPublishResponse(final PublishBatchResponse res) {
    return PublishResponse.newBuilder()
        .addLogPositions(res.getFirstPosition())
        .addLogPositions(res.getLastPosition())
        .build();
  }

  // -------------------------------------------------------------------------
  // Fetch

  /** Raw-batch fetch (client SDK): compact binary layout the SDK parses directly. */
  @GetMapping(
      value = "/{topic}/partitions/{partitionId}/records",
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
                // A from-the-start/below-earliest offset is a client error (reset + retry), not a
                // server fault — surface it as 416 so the client can reset rather than treat it as
                // a transient 500.
                if (FetchErrors.isOffsetOutOfRange(error)) {
                  return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                      .<byte[]>build();
                }
                return ResponseEntity.internalServerError().<byte[]>build();
              }
              return ResponseEntity.ok(toClientFetchResponse(response));
            });
  }

  /**
   * Negotiated fetch (humans/Postman): decodes the batch server-side into a {@link
   * FetchResponseJson} of entries, negotiated between JSON and protobuf.
   */
  @GetMapping(
      value = "/{topic}/partitions/{partitionId}/records",
      produces = {JSON, PROTOBUF})
  public CompletableFuture<ResponseEntity<?>> fetchEntries(
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
                if (FetchErrors.isOffsetOutOfRange(error)) {
                  return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
                }
                return ResponseEntity.internalServerError().build();
              }
              return ResponseEntity.ok(toFetchResponseJson(response, offset));
            });
  }

  /**
   * Same binary layout the client SDK parses: firstPos|lastPos|highWatermark|len|batchBytes. The
   * single allocation here is the gateway's only copy of the batch bytes on this path: it copies
   * straight from the transport buffer that {@link FetchResponse} windows into.
   */
  private static byte[] toClientFetchResponse(final FetchResponse response) {
    final int dataLength = response.getDataLength();
    final byte[] frame = new byte[Long.BYTES * 3 + Integer.BYTES + dataLength];
    final var buffer = ByteBuffer.wrap(frame);
    buffer
        .putLong(response.getFirstPosition())
        .putLong(response.getLastPosition())
        .putLong(response.getHighWatermark())
        .putInt(dataLength);
    response.getData().getBytes(0, frame, buffer.position(), dataLength);
    return frame;
  }

  /** Decodes the fetched batch into individual entries at or after {@code offset}. */
  private static FetchResponseJson toFetchResponseJson(
      final FetchResponse response, final long offset) {
    final byte[] data = new byte[response.getDataLength()];
    response.getData().getBytes(0, data, 0, data.length);
    final var builder =
        FetchResponseJson.newBuilder()
            .setFirstBatchPosition(response.getFirstPosition())
            .setLastBatchPosition(response.getLastPosition())
            .setHighWatermark(response.getHighWatermark());
    for (final BatchReader.Entry entry : BatchReader.read(data, 0, data.length, offset)) {
      builder.addEntries(
          FetchEntry.newBuilder()
              .setPosition(entry.position())
              .setKey(ByteString.copyFrom(entry.key()))
              .setValue(ByteString.copyFrom(entry.value()))
              .build());
    }
    return builder.build();
  }

  private static String rootMessage(final Throwable error) {
    final var cause = error.getCause() != null ? error.getCause() : error;
    return cause.getMessage() != null ? cause.getMessage() : cause.toString();
  }
}
