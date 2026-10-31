/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.agent;

/**
 * Identity of a logical agent. Every process version mints a new agent definition key, so the
 * stable identity across versions is the BPMN process ID plus the element ID (ADR 0011).
 */
public final class AgentIds {

  private AgentIds() {}

  public static String agentId(final String processDefinitionKey, final String flowNodeId) {
    return processDefinitionKey + ":" + flowNodeId;
  }

  public static String agentDefinitionDocumentId(
      final String tenantId, final String processDefinitionKey, final String flowNodeId) {
    return tenantId + ":" + agentId(processDefinitionKey, flowNodeId);
  }
}
