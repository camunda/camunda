/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.client.util;

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
      new TypeReference<Map<String, Object>>() {};

  static {
    JSON_MAPPER.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
  }

  public static void assertEquality(final String actualJson, final String expectedJson) {
    assertThat(asJsonNode(actualJson)).isEqualTo(asJsonNode(expectedJson));
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
