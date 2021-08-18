/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson.record;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.protocol.record.JsonSerializable;
import java.io.UncheckedIOException;

/**
 * Overshadow {@link JsonSerializable} to prevent toJson from being detected as an attribute; it
 * should instead be re-computed every time.
 */
interface DefaultJsonSerializable extends JsonSerializable {
  /** @deprecated use your own {@link ObjectMapper} instead of relying on this one */
  @Deprecated(since = "1.2.0")
  ObjectMapper JSON_WRITER = new ObjectMapper();

  /** @deprecated use a {@link ObjectMapper} directly instead of calling this method */
  @Override
  @Deprecated(since = "1.2.0")
  default String toJson() {
    try {
      return JSON_WRITER.writeValueAsString(this);
    } catch (final JsonProcessingException e) {
      throw new UncheckedIOException(String.format("Failed to serialize %s to JSON", this), e);
    }
  }
}
