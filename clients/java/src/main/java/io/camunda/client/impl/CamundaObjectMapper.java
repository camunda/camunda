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
package io.camunda.client.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.InternalClientException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public final class CamundaObjectMapper implements JsonMapper {

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
      new TypeReference<Map<String, Object>>() {};

  private static final TypeReference<Map<String, String>> STRING_MAP_TYPE_REFERENCE =
      new TypeReference<Map<String, String>>() {};

  private final ObjectMapper objectMapper;

  public CamundaObjectMapper() {
    this(new ObjectMapper());
  }

  public CamundaObjectMapper(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.objectMapper
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  @Override
  public <T> T fromJson(final String json, final Class<T> typeClass) {
    try {
      return objectMapper.readValue(json, typeClass);
    } catch (final IOException e) {
      throw new InternalClientException(
          String.format("Failed to deserialize json '%s' to class '%s'", json, typeClass), e);
    }
  }

  @Override
  public <T> T transform(final Object json, final Class<T> typeClass) {
    try {
      return objectMapper.convertValue(json, typeClass);
    } catch (final IllegalArgumentException e) {
      throw new InternalClientException(
          String.format("Failed to transform object '%s' to class '%s'", json, typeClass), e);
    }
  }

  @Override
  public Map<String, Object> fromJsonAsMap(final String json) {
    try {
      return objectMapper.readValue(json, MAP_TYPE_REFERENCE);
    } catch (final IOException e) {
      throw new InternalClientException(
          String.format("Failed to deserialize json '%s' to 'Map<String, Object>'", json), e);
    }
  }

  @Override
  public Map<String, String> fromJsonAsStringMap(final String json) {
    try {
      return objectMapper.readValue(json, STRING_MAP_TYPE_REFERENCE);
    } catch (final IOException e) {
      throw new InternalClientException(
          String.format("Failed to deserialize json '%s' to 'Map<String, String>'", json), e);
    }
  }

  @Override
  public String toJson(final Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (final JsonProcessingException e) {
      throw new InternalClientException(
          String.format("Failed to serialize object '%s' to json", value), e);
    }
  }

  @Override
  public String validateJson(final String propertyName, final String jsonInput) {
    try {
      return objectMapper.readTree(jsonInput).toString();
    } catch (final IOException e) {
      throw new InternalClientException(
          String.format(
              "Failed to validate json input '%s' for property '%s'", jsonInput, propertyName),
          e);
    }
  }

  @Override
  public String validateJson(final String propertyName, final InputStream jsonInput) {
    try {
      return objectMapper.readTree(jsonInput).toString();
    } catch (final IOException e) {
      throw new InternalClientException(
          String.format("Failed to validate json input stream for property '%s'", propertyName), e);
    }
  }
}
