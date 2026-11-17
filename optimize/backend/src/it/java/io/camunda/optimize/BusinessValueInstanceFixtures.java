/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_BUSINESS_RULE_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_MANUAL_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_SCRIPT_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_SEND_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_SERVICE_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_USER_TASK;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Test-data builders for the Business Value Dashboard integration tests. Kept in the {@code
 * io.camunda.optimize} package so the static factories can reach the {@code protected static
 * AbstractBrokerlessZeebeCCSMIT#completedInstance} base builder without inheriting from it,
 * mirroring {@link AgenticInstanceFixtures}.
 */
public final class BusinessValueInstanceFixtures {

  private static final String STRUCTURAL_GATEWAY = "exclusiveGateway";
  private static final String STRUCTURAL_START_EVENT = "startEvent";
  private static final String STRUCTURAL_END_EVENT = "endEvent";

  private BusinessValueInstanceFixtures() {}

  /**
   * Builds a completed instance with the given execution duration (ms). Used by the cycle-time
   * tiles that assert on {@code DURATION}-view averages/percentiles.
   */
  public static ProcessInstanceDto.ProcessInstanceDtoBuilder bvdInstanceWithDuration(
      final String processDefinitionKey, final long durationMs) {
    return AbstractBrokerlessZeebeCCSMIT.completedInstance(processDefinitionKey)
        .duration(durationMs);
  }

  /**
   * Builds a completed instance carrying the given flow-node instances. Each flow-node inherits the
   * instance's process-definition key/version/tenant so the nested aggregation joins correctly with
   * the parent process instance.
   */
  public static ProcessInstanceDto bvdInstanceWithFlowNodes(
      final String processDefinitionKey, final String... flowNodeTypes) {
    final ProcessInstanceDto instance =
        AbstractBrokerlessZeebeCCSMIT.completedInstance(processDefinitionKey).build();
    final List<FlowNodeInstanceDto> nodes =
        Arrays.stream(flowNodeTypes).map(type -> flowNode(instance, type)).toList();
    instance.setFlowNodeInstances(nodes);
    return instance;
  }

  public static String serviceTaskNode() {
    return FLOW_NODE_TYPE_SERVICE_TASK;
  }

  public static String businessRuleTaskNode() {
    return FLOW_NODE_TYPE_BUSINESS_RULE_TASK;
  }

  public static String scriptTaskNode() {
    return FLOW_NODE_TYPE_SCRIPT_TASK;
  }

  public static String sendTaskNode() {
    return FLOW_NODE_TYPE_SEND_TASK;
  }

  public static String userTaskNode() {
    return FLOW_NODE_TYPE_USER_TASK;
  }

  public static String manualTaskNode() {
    return FLOW_NODE_TYPE_MANUAL_TASK;
  }

  public static String gatewayNode() {
    return STRUCTURAL_GATEWAY;
  }

  public static String startEventNode() {
    return STRUCTURAL_START_EVENT;
  }

  public static String endEventNode() {
    return STRUCTURAL_END_EVENT;
  }

  private static FlowNodeInstanceDto flowNode(
      final ProcessInstanceDto parent, final String flowNodeType) {
    final FlowNodeInstanceDto node = new FlowNodeInstanceDto();
    node.setFlowNodeInstanceId(UUID.randomUUID().toString());
    node.setFlowNodeId(flowNodeType + "-" + UUID.randomUUID());
    node.setFlowNodeType(flowNodeType);
    node.setProcessInstanceId(parent.getProcessInstanceId());
    node.setDefinitionKey(parent.getProcessDefinitionKey());
    node.setDefinitionVersion(parent.getProcessDefinitionVersion());
    node.setTenantId(parent.getTenantId());
    return node;
  }
}
