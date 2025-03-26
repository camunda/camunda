/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState.ACTIVE;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState.COMPLETED;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState.TERMINATED;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.BOUNDARY_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.BUSINESS_RULE_TASK;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.CALL_ACTIVITY;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.END_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.EVENT_BASED_GATEWAY;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.EVENT_SUB_PROCESS;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.EXCLUSIVE_GATEWAY;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.INCLUSIVE_GATEWAY;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.INTERMEDIATE_CATCH_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.INTERMEDIATE_THROW_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.MANUAL_TASK;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.MULTI_INSTANCE_BODY;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.PARALLEL_GATEWAY;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.PROCESS;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.RECEIVE_TASK;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.SCRIPT_TASK;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.SEND_TASK;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.SEQUENCE_FLOW;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.SERVICE_TASK;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.START_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.SUB_PROCESS;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.TASK;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.UNKNOWN;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.UNSPECIFIED;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.USER_TASK;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;

public class FlowNodeInstanceEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity,
        FlowNodeInstanceEntity> {

  @Override
  public FlowNodeInstanceEntity apply(
      final io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity value) {
    return new FlowNodeInstanceEntity(
        value.getKey(),
        value.getProcessInstanceKey(),
        value.getProcessDefinitionKey(),
        value.getStartDate(),
        value.getEndDate(),
        value.getFlowNodeId(),
        value.getTreePath(),
        toType(value.getType()),
        toState(value.getState()),
        value.isIncident(),
        value.getIncidentKey(),
        value.getBpmnProcessId(),
        value.getTenantId());
  }

  private FlowNodeType toType(
      final io.camunda.webapps.schema.entities.flownode.FlowNodeType value) {
    if (value == null) {
      return null;
    }
    return switch (value) {
      case UNSPECIFIED -> UNSPECIFIED;
      case PROCESS -> PROCESS;
      case SUB_PROCESS -> SUB_PROCESS;
      case EVENT_SUB_PROCESS -> EVENT_SUB_PROCESS;
      case START_EVENT -> START_EVENT;
      case INTERMEDIATE_CATCH_EVENT -> INTERMEDIATE_CATCH_EVENT;
      case INTERMEDIATE_THROW_EVENT -> INTERMEDIATE_THROW_EVENT;
      case BOUNDARY_EVENT -> BOUNDARY_EVENT;
      case END_EVENT -> END_EVENT;
      case SERVICE_TASK -> SERVICE_TASK;
      case RECEIVE_TASK -> RECEIVE_TASK;
      case USER_TASK -> USER_TASK;
      case MANUAL_TASK -> MANUAL_TASK;
      case TASK -> TASK;
      case EXCLUSIVE_GATEWAY -> EXCLUSIVE_GATEWAY;
      case INCLUSIVE_GATEWAY -> INCLUSIVE_GATEWAY;
      case PARALLEL_GATEWAY -> PARALLEL_GATEWAY;
      case EVENT_BASED_GATEWAY -> EVENT_BASED_GATEWAY;
      case SEQUENCE_FLOW -> SEQUENCE_FLOW;
      case MULTI_INSTANCE_BODY -> MULTI_INSTANCE_BODY;
      case CALL_ACTIVITY -> CALL_ACTIVITY;
      case BUSINESS_RULE_TASK -> BUSINESS_RULE_TASK;
      case SCRIPT_TASK -> SCRIPT_TASK;
      case SEND_TASK -> SEND_TASK;
      case UNKNOWN -> UNKNOWN;
      default -> throw new IllegalArgumentException("Unknown type: " + value);
    };
  }

  private FlowNodeState toState(
      final io.camunda.webapps.schema.entities.flownode.FlowNodeState value) {
    if (value == null) {
      return null;
    }
    return switch (value) {
      case ACTIVE -> ACTIVE;
      case COMPLETED -> COMPLETED;
      case TERMINATED -> TERMINATED;
      default -> throw new IllegalArgumentException("Unknown state: " + value);
    };
  }
}
