/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.auditlog;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.webapps.schema.entities.usermanagement.EntityJoinRelation;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.ClusterVariableScope;

public class AuditLogEntity extends AbstractExporterEntity<AuditLogEntity> {

  // the key of the affected entity
  private Long entityKey;
  // the id of the affected entity, if applicable (ex. Identity-related entities)
  private String entityId;
  // the type of the affected entity
  private AuditLogEntityType entityType;
  // the type of operation that was performed, i.e. the Zeebe record intent
  private AuditLogOperationType operationType;
  // the version of the affected entity, i.e. the Zeebe record version
  private Integer entityVersion;
  // the value type of the affected entity, i.e. the Zeebe record value type
  private Short entityValueType;
  // the intent of the affected entity, i.e. the Zeebe record intent
  private Short entityOperationIntent;
  // the key of the batch operation, if applicable
  private Long batchOperationKey;
  // the type of the batch operation, if applicable
  private BatchOperationType batchOperationType;
  // the creation timestamp of the Zeebe event that triggered the audit log entry
  private Long timestamp;
  // the type of the actor that performed the operation, (USER or CLIENT)
  private AuditLogActorType actorType;
  // the id of the actor that performed the operation
  private String actorId;
  // the id of the tenant the operation was performed in
  private String tenantId;
  private ClusterVariableScope tenantScope;
  // marks if the operations was successful or failed
  private AuditLogOperationResult result;
  // details
  private String details;
  // the explanation on why the operation was performed
  private String annotation;
  // the category of the operation (ADMIN, OPERATOR, USER_TASK)
  private AuditLogOperationCategory category;

  // searchable fields dependent on the entity type
  private String bpmnProcessId;
  private String decisionRequirementsId;
  private String decisionId;
  private Long deploymentKey;
  private Long formKey;
  private Long resourceKey;
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

  public AuditLogEntityType getEntityType() {
    return entityType;
  }

  public AuditLogEntity setEntityType(final AuditLogEntityType auditLogEntityType) {
    entityType = auditLogEntityType;
    return this;
  }

  public String getEntityId() {
    return entityId;
  }

  public AuditLogEntity setEntityId(final String entityId) {
    this.entityId = entityId;
    return this;
  }

  public AuditLogOperationType getOperationType() {
    return operationType;
  }

  public AuditLogEntity setOperationType(final AuditLogOperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  public Integer getEntityVersion() {
    return entityVersion;
  }

  public AuditLogEntity setEntityVersion(final Integer entityVersion) {
    this.entityVersion = entityVersion;
    return this;
  }

  public Short getEntityValueType() {
    return entityValueType;
  }

  public AuditLogEntity setEntityValueType(final Short entityValueType) {
    this.entityValueType = entityValueType;
    return this;
  }

  public Short getEntityOperationIntent() {
    return entityOperationIntent;
  }

  public AuditLogEntity setEntityOperationIntent(final Short entityOperationIntent) {
    this.entityOperationIntent = entityOperationIntent;
    return this;
  }

  public Long getBatchOperationKey() {
    return batchOperationKey;
  }

  public AuditLogEntity setBatchOperationKey(final Long batchOperationKey) {
    this.batchOperationKey = batchOperationKey;
    return this;
  }

  public BatchOperationType getBatchOperationType() {
    return batchOperationType;
  }

  public AuditLogEntity setBatchOperationType(final BatchOperationType batchOperationType) {
    this.batchOperationType = batchOperationType;
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

  public AuditLogActorType getActorType() {
    return actorType;
  }

  public AuditLogEntity setActorType(final AuditLogActorType actorType) {
    this.actorType = actorType;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public AuditLogEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public AuditLogOperationResult getResult() {
    return result;
  }

  public AuditLogEntity setResult(final AuditLogOperationResult result) {
    this.result = result;
    return this;
  }

  public String getAnnotation() {
    return annotation;
  }

  public AuditLogEntity setAnnotation(final String annotation) {
    this.annotation = annotation;
    return this;
  }

  public String getDetails() {
    return details;
  }

  public AuditLogEntity setDetails(final String details) {
    this.details = details;
    return this;
  }

  public AuditLogOperationCategory getCategory() {
    return category;
  }

  public AuditLogEntity setCategory(final AuditLogOperationCategory category) {
    this.category = category;
    return this;
  }

  public Long getDeploymentKey() {
    return deploymentKey;
  }

  public AuditLogEntity setDeploymentKey(final Long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  public Long getFormKey() {
    return formKey;
  }

  public AuditLogEntity setFormKey(final Long formKey) {
    this.formKey = formKey;
    return this;
  }

  public Long getResourceKey() {
    return resourceKey;
  }

  public AuditLogEntity setResourceKey(final Long resourceKey) {
    this.resourceKey = resourceKey;
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

  public ClusterVariableScope getTenantScope() {
    return tenantScope;
  }

  public AuditLogEntity setTenantScope(final ClusterVariableScope tenantScope) {
    this.tenantScope = tenantScope;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public AuditLogEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  public AuditLogEntity setDecisionRequirementsId(final String decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
    return this;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public AuditLogEntity setDecisionId(final String decisionId) {
    this.decisionId = decisionId;
    return this;
  }
}
