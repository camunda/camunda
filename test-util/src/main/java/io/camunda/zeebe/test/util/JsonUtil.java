/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;

public final class JsonUtil {

  static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
      new TypeReference<>() {};

  static {
    JSON_MAPPER.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
  }

  public static void assertEquality(final String actualJson, final String expectedJson) {
    assertThat(asJsonNode(actualJson)).isEqualTo(asJsonNode(expectedJson));
  }

  public static boolean isEqual(final String actualJson, final String expectedJson) {
    return asJsonNode(actualJson).equals(asJsonNode(expectedJson));
  }

  private static JsonNode asJsonNode(final String json) {
    try {
      return JSON_MAPPER.readTree(json);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Map<String, Object> fromJsonAsMap(final String json) {
    try {
      return JSON_MAPPER.readValue(json, MAP_TYPE_REFERENCE);
    } catch (final IOException e) {
      throw new AssertionError(
          String.format("Failed to deserialize json '%s' to 'Map<String, Object>'", json), e);
    }
  }

  public static String toJson(final Object o) {
    try {
      return JSON_MAPPER.writeValueAsString(o);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
