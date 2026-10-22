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

/**
 * A worker's heartbeat, which it rewrites at {@link ObjectNames#worker} with the beat counting up:
 *
 * <pre>{@code
 * {"version":1,"worker":"w-01JD…","beat":42}
 * }</pre>
 *
 * Others count the worker as live while the beat keeps changing, timed on their own clocks.
 *
 * @param worker the worker's id
 * @param beat counts up with every write
 */
public record Heartbeat(String worker, long beat) {

  public Heartbeat {
    if (worker.isEmpty()) {
      throw new IllegalArgumentException("Expected a worker id, but got an empty one");
    }
  }

  /** The heartbeat as JSON. */
  public byte[] toJson() {
    return JsonFields.write(JsonFields.versioned().put("worker", worker).put("beat", beat));
  }

  /**
   * Reads a heartbeat. Fields that it doesn't know are skipped.
   *
   * @throws InvalidObjectJsonException if the JSON isn't that of a heartbeat
   * @throws IOException if the bytes aren't JSON
   */
  public static Heartbeat fromJson(final byte[] json) throws IOException {
    final var fields = JsonFields.parse(json);
    return new Heartbeat(fields.text("worker"), fields.longValue("beat"));
  }
}
