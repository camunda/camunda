/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.auditlog;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.webapps.schema.entities.SinceVersion;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.time.OffsetDateTime;

public class AuditLogEntity extends AbstractExporterEntity<AuditLogEntity> {

  // the key of the affected entity
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String entityKey;

  // the type of the affected entity
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private AuditLogEntityType entityType;

  // the type of operation that was performed, i.e. the Zeebe record intent
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private AuditLogOperationType operationType;

  // the version of the affected entity, i.e. the Zeebe record version
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Integer entityVersion;

  // the value type of the affected entity, i.e. the Zeebe record value type
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Short entityValueType;

  // the intent of the affected entity, i.e. the Zeebe record intent
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Short entityOperationIntent;

  // the creation timestamp of the Zeebe event that triggered the audit log entry
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private OffsetDateTime timestamp;

  // the category of the operation (ADMIN, DEPLOYED_RESOURCES, USER_TASKS)
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private AuditLogOperationCategory category;

  // the type of the actor that performed the operation, (USER or CLIENT)
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private AuditLogActorType actorType;

  // the id of the actor that performed the operation
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String actorId;

  // the element id of the agent that performed the operation (e.g. ad-hoc subprocess element id)
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String agentElementId;

  // marks if the operations was successful or failed
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private AuditLogOperationResult result;

  // the key of the batch operation, if applicable
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long batchOperationKey;

  // the type of the batch operation, if applicable
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private BatchOperationType batchOperationType;

  // the id of the tenant the operation was performed in
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String tenantId;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private AuditLogTenantScope tenantScope;

  // the explanation on why the operation was performed
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String annotation;

  // searchable fields dependent on the entity type
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String processDefinitionId;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long processDefinitionKey;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long processInstanceKey;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long elementInstanceKey;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long jobKey;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long userTaskKey;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String decisionRequirementsId;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long decisionRequirementsKey;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String decisionDefinitionId;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long decisionDefinitionKey;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long decisionEvaluationKey;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long deploymentKey;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long formKey;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long resourceKey;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long rootProcessInstanceKey;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private AuditLogEntityType relatedEntityType;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String relatedEntityKey;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String entityDescription;

  public String getEntityKey() {
    return entityKey;
  }

  public AuditLogEntity setEntityKey(final String entityKey) {
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

  public OffsetDateTime getTimestamp() {
    return timestamp;
  }

  public AuditLogEntity setTimestamp(final OffsetDateTime timestamp) {
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

  public String getAgentElementId() {
    return agentElementId;
  }

  public AuditLogEntity setAgentElementId(final String agentElementId) {
    this.agentElementId = agentElementId;
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

  public Long getDecisionDefinitionKey() {
    return decisionDefinitionKey;
  }

  public AuditLogEntity setDecisionDefinitionKey(final Long decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
    return this;
  }

  public AuditLogTenantScope getTenantScope() {
    return tenantScope;
  }

  public AuditLogEntity setTenantScope(final AuditLogTenantScope tenantScope) {
    this.tenantScope = tenantScope;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public AuditLogEntity setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  public AuditLogEntity setDecisionRequirementsId(final String decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
    return this;
  }

  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  public AuditLogEntity setDecisionDefinitionId(final String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
    return this;
  }

  public Long getDecisionEvaluationKey() {
    return decisionEvaluationKey;
  }

  public AuditLogEntity setDecisionEvaluationKey(final Long decisionEvaluationKey) {
    this.decisionEvaluationKey = decisionEvaluationKey;
    return this;
  }

  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public AuditLogEntity setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public AuditLogEntityType getRelatedEntityType() {
    return relatedEntityType;
  }

  public AuditLogEntity setRelatedEntityType(final AuditLogEntityType relatedEntityType) {
    this.relatedEntityType = relatedEntityType;
    return this;
  }

  public String getRelatedEntityKey() {
    return relatedEntityKey;
  }

  public AuditLogEntity setRelatedEntityKey(final String relatedEntityKey) {
    this.relatedEntityKey = relatedEntityKey;
    return this;
  }

  public String getEntityDescription() {
    return entityDescription;
  }

  public AuditLogEntity setEntityDescription(final String entityDescription) {
    this.entityDescription = entityDescription;
    return this;
  }
}
