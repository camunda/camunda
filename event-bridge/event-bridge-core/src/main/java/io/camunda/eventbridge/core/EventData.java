/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.core;

import java.util.Objects;

/**
 * Wraps a raw binary event payload. Instances are immutable value types; the caller retains
 * ownership of the backing byte array and must not mutate it after handing the {@code EventData} to
 * an {@link EventDataBatch}.
 *
 * <p>Not thread-safe for mutation of the backing array; concurrent reads are safe after
 * construction because the object itself is immutable.
 */
public record EventData(byte[] body) {

  /**
   * Creates an {@code EventData} wrapping {@code body} by reference (no defensive copy).
   *
   * @param body the raw payload; must not be {@code null}
   * @throws NullPointerException if {@code body} is {@code null}
   */
  public EventData {
    Objects.requireNonNull(body, "body must not be null");
  }

  /** Returns the number of bytes in the payload. Zero-length payloads are structurally valid. */
  public int sizeInBytes() {
    return body.length;
  }
}
