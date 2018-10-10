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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.cmd.InternalClientException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class ZeebeObjectMapper extends ObjectMapper {

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
      new TypeReference<Map<String, Object>>() {};

  public <T> T fromJson(String json, Class<T> typeClass) {
    try {
      return readValue(json, typeClass);
    } catch (IOException e) {
      throw new InternalClientException(
          String.format("Failed to deserialize json '%s' to class '%s'", json, typeClass), e);
    }
  }

  public Map<String, Object> fromJsonAsMap(String json) {
    try {
      return readValue(json, MAP_TYPE_REFERENCE);
    } catch (IOException e) {
      throw new InternalClientException(
          String.format("Failed to deserialize json '%s' to 'Map<String, Object>'", json), e);
    }
  }

  public String toJson(Object value) {
    try {
      return writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new InternalClientException(
          String.format("Failed to serialize object '%s' to json", value), e);
    }
  }

  public String validateJson(String propertyName, String jsonInput) {
    try {
      return readTree(jsonInput).toString();
    } catch (IOException e) {
      throw new InternalClientException(
          String.format(
              "Failed to validate json input '%s' for property '%s'", jsonInput, propertyName),
          e);
    }
  }

  public String validateJson(String propertyName, InputStream jsonInput) {
    try {
      return readTree(jsonInput).toString();
    } catch (IOException e) {
      throw new InternalClientException(
          String.format("Failed to validate json input stream for property '%s'", propertyName), e);
    }
  }
}
