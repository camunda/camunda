/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.agentic;

import java.util.List;

/** Choices for the agent and model filters of the agentic dashboard. */
public record AgenticFilterOptionsDto(List<AgentOption> agents, List<ModelOption> models) {

  public record AgentOption(String agentId, String name, String processDefinitionKey) {}

  public record ModelOption(String model, String provider) {}
}
