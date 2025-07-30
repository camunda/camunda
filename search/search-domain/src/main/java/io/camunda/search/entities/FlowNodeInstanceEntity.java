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

@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowNodeInstanceEntity(
    Long flowNodeInstanceKey,
    Long processInstanceKey,
    Long processDefinitionKey,
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    String flowNodeId,
    String flowNodeName,
    String treePath,
    FlowNodeType type,
    FlowNodeState state,
    Boolean hasIncident,
    Long incidentKey,
    String processDefinitionId,
    String tenantId)
    implements TenantOwnedEntity {

  public FlowNodeInstanceEntity withFlowNodeName(final String name) {
    return new FlowNodeInstanceEntity(
        flowNodeInstanceKey,
        processInstanceKey,
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
        tenantId);
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
