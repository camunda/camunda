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
package io.camunda.process.test.impl.assertions.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

/** Maps JSON values such as variables or decision outputs */
public class AssertionJsonMapper {
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  public static JsonNode readJson(final String value) {
    return readJson(value, JsonNode.class, NullNode.getInstance());
  }

  public static <T> T readJson(final String value, final Class<T> clazz) {
    return readJson(value, clazz, null);
  }

  public static <T> T readJson(final String value, final Class<T> clazz, final T defaultValue) {
    if (value == null) {
      return defaultValue;
    }

    try {
      return JSON_MAPPER.readValue(value, clazz);
    } catch (final JsonProcessingException e) {
      throw new JsonMappingException(String.format("Failed to read JSON: '%s'", value), e);
    }
  }

  public static JsonNode toJson(final Object value) {
    try {
      return JSON_MAPPER.convertValue(value, JsonNode.class);
    } catch (final IllegalArgumentException e) {
      throw new JsonMappingException(
          String.format("Failed to transform value to JSON: '%s'", value), e);
    }
  }

  public static class JsonMappingException extends RuntimeException {

    public JsonMappingException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
