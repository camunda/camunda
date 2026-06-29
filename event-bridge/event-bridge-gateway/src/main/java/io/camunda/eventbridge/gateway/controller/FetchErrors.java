/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

/** Helpers for classifying broker fetch/poll errors that cross the broker transport as messages. */
final class FetchErrors {

  /**
   * Error code used both as the broker-side marker (in the {@code IllegalArgumentException} message
   * raised by {@code EventStreamFetcher}) and as the client-facing error code.
   */
  static final String OFFSET_OUT_OF_RANGE = "OFFSET_OUT_OF_RANGE";

  private FetchErrors() {}

  /**
   * Whether the error chain reports an out-of-range offset — the requested offset is below the
   * partition's earliest retained record (or otherwise not in range). This is a client error (reset
   * the offset and retry), not a server fault, so it maps to {@code 416} rather than {@code 500}.
   * The broker raises it as an {@code IllegalArgumentException} whose message crosses the
   * transport, so we match on the message marker.
   */
  static boolean isOffsetOutOfRange(final Throwable error) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      final var message = t.getMessage();
      if (message != null && message.contains(OFFSET_OUT_OF_RANGE)) {
        return true;
      }
    }
    return false;
  }
}
