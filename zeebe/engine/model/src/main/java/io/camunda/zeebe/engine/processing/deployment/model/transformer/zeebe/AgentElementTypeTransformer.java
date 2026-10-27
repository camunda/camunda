/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAgentDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAgentType;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;

/**
 * Detects the {@link AgentDefinitionType} of a {@code serviceTask} or {@code adHocSubProcess}
 * element at deploy time from an explicit {@code zeebe:agentDefinition} marker. When the marker is
 * absent, the element is left at {@link AgentDefinitionType#UNSPECIFIED}.
 */
public final class AgentElementTypeTransformer {

  public void transform(
      final ExecutableJobWorkerElement element, final ZeebeAgentDefinition agentDefinition) {
    element.setAgentDefinitionType(detectAgentDefinitionType(agentDefinition));
  }

  private static AgentDefinitionType detectAgentDefinitionType(
      final ZeebeAgentDefinition agentDefinition) {
    if (agentDefinition == null) {
      return AgentDefinitionType.UNSPECIFIED;
    }
    return mapAgentType(agentDefinition.getAgentType());
  }

  private static AgentDefinitionType mapAgentType(final ZeebeAgentType agentType) {
    if (agentType == null) {
      // a missing/empty agentType is already reported by the required-attribute validation
      return AgentDefinitionType.UNSPECIFIED;
    }
    return switch (agentType) {
      case aiAgentSubProcess -> AgentDefinitionType.AI_AGENT_SUB_PROCESS;
      case aiAgentTask -> AgentDefinitionType.AI_AGENT_TASK;
      case external -> AgentDefinitionType.EXTERNAL_AGENT;
    };
  }
}
