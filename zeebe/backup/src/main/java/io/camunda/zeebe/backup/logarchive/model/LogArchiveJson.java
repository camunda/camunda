/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The one serializer for all log archive bucket objects. Its output is part of the bucket format
 * and is pinned by golden-file tests, so changing it is a format change.
 *
 * <p>Output is deterministic: properties are written in the order declared by
 * {@code @JsonPropertyOrder}, there is no pretty printing, numbers are JSON numbers and absent
 * optional parts are omitted (see each model record for its rules). Nothing in the output depends
 * on the wall clock or the environment. The output is UTF-8.
 *
 * <p>Reading ignores unknown properties, so a reader keeps working when a later format version adds
 * fields.
 *
 * <p>The {@link ObjectMapper} is configured once and is thread-safe, so one instance is shared.
 */
public final class LogArchiveJson {

  /** Format version written into every bucket object of this PoC. */
  public static final int FORMAT_VERSION = 1;

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .disable(SerializationFeature.INDENT_OUTPUT)
          .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private LogArchiveJson() {}

  /**
   * Serializes a bucket object to deterministic UTF-8 JSON.
   *
   * @throws UncheckedIOException if the object cannot be serialized
   */
  public static byte[] write(final Object value) {
    try {
      return MAPPER.writeValueAsBytes(value);
    } catch (final JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Parses a bucket object from UTF-8 JSON.
   *
   * @throws UncheckedIOException if the bytes are not a valid object of the given type
   */
  public static <T> T read(final byte[] json, final Class<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
