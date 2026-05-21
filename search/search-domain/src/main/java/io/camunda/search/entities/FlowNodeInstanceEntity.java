/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowNodeInstanceEntity(
    Long flowNodeInstanceKey,
    Long processInstanceKey,
    @Nullable Long rootProcessInstanceKey,
    Long processDefinitionKey,
    // only written on AI_START_STATES intents; absent on docs first created by a later intent.
    @Nullable OffsetDateTime startDate,
    @Nullable OffsetDateTime endDate,
    String flowNodeId,
    // null on exporter cache miss.
    @Nullable String flowNodeName,
    @Nullable String treePath,
    FlowNodeType type,
    FlowNodeState state,
    // not set by the primary handler; populated asynchronously by IncidentUpdateTask.
    @Nullable Boolean hasIncident,
    @Nullable Long incidentKey,
    String processDefinitionId,
    String tenantId,
    @Nullable Integer level)
    implements TenantOwnedEntity {

  public FlowNodeInstanceEntity {
    Objects.requireNonNull(flowNodeInstanceKey, "flowNodeInstanceKey");
    Objects.requireNonNull(processInstanceKey, "processInstanceKey");
    Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
    Objects.requireNonNull(flowNodeId, "flowNodeId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(processDefinitionId, "processDefinitionId");
    Objects.requireNonNull(tenantId, "tenantId");
  }

  public FlowNodeInstanceEntity withFlowNodeName(@Nullable final String name) {
    return new FlowNodeInstanceEntity(
        flowNodeInstanceKey,
        processInstanceKey,
        rootProcessInstanceKey,
        processDefinitionKey,
        startDate,
        endDate,
        flowNodeId,
        name,
        treePath,
        type,
        state,
        hasIncident,
        incidentKey,
        processDefinitionId,
        tenantId,
        level);
  }

  public boolean hasFlowNodeName() {
    return flowNodeName != null && !flowNodeName.isBlank();
  }

  public enum FlowNodeType {
    UNSPECIFIED,
    PROCESS,
    SUB_PROCESS,
    EVENT_SUB_PROCESS,
    AD_HOC_SUB_PROCESS,
    AD_HOC_SUB_PROCESS_INNER_INSTANCE,
    START_EVENT,
    INTERMEDIATE_CATCH_EVENT,
    INTERMEDIATE_THROW_EVENT,
    BOUNDARY_EVENT,
    END_EVENT,
    SERVICE_TASK,
    RECEIVE_TASK,
    USER_TASK,
    MANUAL_TASK,
    TASK,
    EXCLUSIVE_GATEWAY,
    INCLUSIVE_GATEWAY,
    PARALLEL_GATEWAY,
    EVENT_BASED_GATEWAY,
    SEQUENCE_FLOW,
    MULTI_INSTANCE_BODY,
    CALL_ACTIVITY,
    BUSINESS_RULE_TASK,
    SCRIPT_TASK,
    SEND_TASK,
    UNKNOWN;

    public static FlowNodeType fromZeebeBpmnElementType(final String bpmnElementType) {
      if (bpmnElementType == null) {
        return UNSPECIFIED;
      }
      try {
        return FlowNodeType.valueOf(bpmnElementType);
      } catch (final IllegalArgumentException ex) {
        return UNKNOWN;
      }
    }
  }

  public enum FlowNodeState {
    ACTIVE,
    COMPLETED,
    TERMINATED
  }
}
