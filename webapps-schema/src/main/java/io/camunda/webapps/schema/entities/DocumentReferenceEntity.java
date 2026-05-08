/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Objects;

public class DocumentReferenceEntity
    implements ExporterEntity<DocumentReferenceEntity>,
        PartitionedEntity<DocumentReferenceEntity>,
        TenantOwned {

  private String id;
  private Long variableKey;
  private String variableName;
  private Long scopeKey;
  private Long processInstanceKey;
  private Long processDefinitionKey;
  private String processDefinitionId;
  private Long rootProcessInstanceKey;
  private String tenantId = DEFAULT_TENANT_IDENTIFIER;
  private Long position;
  private int partitionId;
  private String documentId;
  private String storeId;
  private String fileName;
  private String contentType;
  private Long size;
  private String expiresAt;
  private String contentHash;
  private String customProperties;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public DocumentReferenceEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public Long getVariableKey() {
    return variableKey;
  }

  public DocumentReferenceEntity setVariableKey(final Long variableKey) {
    this.variableKey = variableKey;
    return this;
  }

  public String getVariableName() {
    return variableName;
  }

  public DocumentReferenceEntity setVariableName(final String variableName) {
    this.variableName = variableName;
    return this;
  }

  public Long getScopeKey() {
    return scopeKey;
  }

  public DocumentReferenceEntity setScopeKey(final Long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public DocumentReferenceEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public DocumentReferenceEntity setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public DocumentReferenceEntity setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public DocumentReferenceEntity setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public DocumentReferenceEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public DocumentReferenceEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public DocumentReferenceEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public String getDocumentId() {
    return documentId;
  }

  public DocumentReferenceEntity setDocumentId(final String documentId) {
    this.documentId = documentId;
    return this;
  }

  public String getStoreId() {
    return storeId;
  }

  public DocumentReferenceEntity setStoreId(final String storeId) {
    this.storeId = storeId;
    return this;
  }

  public String getFileName() {
    return fileName;
  }

  public DocumentReferenceEntity setFileName(final String fileName) {
    this.fileName = fileName;
    return this;
  }

  public String getContentType() {
    return contentType;
  }

  public DocumentReferenceEntity setContentType(final String contentType) {
    this.contentType = contentType;
    return this;
  }

  public Long getSize() {
    return size;
  }

  public DocumentReferenceEntity setSize(final Long size) {
    this.size = size;
    return this;
  }

  public String getExpiresAt() {
    return expiresAt;
  }

  public DocumentReferenceEntity setExpiresAt(final String expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  public String getContentHash() {
    return contentHash;
  }

  public DocumentReferenceEntity setContentHash(final String contentHash) {
    this.contentHash = contentHash;
    return this;
  }

  public String getCustomProperties() {
    return customProperties;
  }

  public DocumentReferenceEntity setCustomProperties(final String customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        variableKey,
        variableName,
        scopeKey,
        processInstanceKey,
        processDefinitionKey,
        processDefinitionId,
        rootProcessInstanceKey,
        tenantId,
        position,
        partitionId,
        documentId,
        storeId,
        fileName,
        contentType,
        size,
        expiresAt,
        contentHash,
        customProperties);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DocumentReferenceEntity that = (DocumentReferenceEntity) o;
    return partitionId == that.partitionId
        && Objects.equals(id, that.id)
        && Objects.equals(variableKey, that.variableKey)
        && Objects.equals(variableName, that.variableName)
        && Objects.equals(scopeKey, that.scopeKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(rootProcessInstanceKey, that.rootProcessInstanceKey)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(position, that.position)
        && Objects.equals(documentId, that.documentId)
        && Objects.equals(storeId, that.storeId)
        && Objects.equals(fileName, that.fileName)
        && Objects.equals(contentType, that.contentType)
        && Objects.equals(size, that.size)
        && Objects.equals(expiresAt, that.expiresAt)
        && Objects.equals(contentHash, that.contentHash)
        && Objects.equals(customProperties, that.customProperties);
  }

  @Override
  public String toString() {
    return "DocumentReferenceEntity{"
        + "id='"
        + id
        + '\''
        + ", variableKey="
        + variableKey
        + ", variableName='"
        + variableName
        + '\''
        + ", scopeKey="
        + scopeKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", processDefinitionId='"
        + processDefinitionId
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + ", documentId='"
        + documentId
        + '\''
        + ", storeId='"
        + storeId
        + '\''
        + '}';
  }
}
