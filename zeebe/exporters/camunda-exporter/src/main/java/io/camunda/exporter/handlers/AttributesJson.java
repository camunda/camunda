/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Serializes user and group attributes into the JSON string stored in the (unindexed) field. */
final class AttributesJson {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private AttributesJson() {}

  static @Nullable String serialize(final @Nullable Map<String, Object> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(attributes);
    } catch (final JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize attributes", e);
    }
  }
}
