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

  // this can be generated from the record intent
  private String operationType;
  // this is optional, provided by Operate to correlate Zeebe events to Operate operations
  private String operationReference;
  // this can be the value type
  private String entityType;
  // this is the unique identifier (key) of the entity that was affected by the operation
  private String entityId;
  // the time when the operation was performed
  private String timestamp;
  // the user that performed the operation
  private String username;
  // the explanation on why the operation was performed
  private String annotation;

  public String getOperationType() {
    return operationType;
  }

  public AuditLogEntity setOperationType(final String operationType) {
    this.operationType = operationType;
    return this;
  }

  public String getOperationReference() {
    return operationReference;
  }

  public AuditLogEntity setOperationReference(final String operationReference) {
    this.operationReference = operationReference;
    return this;
  }

  public String getEntityType() {
    return entityType;
  }

  public AuditLogEntity setEntityType(final String entityType) {
    this.entityType = entityType;
    return this;
  }

  public String getEntityId() {
    return entityId;
  }

  public AuditLogEntity setEntityId(final String entityId) {
    this.entityId = entityId;
    return this;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public AuditLogEntity setTimestamp(final String timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public String getUsername() {
    return username;
  }

  public AuditLogEntity setUsername(final String username) {
    this.username = username;
    return this;
  }

  public String getAnnotation() {
    return annotation;
  }

  public AuditLogEntity setAnnotation(final String annotation) {
    this.annotation = annotation;
    return this;
  }
}
