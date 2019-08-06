/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;

public class JsonUtil {

  static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
      new TypeReference<Map<String, Object>>() {};

  static {
    JSON_MAPPER.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
  }

  public static void assertEquality(String actualJson, String expectedJson) {
    assertThat(asJsonNode(actualJson)).isEqualTo(asJsonNode(expectedJson));
  }

  private static JsonNode asJsonNode(String json) {
    try {
      return JSON_MAPPER.readTree(json);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Map<String, Object> fromJsonAsMap(String json) {
    try {
      return JSON_MAPPER.readValue(json, MAP_TYPE_REFERENCE);
    } catch (IOException e) {
      throw new AssertionError(
          String.format("Failed to deserialize json '%s' to 'Map<String, Object>'", json), e);
    }
  }

  public static String toJson(Object o) {
    try {
      return JSON_MAPPER.writeValueAsString(o);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
