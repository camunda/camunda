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
package io.camunda.process.test.impl.judge;

import io.camunda.client.api.response.DocumentMetadata;
import io.camunda.client.api.response.DocumentReferenceResponse;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Local DTO for deserializing Camunda document references from variable JSON. Avoids a compile-time
 * dependency on {@code io.camunda.client.impl.*} types. Unknown JSON fields (e.g. {@code
 * camunda.document.type}) are silently ignored via the ObjectMapper configuration in {@link
 * DocumentReferenceResolver}.
 */
final class DocumentReferenceDto implements DocumentReferenceResponse {

  private String documentId;
  private String storeId;
  private String contentHash;
  private MetadataDto metadata;

  public void setDocumentId(final String documentId) {
    this.documentId = documentId;
  }

  public void setStoreId(final String storeId) {
    this.storeId = storeId;
  }

  public void setContentHash(final String contentHash) {
    this.contentHash = contentHash;
  }

  public void setMetadata(final MetadataDto metadata) {
    this.metadata = metadata;
  }

  @Override
  public String getDocumentId() {
    return documentId;
  }

  @Override
  public String getStoreId() {
    return storeId;
  }

  @Override
  public String getContentHash() {
    return contentHash;
  }

  @Override
  public DocumentMetadata getMetadata() {
    return metadata;
  }

  static final class MetadataDto implements DocumentMetadata {

    private String contentType;
    private String fileName;
    private String expiresAt;
    private Long size;
    private String processDefinitionId;
    private String processInstanceKey;
    private Map<String, Object> customProperties;

    public void setContentType(final String contentType) {
      this.contentType = contentType;
    }

    public void setFileName(final String fileName) {
      this.fileName = fileName;
    }

    public void setExpiresAt(final String expiresAt) {
      this.expiresAt = expiresAt;
    }

    public void setSize(final Long size) {
      this.size = size;
    }

    public void setProcessDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
    }

    public void setProcessInstanceKey(final String processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
    }

    public void setCustomProperties(final Map<String, Object> customProperties) {
      this.customProperties = customProperties;
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public OffsetDateTime getExpiresAt() {
      return expiresAt == null ? null : OffsetDateTime.parse(expiresAt);
    }

    @Override
    public Long getSize() {
      return size;
    }

    @Override
    public String getFileName() {
      return fileName;
    }

    @Override
    public String getProcessDefinitionId() {
      return processDefinitionId;
    }

    @Override
    public Long getProcessInstanceKey() {
      if (processInstanceKey == null || processInstanceKey.isEmpty()) {
        return null;
      }
      try {
        return Long.parseLong(processInstanceKey);
      } catch (final NumberFormatException e) {
        return null;
      }
    }

    @Override
    public Map<String, Object> getCustomProperties() {
      return customProperties;
    }
  }
}
