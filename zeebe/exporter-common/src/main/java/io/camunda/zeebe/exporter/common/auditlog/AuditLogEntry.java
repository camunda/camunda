/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog;

import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.AuditLogActor;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.AuditLogTenant;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.AuditLogProcessInstanceRelated;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import io.camunda.zeebe.util.DateUtil;
import java.time.OffsetDateTime;
import java.util.Optional;

public class AuditLogEntry {

  private String entityKey;

  // the type of the affected entity
  private io.camunda.search.entities.AuditLogEntity.AuditLogEntityType entityType;

  // the description of the affected entity
  private String entityDescription;

  // the type of operation that was performed, i.e. the Zeebe record intent
  private io.camunda.search.entities.AuditLogEntity.AuditLogOperationType operationType;

  // the version of the affected entity, i.e. the Zeebe record version
  private Integer entityVersion;

  // the value type of the affected entity, i.e. the Zeebe record value type
  private Short entityValueType;

  // the intent of the affected entity, i.e. the Zeebe record intent
  private Short entityOperationIntent;

  // the creation timestamp of the Zeebe event that triggered the audit log entry
  private OffsetDateTime timestamp;

  // the category of the operation (ADMIN, DEPLOYED_RESOURCES, USER_TASKS)
  private io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory category;

  // the actor that performed the operation
  private AuditLogActor actor;

  // marks if the operations was successful or failed
  private io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult result;

  // the key of the batch operation, if applicable
  private Long batchOperationKey;

  // the type of the batch operation, if applicable
  private BatchOperationType batchOperationType;

  // the tenant the operation was performed in
  private Optional<AuditLogTenant> tenant;

  // the explanation on why the operation was performed
  private String annotation;

  // searchable fields dependent on the entity type
  private String processDefinitionId;

  private Long processDefinitionKey;

  private Long processInstanceKey;

  private Long elementInstanceKey;

  private Long jobKey;

  private Long userTaskKey;

  private String decisionRequirementsId;

  private Long decisionRequirementsKey;

  private String decisionDefinitionId;

  private Long decisionDefinitionKey;

  private Long decisionEvaluationKey;

  private Long deploymentKey;

  private Long formKey;

  private Long resourceKey;

  private Long rootProcessInstanceKey;

  private String relatedEntityKey;

  private AuditLogEntityType relatedEntityType;

  private Agent agent;

  public String getEntityKey() {
    return entityKey;
  }

  public AuditLogEntry setEntityKey(final String entityKey) {
    this.entityKey = entityKey;
    return this;
  }

  public Optional<Agent> getAgent() {
    return Optional.ofNullable(agent);
  }

  public AuditLogEntry setAgent(final Agent agent) {
    this.agent = agent;
    return this;
  }

  public AuditLogEntry setAgent(final Optional<Agent> agent) {
    this.agent = agent.orElse(null);
    return this;
  }

  public AuditLogEntityType getEntityType() {
    return entityType;
  }

  public AuditLogEntry setEntityType(final AuditLogEntityType auditLogEntityType) {
    entityType = auditLogEntityType;
    return this;
  }

  public String getEntityDescription() {
    return entityDescription;
  }

  public AuditLogEntry setEntityDescription(final String entityDescription) {
    this.entityDescription = entityDescription;
    return this;
  }

  public AuditLogOperationType getOperationType() {
    return operationType;
  }

