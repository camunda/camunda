/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Pins down that an agent-marked element still creates its {@code AgentDefinition} when it is also
 * configured as a multi-instance activity.
 */
public final class AgentDefinitionMultiInstanceDeploymentTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateAgentDefinitionForMultiInstanceServiceTask() {
    // given
    final var processId = "process-multi-instance-service-task";
    final var elementId = "agent-task";

    // when
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        elementId,
                        t ->
                            t.name("AI Agent Task")
                                .zeebeJobType("agent-task-job")
                                .zeebeAiAgentTaskDefinition()
                                .multiInstance(m -> m.zeebeInputCollectionExpression("items")))
                    .endEvent()
                    .done())
            .deploy();
    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // then
    final var agentDefinitionRecord =
        RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst();

    Assertions.assertThat(agentDefinitionRecord.getValue())
        .describedAs(
            "Should create an AgentDefinition for the original service task, not its wrapping"
                + " multi-instance body")
        .hasAgentType(AgentDefinitionType.AI_AGENT_TASK)
        .hasName("AI Agent Task")
        .hasElementId(elementId)
        .hasBpmnProcessId(processId)
        .hasProcessDefinitionKey(processDefinitionKey);
  }

  @Test
  public void shouldCreateAgentDefinitionForMultiInstanceAdHocSubProcess() {
    // given
    final var processId = "process-multi-instance-ad-hoc-sub-process";
    final var elementId = "agent-sub-process";

    // when
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .adHocSubProcess(
                        elementId,
                        ahsp ->
                            ahsp.name("AI Agent")
                                .zeebeJobType("agent-sub-process-job")
                                .zeebeAiAgentSubProcessDefinition()
                                .multiInstance(m -> m.zeebeInputCollectionExpression("items"))
                                .task("inner"))
                    .endEvent()
                    .done())
            .deploy();
    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // then
    final var agentDefinitionRecord =
        RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst();

    Assertions.assertThat(agentDefinitionRecord.getValue())
        .describedAs(
            "Should create an AgentDefinition for the original ad-hoc sub-process, not its"
                + " wrapping multi-instance body")
        .hasAgentType(AgentDefinitionType.AI_AGENT_SUB_PROCESS)
        .hasName("AI Agent")
        .hasElementId(elementId)
        .hasBpmnProcessId(processId)
        .hasProcessDefinitionKey(processDefinitionKey);
  }
}
