/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.function.Function;

public record FlowNodeInstanceDbModel(
    Long flowNodeInstanceKey,
    Long processInstanceKey,
    Long processDefinitionKey,
    String processDefinitionId,
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    String flowNodeId,
    String treePath,
    FlowNodeType type,
    FlowNodeState state,
    Long incidentKey,
    Long scopeKey,
    String tenantId)
    implements Copyable<FlowNodeInstanceDbModel> {

  @Override
  public FlowNodeInstanceDbModel copy(
      final Function<ObjectBuilder<FlowNodeInstanceDbModel>, ObjectBuilder<FlowNodeInstanceDbModel>>
          builderFunction) {
    return builderFunction
        .apply(
            new FlowNodeInstanceDbModelBuilder()
                .flowNodeInstanceKey(flowNodeInstanceKey)
                .processInstanceKey(processInstanceKey())
                .processDefinitionKey(processDefinitionKey)
                .processDefinitionId(processDefinitionId)
                .startDate(startDate)
                .endDate(endDate)
                .flowNodeId(flowNodeId)
                .treePath(treePath)
                .type(type)
                .state(state)
                .incidentKey(incidentKey)
                .scopeKey(scopeKey)
                .tenantId(tenantId))
        .build();
  }

  public static class FlowNodeInstanceDbModelBuilder
      implements ObjectBuilder<FlowNodeInstanceDbModel> {

    private Long flowNodeInstanceKey;
    private Long processInstanceKey;
    private Long processDefinitionKey;
    private String processDefinitionId;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private String flowNodeId;
    private String treePath;
    private FlowNodeType type;
    private FlowNodeState state;
    private Long incidentKey;
    private Long scopeKey;
    private String tenantId;

    // Public constructor to initialize the builder
    public FlowNodeInstanceDbModelBuilder() {}

    // Builder methods for each field
    public FlowNodeInstanceDbModelBuilder flowNodeInstanceKey(final Long key) {
      flowNodeInstanceKey = key;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder startDate(final OffsetDateTime startDate) {
      this.startDate = startDate;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder endDate(final OffsetDateTime endDate) {
      this.endDate = endDate;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder flowNodeId(final String flowNodeId) {
      this.flowNodeId = flowNodeId;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder treePath(final String treePath) {
      this.treePath = treePath;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder type(final FlowNodeType type) {
      this.type = type;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder state(final FlowNodeState state) {
      this.state = state;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder incidentKey(final Long incidentKey) {
      this.incidentKey = incidentKey;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder scopeKey(final Long scopeKey) {
      this.scopeKey = scopeKey;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder processDefinitionId(final String bpmnProcessId) {
      processDefinitionId = bpmnProcessId;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public FlowNodeInstanceDbModel build() {
      return new FlowNodeInstanceDbModel(
          flowNodeInstanceKey,
          processInstanceKey,
          processDefinitionKey,
          processDefinitionId,
          startDate,
          endDate,
          flowNodeId,
          treePath,
          type,
          state,
          incidentKey,
          scopeKey,
          tenantId);
    }
  }
}
