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
package io.camunda.process.test.utils;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.InternalClientException;
import java.io.InputStream;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

/**
 * A {@link JsonMapper} built on Jackson 3, mirroring {@code CamundaJackson3ObjectMapper} from the
 * Spring Boot starter, which is the client's default JSON mapper on Spring Boot 4.
 *
 * <p>It is used to verify that the assertions rely only on the {@link JsonMapper} contract. That
 * contract has no notion of a JSON tree, so a mapper that is not built on Jackson 2 cannot produce
 * one — asking it for a {@code com.fasterxml.jackson.databind.JsonNode} fails here just like it
 * does for a user running on Spring Boot 4.
 */
public class Jackson3JsonMapper implements JsonMapper {

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
      new TypeReference<Map<String, Object>>() {};

  private static final TypeReference<Map<String, String>> STRING_MAP_TYPE_REFERENCE =
      new TypeReference<Map<String, String>>() {};

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .rebuild()
          .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .build();

  @Override
  public <T> T fromJson(final String json, final Class<T> typeClass) {
    try {
      return objectMapper.readValue(json, typeClass);
    } catch (final JacksonException e) {
      throw new InternalClientException(
          String.format("Failed to deserialize json '%s' to class '%s'", json, typeClass), e);
    }
  }

  @Override
  public Map<String, Object> fromJsonAsMap(final String json) {
    try {
      return objectMapper.readValue(json, MAP_TYPE_REFERENCE);
    } catch (final JacksonException e) {
      throw new InternalClientException(
          String.format("Failed to deserialize json '%s' to 'Map<String, Object>'", json), e);
    }
  }

  @Override
  public Map<String, String> fromJsonAsStringMap(final String json) {
    try {
      return objectMapper.readValue(json, STRING_MAP_TYPE_REFERENCE);
    } catch (final JacksonException e) {
      throw new InternalClientException(
          String.format("Failed to deserialize json '%s' to 'Map<String, String>'", json), e);
    }
  }

  @Override
  public String toJson(final Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (final JacksonException e) {
      throw new InternalClientException(
          String.format("Failed to serialize object '%s' to json", value), e);
    }
  }

  @Override
  public String validateJson(final String propertyName, final String jsonInput) {
    try {
      objectMapper.readTree(jsonInput);
      return jsonInput;
    } catch (final JacksonException e) {
      throw new InternalClientException(
          String.format("Failed to validate json input '%s' for property '%s'", jsonInput, e), e);
    }
  }

  @Override
  public String validateJson(final String propertyName, final InputStream jsonInput) {
    try {
      return objectMapper.readTree(jsonInput).toString();
    } catch (final JacksonException e) {
      throw new InternalClientException(
          String.format("Failed to validate json input stream for property '%s'", propertyName), e);
    }
  }
}
