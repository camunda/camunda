/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

public record Lease(String taskId, long timestamp, NodeInstance nodeInstance) {
  public String toJson(final ObjectMapper objectMapper) {
    try {
      return objectMapper.writeValueAsString(this);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public byte[] toJsonBytes(final ObjectMapper objectMapper) {
    try {
      return objectMapper.writeValueAsBytes(this);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static Lease fromJson(final ObjectMapper objectMapper, final String json) {
    try {
      return objectMapper.readValue(json, Lease.class);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static Lease fromJsonBytes(final ObjectMapper objectMapper, final byte[] json)
      throws IOException {
    return objectMapper.readValue(json, Lease.class);
  }

  public boolean isStillValid(final long now, final Duration leaseDuration) {
    return timestamp + leaseDuration.toMillis() > now;
  }

  public Lease renew(final long now, final Duration leaseDuration) {
    if (!isStillValid(now, leaseDuration)) {
      throw new IllegalStateException(
          "Lease is not valid anymore("
              + Instant.ofEpochMilli(now)
              + "), it expired at "
              + Instant.ofEpochMilli(timestamp));
    }
    final var millis = leaseDuration.toMillis();
    return new Lease(taskId, now + millis, nodeInstance);
  }
}
