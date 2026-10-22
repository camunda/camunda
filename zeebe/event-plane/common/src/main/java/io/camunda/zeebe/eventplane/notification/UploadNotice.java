/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.camunda.zeebe.eventplane.segment.LogId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * What a publisher tells the service after it uploaded a segment, and the service tells the workers
 * that subscribed to the segment's log, as JSON:
 *
 * <pre>{@code
 * {"clusterId":"c","tenantId":"default","partition":3,"history":"…","key":"c/default/3/…seg",
 *  "firstIndex":1,"lastIndex":40,"firstPosition":1,"lastPosition":38,"size":18432}
 * }</pre>
 *
 * Publishers {@code POST} it to the service's {@code /v1/uploads}, and workers get it as the data
 * of an {@code upload} event of {@code GET /v1/notifications}. Delivery is best effort: a worker
 * that misses one finds the segment by listing.
 *
 * @param clusterId the cluster of the log
 * @param tenantId the physical tenant of the log
 * @param partition the partition of the log
 * @param history the history whose journal the segment holds
 * @param key the segment's key in the store
 * @param firstIndex the Raft index of the segment's first entry
 * @param lastIndex the Raft index of its last entry
 * @param firstPosition the position of the first record in the segment
 * @param lastPosition the position of the last record in the segment
 * @param size the segment's size in bytes
 */
public record UploadNotice(
    String clusterId,
    String tenantId,
    int partition,
    UUID history,
    String key,
    long firstIndex,
    long lastIndex,
    long firstPosition,
    long lastPosition,
    long size) {

  private static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .build();

  public UploadNotice {
    if (clusterId.isBlank() || tenantId.isBlank() || key.isBlank()) {
      throw new IllegalArgumentException(
          "Expected a notice to name its cluster, tenant and key, but got '%s', '%s' and '%s'"
              .formatted(clusterId, tenantId, key));
    }
    if (partition < 1) {
      throw new IllegalArgumentException("Expected a partition of 1 or more, but got " + partition);
    }
    if (firstIndex < 0 || lastIndex < firstIndex) {
      throw new IllegalArgumentException(
          "Expected a first index of 0 or more and a last index at or after it, but got %d and %d"
              .formatted(firstIndex, lastIndex));
    }
    if (lastPosition < firstPosition) {
      throw new IllegalArgumentException(
          "Expected a last position at or after the first, but got %d and %d"
              .formatted(firstPosition, lastPosition));
    }
    if (size < 0) {
      throw new IllegalArgumentException("Expected a size of 0 or more, but got " + size);
    }
  }

  /** The log of the segment: its cluster, physical tenant and partition. */
  public LogId log() {
    return new LogId(clusterId, tenantId, partition);
  }

  /** The notice as JSON, always the same bytes for the same notice. */
  public byte[] toJson() {
    try {
      return MAPPER.writeValueAsBytes(this);
    } catch (final JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Reads a notice. Fields that it doesn't know are skipped.
   *
   * @throws IOException if the bytes aren't the JSON of a valid notice
   */
  public static UploadNotice fromJson(final byte[] json) throws IOException {
    return MAPPER.readValue(json, UploadNotice.class);
  }

  /**
   * Reads a notice, such as the data of a server-sent event.
   *
   * @throws IOException if the text isn't the JSON of a valid notice
   */
  public static UploadNotice fromJson(final String json) throws IOException {
    return MAPPER.readValue(json, UploadNotice.class);
  }
}
