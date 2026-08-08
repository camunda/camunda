/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers the core detection-to-emission story for {@code AgentDefinition:CREATED} at deploy time:
 * an element carrying a recognized agent marker (explicit {@code zeebe:agentDefinition}, or a
 * {@code zeebe:modelerTemplate} fallback) mints exactly one {@code AgentDefinition} per deployed
 * process version, while an unmarked element mints nothing.
 *
 * <p>These minting scenarios are red until {@code AgentDefinitionTransformer} is wired into {@code
 * BpmnResourceTransformer.writeRecords} to actually mint and emit the records asserted here.
 */
public final class AgentDefinitionDeploymentTest {

  /** {@code zeebe:modelerTemplate} id of the Agentic AI service task connector template. */
  private static final String MODELER_TEMPLATE_AI_AGENT_TASK =
      "io.camunda.connectors.agenticai.aiagent.v1";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateAgentDefinitionForElementWithExplicitMarker() {
    // given
    final var processId = "process-explicit-marker";
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

    Assertions.assertThat(agentDefinitionRecord)
        .describedAs(
            "Should key the AgentDefinition:CREATED record with its own agentDefinitionKey")
        .hasKey(agentDefinitionRecord.getValue().getAgentDefinitionKey());

    Assertions.assertThat(agentDefinitionRecord.getValue())
        .describedAs("Should populate every field of the AgentDefinition from the explicit marker")
        .hasAgentType(AgentDefinitionType.AI_AGENT_SUB_PROCESS)
        .hasName("AI Agent")
        .hasElementId(elementId)
        .hasBpmnProcessId(processId)
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasProcessDefinitionVersion(1)
        .hasProcessDefinitionVersionTag("")
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertAgentDefinitionCreatedImmediatelyBeforeProcessCreated(processDefinitionKey);
  }

  @Test
  public void shouldCreateAgentDefinitionViaModelerTemplateFallback() {
    // given
    final var processId = "process-modeler-template-fallback";
    final var elementId = "agent-task";

    // when — no explicit zeebe:agentDefinition marker, only a recognized modelerTemplate
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
                                .zeebeModelerTemplate(MODELER_TEMPLATE_AI_AGENT_TASK))
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
            "Should resolve AI_AGENT_TASK via the modelerTemplate fallback through the full"
                + " deployment pipeline, not just the isolated transformer unit")
        .hasAgentType(AgentDefinitionType.AI_AGENT_TASK)
        .hasName("AI Agent Task")
        .hasElementId(elementId)
        .hasBpmnProcessId(processId)
        .hasProcessDefinitionKey(processDefinitionKey);
  }

  @Test
  public void shouldNotCreateAgentDefinitionForElementWithoutAgentMarker() {
    // given — a plain service task, an ad-hoc sub-process with no marker, and a service task with
    // an unrecognized modelerTemplate: none of these are agent definitions
    final var processId = "process-unmarked";

    // when
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("plain-task", t -> t.zeebeJobType("plain-task-job"))
                    .serviceTask(
                        "unrecognized-template-task",
                        t ->
                            t.zeebeJobType("unrecognized-template-task-job")
                                .zeebeModelerTemplate("some.other.connector.template.v3"))
                    .adHocSubProcess("plain-ad-hoc", ahsp -> ahsp.task("inner"))
                    .endEvent()
                    .done())
            .deploy();
    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // then
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.agentDefinitionRecords()
                        .withProcessDefinitionKey(processDefinitionKey)
                        .exists()))
        .describedAs("Should not create any AgentDefinition for a process without an agent marker")
        .isFalse();
  }

  @Test
  public void shouldCreateNewAgentDefinitionOnRedeploy() {
    // given
    final var processId = "process-redeploy";
    final var elementId = "agent-task";

    final var firstDeployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        elementId,
                        t -> t.zeebeJobType("agent-task-job-v1").zeebeAiAgentTaskDefinition())
                    .endEvent()
                    .done())
            .deploy();
    final var processDefinitionKeyV1 =
        firstDeployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();
    final var agentDefinitionKeyV1 =
        RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKeyV1)
            .getFirst()
            .getValue()
            .getAgentDefinitionKey();

    // when — redeploy with a changed resource to force a new process version
    final var secondDeployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        elementId,
                        t -> t.zeebeJobType("agent-task-job-v2").zeebeAiAgentTaskDefinition())
                    .endEvent()
                    .done())
            .deploy();
    final var processDefinitionKeyV2 =
        secondDeployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // then
    assertThat(processDefinitionKeyV2)
        .describedAs("Should assign a new processDefinitionKey to the redeployed process")
        .isNotEqualTo(processDefinitionKeyV1);

    final var agentDefinitionRecordV2 =
        RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKeyV2)
            .getFirst();

    Assertions.assertThat(agentDefinitionRecordV2.getValue())
        .describedAs("Should mint a new AgentDefinition for the new process version")
        .hasBpmnProcessId(processId)
        .hasProcessDefinitionKey(processDefinitionKeyV2)
        .hasProcessDefinitionVersion(2)
        .hasElementId(elementId)
        .describedAs(
            "Should fall back to the elementId as the AgentDefinition name when the element itself has no name")
        .hasName(elementId);

    assertThat(agentDefinitionRecordV2.getValue().getAgentDefinitionKey())
        .describedAs(
            "Should mint a distinct agentDefinitionKey for the redeployed process's"
                + " AgentDefinition")
        .isNotEqualTo(agentDefinitionKeyV1);
  }

  private static void assertAgentDefinitionCreatedImmediatelyBeforeProcessCreated(
      final long processDefinitionKey) {
    final var recordsUpToProcessCreated =
        RecordingExporter.records()
            .onlyEvents()
            .withValueTypes(ValueType.AGENT_DEFINITION, ValueType.PROCESS)
            .limit(
                r -> r.getIntent() == ProcessIntent.CREATED && r.getKey() == processDefinitionKey)
            .asList();

    assertThat(recordsUpToProcessCreated)
        .describedAs("Should emit AgentDefinition:CREATED immediately before ProcessIntent.CREATED")
        .extracting(Record::getValueType, Record::getIntent)
        .endsWith(
            tuple(ValueType.AGENT_DEFINITION, AgentDefinitionIntent.CREATED),
            tuple(ValueType.PROCESS, ProcessIntent.CREATED));
  }
}
