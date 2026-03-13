/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.model.session;

import java.time.Instant;
import java.util.Map;

/** Represents a persistent web session's data for storage and retrieval. */
public record SessionData(
    String id,
    Instant creationTime,
    Instant lastAccessedTime,
    long maxInactiveIntervalInSeconds,
    Map<String, Object> attributes) {

  public SessionData {
    attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
  }
}
