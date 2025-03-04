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
import java.time.OffsetDateTime;
import java.util.Map;

public class DocumentMetadataImpl implements DocumentMetadata {

  private final io.camunda.client.protocol.rest.DocumentMetadata response;

  public DocumentMetadataImpl(final io.camunda.client.protocol.rest.DocumentMetadata response) {
    this.response = response;
  }

  @Override
  public String getContentType() {
    return response.getContentType();
  }

  @Override
  public OffsetDateTime getExpiresAt() {
    final String expiresAt = response.getExpiresAt();
    if (expiresAt == null) {
      return null;
    }
    try {
      return OffsetDateTime.parse(expiresAt);
    } catch (final Exception e) {
      throw new IllegalArgumentException(
          "Failed to parse expiresAt date: " + response.getExpiresAt(), e);
    }
  }

  @Override
  public Long getSize() {
    return response.getSize();
  }

  @Override
  public String getFileName() {
    return response.getFileName();
  }

  @Override
  public String getProcessDefinitionId() {
    return response.getProcessDefinitionId();
  }

  @Override
  public Long getProcessInstanceKey() {
    return response.getProcessInstanceKey();
  }

  @Override
  public Map<String, Object> getCustomProperties() {
    return response.getCustomProperties();
  }
}
