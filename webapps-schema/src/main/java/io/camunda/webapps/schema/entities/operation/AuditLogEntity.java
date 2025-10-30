/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operation;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;

public class AuditLogEntity extends AbstractExporterEntity<AuditLogEntity> {

  // this can be the value type
  private String entityType;
  // this is the unique identifier (key) of the entity that was affected by the operation
  private String entityKey;
  // this can be generated from the record intent
  private String operationType;
  // this is optional, provided by Operate to correlate Zeebe events to Operate operations
  private String batchOperationKey;
  // the time when the operation was performed, or started for batch operations
  private Long createTimestamp;
  // the time when the operation was completed, for batch operations
  private Long completeTimestamp;
  // this can be the value type
  private String identityEntityType;
  // the user that performed the operation
  private String identityEntityId;
  // marks if the operations was successful or failed
  private Short operationStatus;
  // details
  private String details;
  // the explanation on why the operation was performed
  private String note;
  // the tena
  private String tenantId;

  public String getOperationType() {
    return operationType;
  }

  public AuditLogEntity setOperationType(final String operationType) {
    this.operationType = operationType;
    return this;
  }

  public String getBatchOperationKey() {
    return batchOperationKey;
  }

  public AuditLogEntity setBatchOperationKey(final String batchOperationKey) {
    this.batchOperationKey = batchOperationKey;
    return this;
  }

  public String getEntityType() {
    return entityType;
  }

  public AuditLogEntity setEntityType(final String entityType) {
    this.entityType = entityType;
    return this;
  }

  public String getEntityKey() {
    return entityKey;
  }

  public AuditLogEntity setEntityKey(final String entityKey) {
    this.entityKey = entityKey;
    return this;
  }

  public Long getCreateTimestamp() {
    return createTimestamp;
  }

  public AuditLogEntity setCreateTimestamp(final Long createTimestamp) {
    this.createTimestamp = createTimestamp;
    return this;
  }

  public String getIdentityEntityId() {
    return identityEntityId;
  }

  public AuditLogEntity setIdentityEntityId(final String identityEntityId) {
    this.identityEntityId = identityEntityId;
    return this;
  }

  public String getIdentityEntityType() {
    return identityEntityType;
  }

  public AuditLogEntity setIdentityEntityType(final String identityEntityType) {
    this.identityEntityType = identityEntityType;
    return this;
  }

  public Long getCompleteTimestamp() {
    return completeTimestamp;
  }

  public AuditLogEntity setCompleteTimestamp(final Long completeTimestamp) {
    this.completeTimestamp = completeTimestamp;
    return this;
  }

  public Short getOperationStatus() {
    return operationStatus;
  }

  public AuditLogEntity setOperationStatus(final Short operationStatus) {
    this.operationStatus = operationStatus;
    return this;
  }

  public String getDetails() {
    return details;
  }

  public AuditLogEntity setDetails(final String details) {
    this.details = details;
    return this;
  }

  public String getNote() {
    return note;
  }

  public AuditLogEntity setNote(final String note) {
    this.note = note;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }
}
