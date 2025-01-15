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
import java.util.Objects;
import java.util.function.Function;

public final class FlowNodeInstanceDbModel implements Copyable<FlowNodeInstanceDbModel> {
  private final Long flowNodeInstanceKey;
  private final Long processInstanceKey;
  private final Long processDefinitionKey;
  private final String processDefinitionId;
  private final OffsetDateTime startDate;
  private final OffsetDateTime endDate;
  private final String flowNodeId;
  private final String treePath;
  private final FlowNodeType type;
  private final FlowNodeState state;
  private final Long incidentKey;
  private final Long numSubprocessIncidents;
  private final String tenantId;
  private final String legacyId;
  private final String legacyProcessInstanceId;
  private final Boolean hasIncident;

  public FlowNodeInstanceDbModel(
      Long flowNodeInstanceKey,
      String legacyId,
      String legacyProcessInstanceId,
      String flowNodeId,
      Long processInstanceKey,
      String processDefinitionId,
      Long processDefinitionKey,
      FlowNodeType type,
      FlowNodeState state,
      OffsetDateTime startDate,
      OffsetDateTime endDate,
      String tenantId,
      String treePath,
      Long incidentKey,
      Long numSubprocessIncidents,
      Boolean hasIncident) {
    this.flowNodeInstanceKey = flowNodeInstanceKey;
    this.processInstanceKey = processInstanceKey;
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionId = processDefinitionId;
    this.startDate = startDate;
    this.endDate = endDate;
    this.flowNodeId = flowNodeId;
    this.treePath = treePath;
    this.type = type;
    this.state = state;
    this.incidentKey = incidentKey;
    this.numSubprocessIncidents = numSubprocessIncidents;
    this.tenantId = tenantId;
    this.legacyId = legacyId;
    this.legacyProcessInstanceId = legacyProcessInstanceId;
    this.hasIncident = hasIncident;
  }

  @Override
  public FlowNodeInstanceDbModel copy(
      final Function<ObjectBuilder<FlowNodeInstanceDbModel>, ObjectBuilder<FlowNodeInstanceDbModel>>
          builderFunction) {
    return builderFunction
        .apply(
            new FlowNodeInstanceDbModelBuilder()
                .legacyId(legacyId)
                .legacyProcessInstanceId(legacyProcessInstanceId)
                .flowNodeInstanceKey(flowNodeInstanceKey)
                .processInstanceKey(processInstanceKey)
                .processDefinitionKey(processDefinitionKey)
                .processDefinitionId(processDefinitionId)
                .startDate(startDate)
                .endDate(endDate)
                .flowNodeId(flowNodeId)
                .treePath(treePath)
                .type(type)
                .state(state)
                .incidentKey(incidentKey)
                .numSubprocessIncidents(numSubprocessIncidents)
                .tenantId(tenantId))
        .build();
  }

  public Long flowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  public Long processInstanceKey() {
    return processInstanceKey;
  }

  public Long processDefinitionKey() {
    return processDefinitionKey;
  }

  public String processDefinitionId() {
    return processDefinitionId;
  }

  public OffsetDateTime startDate() {
    return startDate;
  }

  public OffsetDateTime endDate() {
    return endDate;
  }

  public String flowNodeId() {
    return flowNodeId;
  }

  public String treePath() {
    return treePath;
  }

  public FlowNodeType type() {
    return type;
  }

  public FlowNodeState state() {
    return state;
  }

  public Long incidentKey() {
    return incidentKey;
  }

  public Long numSubprocessIncidents() {
    return numSubprocessIncidents;
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

  public Boolean hasIncident() {
    return hasIncident;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    final var that = (FlowNodeInstanceDbModel) obj;
    return Objects.equals(this.flowNodeInstanceKey, that.flowNodeInstanceKey)
        && Objects.equals(this.processInstanceKey, that.processInstanceKey)
        && Objects.equals(this.processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(this.processDefinitionId, that.processDefinitionId)
        && Objects.equals(this.startDate, that.startDate)
        && Objects.equals(this.endDate, that.endDate)
        && Objects.equals(this.flowNodeId, that.flowNodeId)
        && Objects.equals(this.treePath, that.treePath)
        && Objects.equals(this.type, that.type)
        && Objects.equals(this.state, that.state)
        && Objects.equals(this.incidentKey, that.incidentKey)
        && Objects.equals(this.numSubprocessIncidents, that.numSubprocessIncidents)
        && Objects.equals(this.tenantId, that.tenantId)
        && Objects.equals(this.legacyId, that.legacyId)
        && Objects.equals(this.legacyProcessInstanceId, that.legacyProcessInstanceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
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
        numSubprocessIncidents,
        tenantId,
        legacyId,
        legacyProcessInstanceId);
  }

  @Override
  public String toString() {
    return "FlowNodeInstanceDbModel["
        + "flowNodeInstanceKey="
        + flowNodeInstanceKey
        + ", "
        + "processInstanceKey="
        + processInstanceKey
        + ", "
        + "processDefinitionKey="
        + processDefinitionKey
        + ", "
        + "processDefinitionId="
        + processDefinitionId
        + ", "
        + "startDate="
        + startDate
        + ", "
        + "endDate="
        + endDate
        + ", "
        + "flowNodeId="
        + flowNodeId
        + ", "
        + "treePath="
        + treePath
        + ", "
        + "type="
        + type
        + ", "
        + "state="
        + state
        + ", "
        + "incidentKey="
        + incidentKey
        + ", "
        + "numSubprocessIncidents="
        + numSubprocessIncidents
        + ", "
        + "tenantId="
        + tenantId
        + ", "
        + "legacyId="
        + legacyId
        + ", "
        + "legacyProcessInstanceId="
        + legacyProcessInstanceId
        + ']';
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
    private Long numSubprocessIncidents = 0L;
    private String tenantId;
    private String legacyId;
    private String legacyProcessInstanceId;
    private Boolean hasIncident;

    // Public constructor to initialize the builder
    public FlowNodeInstanceDbModelBuilder() {}

    // Builder methods for each field
    public FlowNodeInstanceDbModelBuilder legacyId(final String id) {
      legacyId = id;
      return this;
    }

    public FlowNodeInstanceDbModelBuilder legacyProcessInstanceId(final String id) {
      legacyProcessInstanceId = id;
      return this;
    }

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

    public Long numSubprocessIncidents() {
      return numSubprocessIncidents;
    }

    public FlowNodeInstanceDbModelBuilder numSubprocessIncidents(
        final Long numSubprocessIncidents) {
      this.numSubprocessIncidents = numSubprocessIncidents;
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

    public FlowNodeInstanceDbModelBuilder hasIncident(final Boolean hasIncident) {
      this.hasIncident = hasIncident;
      return this;
    }

    @Override
    public FlowNodeInstanceDbModel build() {
      return new FlowNodeInstanceDbModel(
          flowNodeInstanceKey,
          legacyId,
          legacyProcessInstanceId,
          flowNodeId,
          processInstanceKey,
          processDefinitionId,
          processDefinitionKey,
          type,
          state,
          startDate,
          endDate,
          tenantId,
          treePath,
          incidentKey,
          numSubprocessIncidents,
          hasIncident);
    }
  }
}
