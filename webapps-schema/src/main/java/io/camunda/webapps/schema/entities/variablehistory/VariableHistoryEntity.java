/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.variablehistory;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.webapps.schema.entities.SinceVersion;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import java.time.OffsetDateTime;

/** One change of a variable, stored in the {@code variable-history} index. */
public class VariableHistoryEntity extends AbstractExporterEntity<VariableHistoryEntity> {
  // the sortable key of this change, encoded from the partition id and the log position
  @SinceVersion(value = "8.11.0", requireDefault = false)
  private Long variableHistoryItemKey;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private Long variableKey;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private String name;

  // the value, or its preview when the value is too long. For an entry that fills one output
  // collection item, only that item.
  @SinceVersion(value = "8.11.0", requireDefault = false)
  private String value;

  // the whole value after the change. Null when value already holds it, and for an entry that
  // fills one output collection item.
  @SinceVersion(value = "8.11.0", requireDefault = false)
  private String fullValue;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private Boolean isPreview;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private Long scopeKey;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private Long processInstanceKey;

  // the root of the process instance tree, used by the cleanup
  @SinceVersion(value = "8.11.0", requireDefault = false)
  private Long rootProcessInstanceKey;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private Integer storageOrdinal;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private Long processDefinitionKey;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private String processDefinitionId;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private String tenantId;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private OffsetDateTime timestamp;

  // CREATE for the first value of a variable, UPDATE for later ones
  @SinceVersion(value = "8.11.0", requireDefault = false)
  private VariableHistoryOperationType operationType;

  // what wrote the value, such as an output mapping
  @SinceVersion(value = "8.11.0", requireDefault = false)
  private VariableOperationType variableOperationType;

  // the element instance that caused the change. Without a causing element, this and elementId
  // stay absent, not the protocol defaults -1 and "", because the readers return what is stored.
  @SinceVersion(value = "8.11.0", requireDefault = false)
  private Long elementInstanceKey;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private String elementId;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private VariableHistoryActorType actorType;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private String actorId;

  // the element id of the agent that made the change
  @SinceVersion(value = "8.11.0", requireDefault = false)
  private String agentElementId;

  // the zero-based index of the output collection item that this change fills, set for the output
  // collection of a multi-instance element or an ad-hoc sub-process
  @SinceVersion(value = "8.11.0", requireDefault = false)
  private Integer itemIndex;

  public Long getVariableHistoryItemKey() {
    return variableHistoryItemKey;
  }

  public VariableHistoryEntity setVariableHistoryItemKey(final Long variableHistoryItemKey) {
    this.variableHistoryItemKey = variableHistoryItemKey;
    return this;
  }

  public Long getVariableKey() {
    return variableKey;
  }

  public VariableHistoryEntity setVariableKey(final Long variableKey) {
    this.variableKey = variableKey;
    return this;
  }

  public String getName() {
    return name;
  }

  public VariableHistoryEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public VariableHistoryEntity setValue(final String value) {
    this.value = value;
    return this;
  }

  public String getFullValue() {
    return fullValue;
  }

  public VariableHistoryEntity setFullValue(final String fullValue) {
    this.fullValue = fullValue;
    return this;
  }

  public Boolean getIsPreview() {
    return isPreview;
  }

  public VariableHistoryEntity setIsPreview(final Boolean isPreview) {
    this.isPreview = isPreview;
    return this;
  }

  public Long getScopeKey() {
    return scopeKey;
  }

  public VariableHistoryEntity setScopeKey(final Long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public VariableHistoryEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public VariableHistoryEntity setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public Integer getStorageOrdinal() {
    return storageOrdinal;
  }

  public VariableHistoryEntity setStorageOrdinal(final Integer storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public VariableHistoryEntity setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public VariableHistoryEntity setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public VariableHistoryEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public OffsetDateTime getTimestamp() {
    return timestamp;
  }

  public VariableHistoryEntity setTimestamp(final OffsetDateTime timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public VariableHistoryOperationType getOperationType() {
    return operationType;
  }

  public VariableHistoryEntity setOperationType(final VariableHistoryOperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  public VariableOperationType getVariableOperationType() {
    return variableOperationType;
  }

  public VariableHistoryEntity setVariableOperationType(
      final VariableOperationType variableOperationType) {
    this.variableOperationType = variableOperationType;
    return this;
  }

  public Long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public VariableHistoryEntity setElementInstanceKey(final Long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public String getElementId() {
    return elementId;
  }

  public VariableHistoryEntity setElementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public VariableHistoryActorType getActorType() {
    return actorType;
  }

  public VariableHistoryEntity setActorType(final VariableHistoryActorType actorType) {
    this.actorType = actorType;
    return this;
  }

  public String getActorId() {
    return actorId;
  }

  public VariableHistoryEntity setActorId(final String actorId) {
    this.actorId = actorId;
    return this;
  }

  public String getAgentElementId() {
    return agentElementId;
  }

  public VariableHistoryEntity setAgentElementId(final String agentElementId) {
    this.agentElementId = agentElementId;
    return this;
  }

  public Integer getItemIndex() {
    return itemIndex;
  }

  public VariableHistoryEntity setItemIndex(final Integer itemIndex) {
    this.itemIndex = itemIndex;
    return this;
  }
}
