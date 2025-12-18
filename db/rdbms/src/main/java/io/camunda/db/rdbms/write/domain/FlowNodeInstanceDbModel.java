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
    Long rootProcessInstanceKey,
    Long processDefinitionKey,
    String processDefinitionId,
    Long flowNodeScopeKey,
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    String flowNodeId,
    String flowNodeName,
    String treePath,
    FlowNodeType type,
    FlowNodeState state,
    Long incidentKey,
    Long numSubprocessIncidents,
    Boolean hasIncident,
    String tenantId,
    Integer partitionId,
    OffsetDateTime historyCleanupDate)
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
                .rootProcessInstanceKey(rootProcessInstanceKey())
                .processDefinitionKey(processDefinitionKey)
                .processDefinitionId(processDefinitionId)
                .flowNodeScopeKey(flowNodeScopeKey)
                .startDate(startDate)
                .endDate(endDate)
                .flowNodeId(flowNodeId)
                .flowNodeName(flowNodeName)
                .treePath(treePath)
                .type(type)
                .state(state)
                .incidentKey(incidentKey)
                .numSubprocessIncidents(numSubprocessIncidents)
                .hasIncident(hasIncident)
                .tenantId(tenantId)
                .partitionId(partitionId)
                .historyCleanupDate(historyCleanupDate))
        .build();
  }

  public static class FlowNodeInstanceDbModelBuilder
      implements ObjectBuilder<FlowNodeInstanceDbModel> {

    private Long flowNodeInstanceKey;
    private Long processInstanceKey;
    private Long rootProcessInstanceKey;
    private Long processDefinitionKey;
    private String processDefinitionId;
    private Long flowNodeScopeKey;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private String flowNodeId;
    private String flowNodeName;
    private String treePath;
    private FlowNodeType type;
    private FlowNodeState state;
    private Long incidentKey;
    private Long numSubprocessIncidents = 0L;
    private Boolean hasIncident;
    private String tenantId;
    private int partitionId;
    private OffsetDateTime historyCleanupDate;

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

    public FlowNodeInstanceDbModelBuilder rootProcessInstanceKey(
        final Long rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder flowNodeScopeKey(final Long flowNodeScopeKey) {
      this.flowNodeScopeKey = flowNodeScopeKey;
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

    public FlowNodeInstanceDbModelBuilder flowNodeName(final String flowNodeName) {
      this.flowNodeName = flowNodeName;
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

    public Long numSubprocessIncidents() {
      return numSubprocessIncidents;
    }

    public FlowNodeInstanceDbModelBuilder numSubprocessIncidents(
        final Long numSubprocessIncidents) {
      this.numSubprocessIncidents = numSubprocessIncidents;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder hasIncident(final Boolean hasIncident) {
      this.hasIncident = hasIncident;
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

    public FlowNodeInstanceDbModelBuilder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder historyCleanupDate(final OffsetDateTime value) {
      historyCleanupDate = value;
      return this;
    }

    @Override
    public FlowNodeInstanceDbModel build() {
      return new FlowNodeInstanceDbModel(
          flowNodeInstanceKey,
          processInstanceKey,
          rootProcessInstanceKey,
          processDefinitionKey,
          processDefinitionId,
          flowNodeScopeKey,
          startDate,
          endDate,
          flowNodeId,
          flowNodeName,
          treePath,
          type,
          state,
          incidentKey,
          numSubprocessIncidents,
          hasIncident,
          tenantId,
          partitionId,
          historyCleanupDate);
    }
  }
}
