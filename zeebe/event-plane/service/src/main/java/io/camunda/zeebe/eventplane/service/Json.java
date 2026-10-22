/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.UncheckedIOException;

/** The service's JSON, with one mapper for all of it. */
final class Json {

  private static final ObjectMapper MAPPER = JsonMapper.builder().build();

  private Json() {}

  /** The JSON of a value, such as a record. */
  static byte[] write(final Object value) {
    try {
      return MAPPER.writeValueAsBytes(value);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Reads a JSON document.
   *
   * @throws IOException if the bytes aren't JSON
   */
  static JsonNode read(final byte[] json) throws IOException {
    return MAPPER.readTree(json);
  }

  /** Whether the text is a JSON object. */
  static boolean isObject(final String json) {
    try {
      return MAPPER.readTree(json).isObject();
    } catch (final IOException e) {
      return false;
    }
  }
}
