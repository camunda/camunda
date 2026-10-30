/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAgentDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAgentType;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class AgentElementTypeTransformerTest {

  private final AgentElementTypeTransformer transformer = new AgentElementTypeTransformer();

  private static Stream<Arguments> elements() {
    return Stream.of(
        Arguments.of(Named.of("service task", new ExecutableJobWorkerTask("serviceTask"))),
        Arguments.of(
            Named.of("ad-hoc sub-process", new ExecutableAdHocSubProcess("adHocSubProcess"))));
  }

  @Test
  void shouldUseExplicitMarkerForAiAgentSubProcess() {
    // given
    final var element = new ExecutableAdHocSubProcess("adHocSubProcess");
    final var agentDefinition = agentDefinitionWithType(ZeebeAgentType.aiAgentSubProcess);

    // when
    transformer.transform(element, agentDefinition);

    // then
    assertThat(element.getAgentDefinitionType())
        .describedAs("Should map the aiAgentSubProcess marker to AI_AGENT_SUB_PROCESS")
        .isEqualTo(AgentDefinitionType.AI_AGENT_SUB_PROCESS);
  }

  @Test
  void shouldUseExplicitMarkerForAiAgentTask() {
    // given
    final var element = new ExecutableJobWorkerTask("serviceTask");
    final var agentDefinition = agentDefinitionWithType(ZeebeAgentType.aiAgentTask);

    // when
    transformer.transform(element, agentDefinition);

    // then
    assertThat(element.getAgentDefinitionType())
        .describedAs("Should map the aiAgentTask marker to AI_AGENT_TASK")
        .isEqualTo(AgentDefinitionType.AI_AGENT_TASK);
  }

  @ParameterizedTest(
      name = "AgentDefinitionType for {0} is EXTERNAL_AGENT when the explicit marker is set")
  @MethodSource("elements")
  void shouldUseExplicitMarkerForExternal(final ExecutableJobWorkerElement element) {
    // given
    final var agentDefinition = agentDefinitionWithType(ZeebeAgentType.external);

    // when
    transformer.transform(element, agentDefinition);

    // then
    assertThat(element.getAgentDefinitionType())
        .describedAs("Should map the external marker to EXTERNAL_AGENT")
        .isEqualTo(AgentDefinitionType.EXTERNAL_AGENT);
  }

  @ParameterizedTest(name = "AgentDefinitionType for {0} stays UNSPECIFIED without a marker")
  @MethodSource("elements")
  void shouldStayUnspecifiedWithoutMarker(final ExecutableJobWorkerElement element) {
    // when
    transformer.transform(element, null);

    // then
    assertThat(element.getAgentDefinitionType())
        .describedAs("Should stay UNSPECIFIED when no explicit marker is present")
        .isEqualTo(AgentDefinitionType.UNSPECIFIED);
  }

  private static ZeebeAgentDefinition agentDefinitionWithType(final ZeebeAgentType agentType) {
    final var agentDefinition = Mockito.mock(ZeebeAgentDefinition.class);
    Mockito.when(agentDefinition.getAgentType()).thenReturn(agentType);
    return agentDefinition;
  }
}
