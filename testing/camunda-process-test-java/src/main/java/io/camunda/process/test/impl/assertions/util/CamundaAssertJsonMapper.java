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

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.InternalClientException;

/**
 * Converts between JSON strings and the values that the assertions compare.
 *
 * <p>Every conversion goes through the client's {@link JsonMapper}, so a mapper customized by the
 * user applies to the assertions as well.
 *
 * <p>Values are represented as plain Java types ({@code Map}, {@code List}, {@code String}, {@code
 * Number}, {@code Boolean} and {@code null}) rather than a library-specific tree type. The {@link
 * JsonMapper} contract cannot produce such a tree, so requiring one would restrict the assertions
 * to mappers built on one specific JSON library.
 */
public class CamundaAssertJsonMapper {

  private final JsonMapper jsonMapper;

  public CamundaAssertJsonMapper(final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  /** Reads a JSON string into a value that can be compared against an expected value. */
  public Object readJson(final String value) {
    return readJson(value, Object.class);
  }

  public <T> T readJson(final String value, final Class<T> clazz) {
    return readJson(value, clazz, null);
  }

  public <T> T readJson(final String value, final Class<T> clazz, final T defaultValue) {
    if (value == null) {
      return defaultValue;
    }

    try {
      return jsonMapper.fromJson(value, clazz);
    } catch (final InternalClientException e) {
      throw new JsonMappingException(String.format("Failed to read JSON: '%s'", value), e);
    }
  }

  /**
   * Converts an expected value into the same representation that {@link #readJson(String)} produces
   * for an actual value, so that the two can be compared.
   */
  public Object toJsonValue(final Object value) {
    // Serialize to a JSON string and read it back to normalize numeric types (e.g. int vs long,
    // float vs double, decimal handling). This ensures the expected value is represented like the
    // actual value that is read from its JSON representation.
    return readJson(toJson(value), Object.class);
  }

  /** Renders a value as JSON, for example to describe an assertion failure. */
  public String toJson(final Object value) {
    try {
      return jsonMapper.toJson(value);
    } catch (final InternalClientException e) {
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
