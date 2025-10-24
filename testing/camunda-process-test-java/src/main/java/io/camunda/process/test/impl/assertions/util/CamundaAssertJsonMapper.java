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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.InternalClientException;

public class CamundaAssertJsonMapper {

  private final JsonMapper jsonMapper;
  private final io.camunda.zeebe.client.api.JsonMapper zeebeJsonMapper;

  public CamundaAssertJsonMapper(final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    zeebeJsonMapper = null;
  }

  public CamundaAssertJsonMapper(final io.camunda.zeebe.client.api.JsonMapper jsonMapper) {
    this.jsonMapper = null;
    zeebeJsonMapper = jsonMapper;
  }

  public JsonNode readJson(final String value) {
    return readJson(value, JsonNode.class, NullNode.getInstance());
  }

  public <T> T readJson(final String value, final Class<T> clazz) {
    return readJson(value, clazz, null);
  }

  public <T> T readJson(final String value, final Class<T> clazz, final T defaultValue) {
    if (value == null) {
      return defaultValue;
    }

    try {
      return read(value, clazz);
    } catch (final InternalClientException e) {
      throw new JsonMappingException(String.format("Failed to read JSON: '%s'", value), e);
    }
  }

  public JsonNode toJsonNode(final Object value) {
    try {
      return write(value);
    } catch (final InternalClientException e) {
      throw new JsonMappingException(
          String.format("Failed to transform value to JSON: '%s'", value), e);
    }
  }

  private <T> T read(final String value, final Class<T> clazz) {
    if (jsonMapper != null) {
      return jsonMapper.fromJson(value, clazz);
    } else {
      return zeebeJsonMapper.fromJson(value, clazz);
    }
  }

  private JsonNode write(final Object value) {
    if (jsonMapper != null) {
      return jsonMapper.transform(value, JsonNode.class);
    } else {
      return zeebeJsonMapper.fromJson(zeebeJsonMapper.toJson(value), JsonNode.class);
    }
  }

  public static class JsonMappingException extends RuntimeException {

    public JsonMappingException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
