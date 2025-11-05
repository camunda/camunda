/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.auditlog;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.usermanagement.EntityJoinRelation;

public class AuditLogEntity extends AbstractExporterEntity<AuditLogEntity> {

  // the unique id of the audit log entry
  private String id;
  // the key of the affected entity
  private Long entityKey;
  // the type of the affected entity, i.e. the Zeebe ValueType
  private Short entityType;
  // the version of the affected entity, i.e. the Zeebe record version
  private Integer entityVersion;
  // the type of operation that was performed, i.e. the Zeebe record intent
  private Short operationType;
  // the key of the batch operation, if applicable
  private Long batchOperationKey;
  // the creation timestamp of the Zeebe event that triggered the audit log entry
  private Long timestamp;
  // the id of the actor that performed the operation
  private String actorId;
  // the type of the actor that performed the operation, (USER or CLIENT)
  private Short actorType;
  // the id of the tenant the operation was performed in
  private String tenantId;
  // marks if the operations was successful or failed
  private OperationState operationState;
  // the explanation on why the operation was performed
  private String operationNote;
  // the category of the operation (ADMIN, OPERATOR, USER_TASK)
  private Short operationCategory;
  // details
  private String operationDetails;

  // searchable fields dependant on the entity type
  private Long processDefinitionKey;
  private Long processInstanceKey;
  private Long elementInstanceKey;
  private Long jobKey;
  private Long userTaskKey;
  private Long decisionRequirementsKey;
  private Long decisionKey;

  // join relation for batch operation parent and items
  private EntityJoinRelation join;

  public Long getEntityKey() {
    return entityKey;
  }

  public AuditLogEntity setEntityKey(final Long entityKey) {
    this.entityKey = entityKey;
    return this;
  }

  public Short getEntityType() {
    return entityType;
  }

  public AuditLogEntity setEntityType(final Short entityType) {
    this.entityType = entityType;
    return this;
  }

  public Integer getEntityVersion() {
    return entityVersion;
  }

  public void setEntityVersion(final Integer entityVersion) {
    this.entityVersion = entityVersion;
  }

  public Short getOperationType() {
    return operationType;
  }

  public AuditLogEntity setOperationType(final Short operationType) {
    this.operationType = operationType;
    return this;
  }

  public Long getBatchOperationKey() {
    return batchOperationKey;
  }

  public AuditLogEntity setBatchOperationKey(final Long batchOperationKey) {
    this.batchOperationKey = batchOperationKey;
    return this;
  }

  public Long getTimestamp() {
    return timestamp;
  }

  public AuditLogEntity setTimestamp(final Long timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public String getActorId() {
    return actorId;
  }

  public AuditLogEntity setActorId(final String actorId) {
    this.actorId = actorId;
    return this;
  }

  public Short getActorType() {
    return actorType;
  }

  public AuditLogEntity setActorType(final Short actorType) {
    this.actorType = actorType;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public OperationState getOperationState() {
    return operationState;
  }

  public AuditLogEntity setOperationState(final OperationState operationState) {
    this.operationState = operationState;
    return this;
  }

  public String getOperationNote() {
    return operationNote;
  }

  public AuditLogEntity setOperationNote(final String operationNote) {
    this.operationNote = operationNote;
    return this;
  }

  public String getOperationDetails() {
    return operationDetails;
  }

  public AuditLogEntity setOperationDetails(final String operationDetails) {
    this.operationDetails = operationDetails;
    return this;
  }

  public Short getOperationCategory() {
    return operationCategory;
  }

  public AuditLogEntity setOperationCategory(final Short operationCategory) {
    this.operationCategory = operationCategory;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public AuditLogEntity setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public AuditLogEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public AuditLogEntity setElementInstanceKey(final Long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public Long getJobKey() {
    return jobKey;
  }

  public AuditLogEntity setJobKey(final Long jobKey) {
    this.jobKey = jobKey;
    return this;
  }

  public Long getUserTaskKey() {
    return userTaskKey;
  }

  public AuditLogEntity setUserTaskKey(final Long userTaskKey) {
    this.userTaskKey = userTaskKey;
    return this;
  }

  public Long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  public AuditLogEntity setDecisionRequirementsKey(final Long decisionRequirementsKey) {
    this.decisionRequirementsKey = decisionRequirementsKey;
    return this;
  }

  public Long getDecisionKey() {
    return decisionKey;
  }

  public AuditLogEntity setDecisionKey(final Long decisionKey) {
    this.decisionKey = decisionKey;
    return this;
  }

  public EntityJoinRelation getJoin() {
    return join;
  }

  public AuditLogEntity setJoin(final EntityJoinRelation join) {
    this.join = join;
    return this;
  }
}
