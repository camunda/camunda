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
import java.util.UUID;

/**
 * A group's cursor in a log, which its holder rewrites at {@link ObjectNames#cursor} with a plain
 * PUT:
 *
 * <pre>{@code
 * {"version":1,"history":"…","index":1096704,"position":1052032,"worker":"w-01JD…"}
 * }</pre>
 *
 * @param cursor how far the group got
 * @param worker the worker that wrote it
 */
public record GroupCursor(Cursor cursor, String worker) {

  public GroupCursor {
    if (worker.isEmpty()) {
      throw new IllegalArgumentException("Expected a worker id, but got an empty one");
    }
  }

  /** The cursor as JSON. */
  public byte[] toJson() {
    return JsonFields.write(
        JsonFields.versioned()
            .put("history", cursor.history().toString())
            .put("index", cursor.index())
            .put("position", cursor.position())
            .put("worker", worker));
  }

  /**
   * Reads a cursor. Fields that it doesn't know are skipped.
   *
   * @throws InvalidObjectJsonException if the JSON isn't that of a cursor
   * @throws IOException if the bytes aren't JSON
   */
  public static GroupCursor fromJson(final byte[] json) throws IOException {
    final var fields = JsonFields.parse(json);
    final UUID history = fields.uuid("history");
    final long index = fields.longValue("index");
    final long position = fields.longValue("position");
    final var worker = fields.text("worker");
    try {
      return new GroupCursor(new Cursor(history, index, position), worker);
    } catch (final IllegalArgumentException e) {
      throw new InvalidObjectJsonException(
          "the cursor", "a valid cursor", JsonFields.shown(json), e);
    }
  }
}
