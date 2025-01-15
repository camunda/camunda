/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.function.Function;

public final class IncidentDbModel implements DbModel<IncidentDbModel> {
  private final Long incidentKey;
  private final Long flowNodeInstanceKey;
  private final String flowNodeId;
  private final Long processInstanceKey;
  private final String processDefinitionId;
  private final Long processDefinitionKey;
  private final String errorMessage;
  private final ErrorType errorType;
  private final IncidentState state;
  private final OffsetDateTime creationDate;
  private final Long jobKey;
  private final String treePath;
  private final String tenantId;
  private final String legacyId;
  private final String legacyProcessInstanceId;

  public IncidentDbModel(Long incidentKey,
                         Long flowNodeInstanceKey,
                         String flowNodeId,
                         Long processInstanceKey,
                         String processDefinitionId,
                         Long processDefinitionKey,
                         String errorMessage,
                         ErrorType errorType,
                         IncidentState state,
                         OffsetDateTime creationDate,
                         Long jobKey,
                         String treePath,
                         String tenantId,
                         String legacyId,
                         String legacyProcessInstanceId) {
    this.incidentKey = incidentKey;
    this.flowNodeInstanceKey = flowNodeInstanceKey;
    this.flowNodeId = flowNodeId;
    this.processInstanceKey = processInstanceKey;
    this.processDefinitionId = processDefinitionId;
    this.processDefinitionKey = processDefinitionKey;
    this.errorMessage = errorMessage;
    this.errorType = errorType;
    this.state = state;
    this.creationDate = creationDate;
    this.jobKey = jobKey;
    this.treePath = treePath;
    this.tenantId = tenantId;
    this.legacyId = legacyId;
    this.legacyProcessInstanceId = legacyProcessInstanceId;
  }

  @Override
  public IncidentDbModel copy(final Function<ObjectBuilder<IncidentDbModel>, ObjectBuilder<IncidentDbModel>> builderFunction) {
    return builderFunction.apply(new Builder().incidentKey(incidentKey)
        .processDefinitionKey(processDefinitionKey)
        .processDefinitionId(processDefinitionId)
        .processInstanceKey(processInstanceKey)
        .flowNodeInstanceKey(flowNodeInstanceKey)
        .flowNodeId(flowNodeId)
        .jobKey(jobKey)
        .errorType(errorType)
        .errorMessage(errorMessage)
        .creationDate(creationDate)
        .state(state)
        .treePath(treePath)
        .tenantId(tenantId)).build();
  }

  public Long incidentKey() {
    return incidentKey;
  }

  public Long flowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  public String flowNodeId() {
    return flowNodeId;
  }

  public Long processInstanceKey() {
    return processInstanceKey;
  }

  public String processDefinitionId() {
    return processDefinitionId;
  }

  public Long processDefinitionKey() {
    return processDefinitionKey;
  }

  public String errorMessage() {
    return errorMessage;
  }

  public ErrorType errorType() {
    return errorType;
  }

  public IncidentState state() {
    return state;
  }

  public OffsetDateTime creationDate() {
    return creationDate;
  }

  public Long jobKey() {
    return jobKey;
  }

  public String treePath() {
    return treePath;
  }

  public String tenantId() {
    return tenantId;
  }

  public String legacyId() {
    return legacyId;
  }

  public String legacyProcessInstanceId() {
    return legacyProcessInstanceId;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this)
      return true;
    if (obj == null || obj.getClass() != this.getClass())
      return false;
    var that = (IncidentDbModel) obj;
    return Objects.equals(this.incidentKey, that.incidentKey) && Objects.equals(this.flowNodeInstanceKey, that.flowNodeInstanceKey) && Objects.equals(this.flowNodeId,
        that.flowNodeId) && Objects.equals(this.processInstanceKey, that.processInstanceKey) && Objects.equals(this.processDefinitionId,
        that.processDefinitionId) && Objects.equals(this.processDefinitionKey, that.processDefinitionKey) && Objects.equals(this.errorMessage,
        that.errorMessage) && Objects.equals(this.errorType, that.errorType) && Objects.equals(this.state, that.state) && Objects.equals(this.creationDate,
        that.creationDate) && Objects.equals(this.jobKey, that.jobKey) && Objects.equals(this.treePath, that.treePath) && Objects.equals(
        this.tenantId, that.tenantId) && Objects.equals(this.legacyId, that.legacyId) && Objects.equals(this.legacyProcessInstanceId,
        that.legacyProcessInstanceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(incidentKey, flowNodeInstanceKey, flowNodeId, processInstanceKey, processDefinitionId,
        processDefinitionKey, errorMessage, errorType, state, creationDate, jobKey, treePath, tenantId, legacyId,
        legacyProcessInstanceId);
  }

  @Override
  public String toString() {
    return "IncidentDbModel[" + "incidentKey=" + incidentKey + ", " + "flowNodeInstanceKey=" + flowNodeInstanceKey
        + ", " + "flowNodeId=" + flowNodeId + ", " + "processInstanceKey=" + processInstanceKey + ", "
        + "processDefinitionId=" + processDefinitionId + ", " + "processDefinitionKey=" + processDefinitionKey + ", "
        + "errorMessage=" + errorMessage + ", " + "errorType=" + errorType + ", " + "state=" + state + ", "
        + "creationDate=" + creationDate + ", " + "jobKey=" + jobKey + ", " + "treePath=" + treePath + ", "
        + "tenantId=" + tenantId + ", " + "legacyId=" + legacyId + ", " + "legacyProcessInstanceId="
        + legacyProcessInstanceId + ']';
  }

  public static class Builder implements ObjectBuilder<IncidentDbModel> {

    private Long incidentKey;
    private Long processDefinitionKey;
    private String processDefinitionId;
    private Long processInstanceKey;
    private Long flowNodeInstanceKey;
    private String flowNodeId;
    private Long jobKey;
    private ErrorType errorType;
    private String errorMessage;
    private OffsetDateTime creationDate;
    private IncidentState state;
    private String treePath;
    private String tenantId;
    private String legacyId;
    private String legacyProcessInstanceId;

    public Builder legacyId(final String id) {
      legacyId = id;
      return this;
    }

    public Builder legacyProcessInstanceId(final String id) {
      legacyProcessInstanceId = id;
      return this;
    }

    public Builder incidentKey(final Long incidentKey) {
      this.incidentKey = incidentKey;
      return this;
    }

    public Builder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public Builder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder flowNodeInstanceKey(final Long flowNodeInstanceKey) {
      this.flowNodeInstanceKey = flowNodeInstanceKey;
      return this;
    }

    public Builder flowNodeId(final String flowNodeId) {
      this.flowNodeId = flowNodeId;
      return this;
    }

    public Builder jobKey(final Long jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    public Builder errorType(final ErrorType errorType) {
      this.errorType = errorType;
      return this;
    }

    public Builder errorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    public Builder creationDate(final OffsetDateTime creationDate) {
      this.creationDate = creationDate;
      return this;
    }

    public Builder state(final IncidentState state) {
      this.state = state;
      return this;
    }

    public Builder treePath(final String treePath) {
      this.treePath = treePath;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public IncidentDbModel build() {
      return new IncidentDbModel(incidentKey, flowNodeInstanceKey, flowNodeId, processInstanceKey, processDefinitionId,
          processDefinitionKey, errorMessage, errorType, state, creationDate, jobKey, treePath, tenantId, legacyId,
          legacyProcessInstanceId);
    }
  }
}
