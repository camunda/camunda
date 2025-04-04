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
import java.util.function.Function;

public record IncidentDbModel(
    Long incidentKey,
    Long processDefinitionKey,
    String processDefinitionId,
    Long processInstanceKey,
    Long flowNodeInstanceKey,
    String flowNodeId,
    Long jobKey,
    ErrorType errorType,
    String errorMessage,
    Integer errorMessageHash,
    OffsetDateTime creationDate,
    IncidentState state,
    String treePath,
    String tenantId,
    int partitionId,
    OffsetDateTime historyCleanupDate)
    implements DbModel<IncidentDbModel> {

  @Override
  public IncidentDbModel copy(
      final Function<ObjectBuilder<IncidentDbModel>, ObjectBuilder<IncidentDbModel>>
          builderFunction) {
    return builderFunction
        .apply(
            new Builder()
                .incidentKey(incidentKey)
                .processDefinitionKey(processDefinitionKey)
                .processDefinitionId(processDefinitionId)
                .processInstanceKey(processInstanceKey)
                .flowNodeInstanceKey(flowNodeInstanceKey)
                .flowNodeId(flowNodeId)
                .jobKey(jobKey)
                .errorType(errorType)
                .errorMessage(errorMessage)
                .errorMessageHash(errorMessageHash)
                .creationDate(creationDate)
                .state(state)
                .treePath(treePath)
                .tenantId(tenantId)
                .partitionId(partitionId)
                .historyCleanupDate(historyCleanupDate))
        .build();
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
    private Integer errorMessageHash;
    private OffsetDateTime creationDate;
    private IncidentState state;
    private String treePath;
    private String tenantId;
    private int partitionId;
    private OffsetDateTime historyCleanupDate;

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

    public Builder errorMessageHash(final Integer errorMessageHash) {
      this.errorMessageHash = errorMessageHash;
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

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public Builder historyCleanupDate(final OffsetDateTime value) {
      historyCleanupDate = value;
      return this;
    }

    @Override
    public IncidentDbModel build() {
      return new IncidentDbModel(
          incidentKey,
          processDefinitionKey,
          processDefinitionId,
          processInstanceKey,
          flowNodeInstanceKey,
          flowNodeId,
          jobKey,
          errorType,
          errorMessage,
          errorMessageHash,
          creationDate,
          state,
          treePath,
          tenantId,
          partitionId,
          historyCleanupDate);
    }
  }
}
