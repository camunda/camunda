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
 * element at deploy time. An explicit {@code zeebe:agentDefinition} marker wins if present;
 * otherwise a recognized {@code zeebe:modelerTemplate} id is used as a fallback so that agent
 * connector templates authored before the marker was introduced are still detected. When neither is
 * present, or the {@code zeebe:modelerTemplate} id isn't recognized, the element is left at {@link
 * AgentDefinitionType#UNSPECIFIED}.
 */
public final class AgentElementTypeTransformer {

  /** {@code zeebe:modelerTemplate} id of the Agentic AI ad-hoc sub-process connector template. */
  private static final String MODELER_TEMPLATE_AI_AGENT_SUB_PROCESS =
      "io.camunda.connectors.agenticai.aiagent.jobworker.v1";

  /** {@code zeebe:modelerTemplate} id of the Agentic AI service task connector template. */
  private static final String MODELER_TEMPLATE_AI_AGENT_TASK =
      "io.camunda.connectors.agenticai.aiagent.v1";

  public void transform(
      final ExecutableJobWorkerElement element,
      final ZeebeAgentDefinition agentDefinition,
      final String modelerTemplate) {
    element.setAgentDefinitionType(detectAgentDefinitionType(agentDefinition, modelerTemplate));
  }

  private static AgentDefinitionType detectAgentDefinitionType(
      final ZeebeAgentDefinition agentDefinition, final String modelerTemplate) {
    if (agentDefinition != null) {
      return mapAgentType(agentDefinition.getAgentType());
    }
    return mapModelerTemplate(modelerTemplate);
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

  private static AgentDefinitionType mapModelerTemplate(final String modelerTemplate) {
    if (MODELER_TEMPLATE_AI_AGENT_SUB_PROCESS.equals(modelerTemplate)) {
      return AgentDefinitionType.AI_AGENT_SUB_PROCESS;
    } else if (MODELER_TEMPLATE_AI_AGENT_TASK.equals(modelerTemplate)) {
      return AgentDefinitionType.AI_AGENT_TASK;
    }
    // neither a recognized template nor an explicit marker: not an agent element, or a
    // connector template that isn't (yet) mapped to an AgentDefinitionType
    return AgentDefinitionType.UNSPECIFIED;
  }
}