  public AuditLogEntry setOperationType(final AuditLogOperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  public Integer getEntityVersion() {
    return entityVersion;
  }

  public AuditLogEntry setEntityVersion(final Integer entityVersion) {
    this.entityVersion = entityVersion;
    return this;
  }

  public Short getEntityValueType() {
    return entityValueType;
  }

  public AuditLogEntry setEntityValueType(final Short entityValueType) {
    this.entityValueType = entityValueType;
    return this;
  }

  public Short getEntityOperationIntent() {
    return entityOperationIntent;
  }

  public AuditLogEntry setEntityOperationIntent(final Short entityOperationIntent) {
    this.entityOperationIntent = entityOperationIntent;
    return this;
  }

  public Long getBatchOperationKey() {
    return batchOperationKey;
  }

  public AuditLogEntry setBatchOperationKey(final Long batchOperationKey) {
    this.batchOperationKey = batchOperationKey;
    return this;
  }

  public BatchOperationType getBatchOperationType() {
    return batchOperationType;
  }

  public AuditLogEntry setBatchOperationType(final BatchOperationType batchOperationType) {
    this.batchOperationType = batchOperationType;
    return this;
  }

  public OffsetDateTime getTimestamp() {
    return timestamp;
  }

  public AuditLogEntry setTimestamp(final OffsetDateTime timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public AuditLogActor getActor() {
    return actor;
  }

  public AuditLogEntry setActor(final AuditLogActor actor) {
    this.actor = actor;
    return this;
  }

  public Optional<AuditLogTenant> getTenant() {
    return tenant;
  }

  public AuditLogEntry setTenant(final Optional<AuditLogTenant> tenant) {
    this.tenant = tenant;
    return this;
  }

  public AuditLogOperationResult getResult() {
    return result;
  }

  public AuditLogEntry setResult(final AuditLogOperationResult result) {
    this.result = result;
    return this;
  }

  public String getAnnotation() {
    return annotation;
  }

  public AuditLogEntry setAnnotation(final String annotation) {
    this.annotation = annotation;
    return this;
  }

  public AuditLogOperationCategory getCategory() {
    return category;
  }

  public AuditLogEntry setCategory(final AuditLogOperationCategory category) {
    this.category = category;
    return this;
  }

  public Long getDeploymentKey() {
    return deploymentKey;
  }

  public AuditLogEntry setDeploymentKey(final Long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  public Long getFormKey() {
    return formKey;
  }

  public AuditLogEntry setFormKey(final Long formKey) {
    this.formKey = formKey;
    return this;
  }

  public Long getResourceKey() {
    return resourceKey;
  }

  public AuditLogEntry setResourceKey(final Long resourceKey) {
    this.resourceKey = resourceKey;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public AuditLogEntry setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public AuditLogEntry setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public AuditLogEntry setElementInstanceKey(final Long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public Long getJobKey() {
    return jobKey;
  }

  public AuditLogEntry setJobKey(final Long jobKey) {
    this.jobKey = jobKey;
    return this;
  }

  public Long getUserTaskKey() {
    return userTaskKey;
  }

  public AuditLogEntry setUserTaskKey(final Long userTaskKey) {
    this.userTaskKey = userTaskKey;
    return this;
  }

  public Long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  public AuditLogEntry setDecisionRequirementsKey(final Long decisionRequirementsKey) {
    this.decisionRequirementsKey = decisionRequirementsKey;
    return this;
  }

  public Long getDecisionDefinitionKey() {
    return decisionDefinitionKey;
  }

  public AuditLogEntry setDecisionDefinitionKey(final Long decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public AuditLogEntry setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  public AuditLogEntry setDecisionRequirementsId(final String decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
    return this;
  }

  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  public AuditLogEntry setDecisionDefinitionId(final String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
    return this;
  }

  public Long getDecisionEvaluationKey() {
    return decisionEvaluationKey;
  }

  public AuditLogEntry setDecisionEvaluationKey(final Long decisionEvaluationKey) {
    this.decisionEvaluationKey = decisionEvaluationKey;
    return this;
  }

  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public AuditLogEntry setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public String getRelatedEntityKey() {
    return relatedEntityKey;
  }

  public AuditLogEntry setRelatedEntityKey(final String relatedEntityKey) {
    this.relatedEntityKey = relatedEntityKey;
    return this;
  }

  public AuditLogEntityType getRelatedEntityType() {
    return relatedEntityType;
  }

  public AuditLogEntry setRelatedEntityType(final AuditLogEntityType relatedEntityType) {
    this.relatedEntityType = relatedEntityType;
    return this;
  }

  public static <R extends RecordValue> AuditLogEntry of(final Record<R> record) {
    final AuditLogInfo info = AuditLogInfo.of(record);

    final AuditLogEntry log =
        new AuditLogEntry()
            .setEntityKey(String.valueOf(record.getKey()))
            .setEntityType(info.entityType())
            .setCategory(info.category())
            .setOperationType(info.operationType())
            .setActor(info.actor())
            .setAgent(record.getAgent())
            .setTenant(AuditLogTenant.of(record))
            .setBatchOperationKey(getBatchOperationKey(record))
            .setProcessInstanceKey(getProcessInstanceKey(record))
            .setProcessDefinitionKey(getProcessDefinitionKey(record))
            .setElementInstanceKey(getElementInstanceKey(record))
            .setProcessDefinitionId(getProcessDefinitionId(record))
            .setEntityVersion(record.getRecordVersion())
            .setEntityValueType(record.getValueType().value())
            .setEntityOperationIntent(record.getIntent().value())
            .setTimestamp(DateUtil.toOffsetDateTime(record.getTimestamp()));

    return log;
  }

  private static <R extends RecordValue> Long getProcessInstanceKey(final Record<R> record) {
    final var value = record.getValue();
    if (value instanceof ProcessInstanceRelated) {
      return ((ProcessInstanceRelated) value).getProcessInstanceKey();
    }
    return null;
  }

  private static <R extends RecordValue> Long getProcessDefinitionKey(final Record<R> record) {
    final var value = record.getValue();
    if (value instanceof ProcessInstanceRelated) {
      return ((ProcessInstanceRelated) value).getProcessDefinitionKey();
    }
    return null;
  }

  private static <R extends RecordValue> Long getBatchOperationKey(final Record<R> record) {
    final var batchOperationKey = record.getBatchOperationReference();
    if (RecordMetadataDecoder.batchOperationReferenceNullValue() != batchOperationKey) {
      return batchOperationKey;
    }
    return null;
  }

  private static <R extends RecordValue> Long getElementInstanceKey(final Record<R> record) {
    final var value = record.getValue();
    if (value instanceof AuditLogProcessInstanceRelated) {
      return ((AuditLogProcessInstanceRelated) value).getElementInstanceKey();
    }
    return null;
  }

  private static <R extends RecordValue> String getProcessDefinitionId(final Record<R> record) {
    final var value = record.getValue();
    if (value instanceof AuditLogProcessInstanceRelated) {
      return ((AuditLogProcessInstanceRelated) value).getBpmnProcessId();
    }
    return null;
  }
}
