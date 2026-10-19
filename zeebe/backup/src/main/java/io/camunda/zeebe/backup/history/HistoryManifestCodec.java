/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.history;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.jspecify.annotations.NullMarked;

/** Versioned JSON contract shared with the Event Plane, independent of its segment format. */
@NullMarked
public final class HistoryManifestCodec {
  private final JsonMapper mapper =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .build();

  public byte[] encode(final HistoryManifest manifest) {
    try {
      return mapper.writeValueAsBytes(manifest);
    } catch (final IOException e) {
      throw new UncheckedIOException("Cannot encode history manifest", e);
    }
  }

  public HistoryManifest decode(final byte[] bytes) {
    try {
      return mapper.readValue(bytes, HistoryManifest.class);
    } catch (final IOException e) {
      throw new UncheckedIOException("Cannot decode history manifest", e);
    }
  }
}
