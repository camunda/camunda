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
package io.camunda.zeebe.client.impl.response;

import io.camunda.zeebe.client.api.response.DocumentMetadata;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class DocumentMetadataImpl implements DocumentMetadata {

  private static final String EXPIRES_AT =
      io.camunda.zeebe.client.protocol.rest.DocumentMetadata.JSON_PROPERTY_EXPIRES_AT;
  private static final String SIZE =
      io.camunda.zeebe.client.protocol.rest.DocumentMetadata.JSON_PROPERTY_SIZE;
  private static final String FILE_NAME =
      io.camunda.zeebe.client.protocol.rest.DocumentMetadata.JSON_PROPERTY_FILE_NAME;
  private static final String CONTENT_TYPE =
      io.camunda.zeebe.client.protocol.rest.DocumentMetadata.JSON_PROPERTY_CONTENT_TYPE;
  private final io.camunda.zeebe.client.protocol.rest.DocumentMetadata response;

  public DocumentMetadataImpl(
      final io.camunda.zeebe.client.protocol.rest.DocumentMetadata response) {
    this.response = response;
  }

  @Override
  public Map<String, Object> asMap() {
    final Map<String, Object> metadataMap = new HashMap<>(response);
    if (response.getExpiresAt() != null) {
      metadataMap.put(EXPIRES_AT, response.getExpiresAt());
    }
    if (response.getSize() != null) {
      metadataMap.put(SIZE, response.getSize());
    }
    if (response.getFileName() != null) {
      metadataMap.put(FILE_NAME, response.getFileName());
    }
    if (response.getContentType() != null) {
      metadataMap.put(CONTENT_TYPE, response.getContentType());
    }

    return metadataMap;
  }

  @Override
  public Object get(final String key) {
    return response.get(key);
  }

  @Override
  public String getContentType() {
    return getMetadataPropertyOrFallback(CONTENT_TYPE, response::getContentType);
  }

  @Override
  public ZonedDateTime getExpiresAt() {
    final String expiresAt = getMetadataPropertyOrFallback(EXPIRES_AT, response::getExpiresAt);
    if (expiresAt == null) {
      return null;
    }
    try {
      return ZonedDateTime.parse(expiresAt);
    } catch (final Exception e) {
      throw new IllegalArgumentException(
          "Failed to parse expiresAt date: " + response.getExpiresAt(), e);
    }
  }

  @Override
  public Long getSize() {
    final Object size = Optional.ofNullable(response.get(SIZE)).orElse(response.getSize());
    if (size == null) {
      return null;
    }
    if (size instanceof Integer) {
      return ((Integer) size).longValue();
    }
    if (size instanceof Long) {
      return (Long) size;
    }
    throw new IllegalArgumentException("Size is not a number: " + size);
  }

  @Override
  public String getFileName() {
    return getMetadataPropertyOrFallback(FILE_NAME, response::getFileName);
  }

  private <T> T getMetadataPropertyOrFallback(final String key, final Supplier<T> fallback) {
    final Object property = Optional.ofNullable(response.get(key)).orElse(fallback.get());
    if (property == null) {
      return null;
    }
    return (T) property;
  }
}
