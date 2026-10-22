/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import io.camunda.zeebe.eventplane.store.ObjectNames;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * What a retention run keeps of a log, as maintenance writes it at {@link
 * ObjectNames#retentionMarker} before it deletes anything; the latest by ULID applies:
 *
 * <pre>{@code
 * {"version":1,"history":"…","firstIndex":1096704,"firstPosition":1052032,"createdAt":"2026-10-20T12:00:00Z"}
 * }</pre>
 *
 * @param history the then current history
 * @param firstIndex the first entry that is kept
 * @param firstPosition the position of the first record that is kept
 * @param createdAt when the run wrote it
 */
public record RetentionMarker(
    UUID history, long firstIndex, long firstPosition, Instant createdAt) {

  public RetentionMarker {
    if (firstIndex < 1 || firstPosition < 1) {
      throw new IllegalArgumentException(
          "Expected a retention marker's first index and position to be 1 or more, but got %d and %d"
              .formatted(firstIndex, firstPosition));
    }
  }

  /** The marker as JSON. */
  public byte[] toJson() {
    return JsonFields.write(
        JsonFields.versioned()
            .put("history", history.toString())
            .put("firstIndex", firstIndex)
            .put("firstPosition", firstPosition)
            .put("createdAt", createdAt.toString()));
  }

  /**
   * Reads a marker. Fields that it doesn't know are skipped.
   *
   * @throws InvalidObjectJsonException if the JSON isn't that of a retention marker
   * @throws IOException if the bytes aren't JSON
   */
  public static RetentionMarker fromJson(final byte[] json) throws IOException {
    final var fields = JsonFields.parse(json);
    final var history = fields.uuid("history");
    final long firstIndex = fields.longValue("firstIndex");
    final long firstPosition = fields.longValue("firstPosition");
    final var createdAt = fields.instant("createdAt");
    try {
      return new RetentionMarker(history, firstIndex, firstPosition, createdAt);
    } catch (final IllegalArgumentException e) {
      throw new InvalidObjectJsonException(
          "the marker", "a valid retention marker", JsonFields.shown(json), e);
    }
  }
}
