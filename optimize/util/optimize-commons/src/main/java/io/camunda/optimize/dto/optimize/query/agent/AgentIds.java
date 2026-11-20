/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.agent;

/**
 * Identifiers of a logical agent. The engine mints a new agent definition key for every process
 * version, so one agent across versions is identified by its BPMN process ID and element ID, and
 * scoped per tenant (zeebe ADR 0011).
 *
 * <p>Optimize uses C7 naming: {@code processDefinitionKey} is the BPMN process ID. See {@code
 * optimize/docs/adr/001-c7-naming-conventions.md}.
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
