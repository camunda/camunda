/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Reads the JSON that workers write into the store, as in the contract's "Consumer group objects":
 * a heartbeat and a cursor. Fields that the service doesn't know are skipped.
 */
final class GroupObjects {

  private GroupObjects() {}

  /**
   * Reads a heartbeat.
   *
   * @throws InvalidGroupObjectException if the bytes aren't the JSON of a heartbeat
   */
  static Heartbeat heartbeat(final String key, final byte[] json) {
    final var object = object(key, json);
    return new Heartbeat(text(key, object, "worker"), number(key, object, "beat"));
  }

  /**
   * Reads a cursor.
   *
   * @throws InvalidGroupObjectException if the bytes aren't the JSON of a cursor
   */
  static Cursor cursor(final String key, final byte[] json) {
    final var object = object(key, json);
    final var history = text(key, object, "history");
    final UUID uuid;
    try {
      uuid = UUID.fromString(history);
    } catch (final IllegalArgumentException e) {
      throw new InvalidGroupObjectException(key, "a history that is a UUID", "'" + history + "'");
    }
    final var worker = object.get("worker");
    return new Cursor(
        uuid,
        number(key, object, "index"),
        number(key, object, "position"),
        worker != null && worker.isTextual() ? worker.asText() : null);
  }

  private static JsonNode object(final String key, final byte[] json) {
    final JsonNode node;
    try {
      node = Json.read(json);
    } catch (final IOException e) {
      throw new InvalidGroupObjectException(key, "JSON", firstLine(e));
    }
    if (!node.isObject()) {
      throw new InvalidGroupObjectException(key, "a JSON object", node.getNodeType().toString());
    }
    return node;
  }

  private static String text(final String key, final JsonNode object, final String field) {
    final var value = object.get(field);
    if (value == null || !value.isTextual()) {
      throw new InvalidGroupObjectException(key, "a text field '" + field + "'", describe(value));
    }
    return value.asText();
  }

  private static long number(final String key, final JsonNode object, final String field) {
    final var value = object.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
      throw new InvalidGroupObjectException(
          key, "an integer field '" + field + "'", describe(value));
    }
    return value.asLong();
  }

  private static String describe(@Nullable final JsonNode value) {
    return value == null ? "none" : value.toString();
  }

  private static String firstLine(final Exception e) {
    return String.valueOf(e.getMessage()).lines().findFirst().orElse(e.getClass().getSimpleName());
  }

  /** A worker's heartbeat: {@code {"version":1,"worker":"w-…","beat":42}}. */
  record Heartbeat(String worker, long beat) {}

  /**
   * A cursor: {@code {"version":1,"history":"…","index":1,"position":1,"worker":"w-…"}}. The worker
   * is optional.
   */
  record Cursor(UUID history, long index, long position, @Nullable String worker) {}
}
