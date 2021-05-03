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
package io.zeebe.client.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.api.JsonMapper;
import io.zeebe.client.api.command.InternalClientException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public final class ZeebeObjectMapper extends ObjectMapper implements JsonMapper {

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
      new TypeReference<Map<String, Object>>() {};

  private static final TypeReference<Map<String, String>> STRING_MAP_TYPE_REFERENCE =
      new TypeReference<Map<String, String>>() {};

  public ZeebeObjectMapper() {
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public <T> T fromJson(final String json, final Class<T> typeClass) {
    try {
      return readValue(json, typeClass);
    } catch (final IOException e) {
      throw new InternalClientException(
          String.format("Failed to deserialize json '%s' to class '%s'", json, typeClass), e);
    }
  }

  public Map<String, Object> fromJsonAsMap(final String json) {
    try {
      return readValue(json, MAP_TYPE_REFERENCE);
    } catch (final IOException e) {
      throw new InternalClientException(
          String.format("Failed to deserialize json '%s' to 'Map<String, Object>'", json), e);
    }
  }

  public Map<String, String> fromJsonAsStringMap(final String json) {
    try {
      return readValue(json, STRING_MAP_TYPE_REFERENCE);
    } catch (final IOException e) {
      throw new InternalClientException(
          String.format("Failed to deserialize json '%s' to 'Map<String, String>'", json), e);
    }
  }

  public String toJson(final Object value) {
    try {
      return writeValueAsString(value);
    } catch (final JsonProcessingException e) {
      throw new InternalClientException(
          String.format("Failed to serialize object '%s' to json", value), e);
    }
  }

  public String validateJson(final String propertyName, final String jsonInput) {
    try {
      return readTree(jsonInput).toString();
    } catch (final IOException e) {
      throw new InternalClientException(
          String.format(
              "Failed to validate json input '%s' for property '%s'", jsonInput, propertyName),
          e);
    }
  }

  public String validateJson(final String propertyName, final InputStream jsonInput) {
    try {
      return readTree(jsonInput).toString();
    } catch (final IOException e) {
      throw new InternalClientException(
          String.format("Failed to validate json input stream for property '%s'", propertyName), e);
    }
  }
}
