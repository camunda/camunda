/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class AgentDefinitionJobHeaderTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldIncludeAgentDefinitionKeyHeaderForServiceTaskWithAgentDefinition() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("jobType").zeebeAiAgentTaskDefinition())
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var agentDefinitionKey =
        RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
            .withBpmnProcessId(processId)
            .getFirst()
            .getValue()
            .getAgentDefinitionKey();

    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(jobCreated.getValue().getCustomHeaders())
        .contains(
            entry(Protocol.AGENT_DEFINITION_KEY_HEADER_NAME, String.valueOf(agentDefinitionKey)));
  }

  @Test
  public void shouldNotIncludeAgentDefinitionKeyHeaderForServiceTaskWithoutAgentDefinition() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("jobType"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(jobCreated.getValue().getCustomHeaders())
        .doesNotContainKey(Protocol.AGENT_DEFINITION_KEY_HEADER_NAME);
  }

  @Test
  public void shouldIncludeAgentDefinitionKeyHeaderForAdHocSubProcessWithAgentDefinition() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .adHocSubProcess(
                    "ad-hoc",
                    ahsp ->
                        ahsp.zeebeJobType("jobType")
                            .zeebeAiAgentSubProcessDefinition()
                            .task("inner"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var agentDefinitionKey =
        RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
            .withBpmnProcessId(processId)
            .getFirst()
            .getValue()
            .getAgentDefinitionKey();

    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("ad-hoc")
            .getFirst();

    assertThat(jobCreated.getValue().getCustomHeaders())
        .contains(
            entry(Protocol.AGENT_DEFINITION_KEY_HEADER_NAME, String.valueOf(agentDefinitionKey)));
  }

  @Test
  public void shouldNotIncludeAgentDefinitionKeyHeaderForAdHocSubProcessWithoutAgentDefinition() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .adHocSubProcess("ad-hoc", ahsp -> ahsp.zeebeJobType("jobType").task("inner"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("ad-hoc")
            .getFirst();

    assertThat(jobCreated.getValue().getCustomHeaders())
        .doesNotContainKey(Protocol.AGENT_DEFINITION_KEY_HEADER_NAME);
  }

  @Test
  public void
      shouldIncludeAgentDefinitionKeyHeaderForExecutionListenerJobOnAgentMarkedServiceTask() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType("jobType")
                            .zeebeAiAgentTaskDefinition()
                            .zeebeStartExecutionListener("listenerJobType"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var agentDefinitionKey =
        RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
            .withBpmnProcessId(processId)
            .getFirst()
            .getValue()
            .getAgentDefinitionKey();

    final var listenerJobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("listenerJobType")
            .getFirst();

    assertThat(listenerJobCreated.getValue().getCustomHeaders())
        .contains(
            entry(Protocol.AGENT_DEFINITION_KEY_HEADER_NAME, String.valueOf(agentDefinitionKey)));
  }

  @Test
  public void
      shouldIncludeAgentDefinitionKeyHeaderForBeforeAllListenerOnMultiInstanceAgentServiceTask() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType("jobType")
                            .zeebeAiAgentTaskDefinition()
                            .zeebeBeforeAllExecutionListener("beforeAllJobType")
                            .multiInstance(
                                m ->
                                    m.zeebeInputCollectionExpression("items")
                                        .zeebeInputElement("item")))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("items", List.of(1))
            .create();

    // then
    final var agentDefinitionKey =
        RecordingExporter.agentDefinitionRecords(AgentDefinitionIntent.CREATED)
            .withBpmnProcessId(processId)
            .getFirst()
            .getValue()
            .getAgentDefinitionKey();

    final var beforeAllJobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("beforeAllJobType")
            .getFirst();

    assertThat(beforeAllJobCreated.getValue().getCustomHeaders())
        .contains(
            entry(Protocol.AGENT_DEFINITION_KEY_HEADER_NAME, String.valueOf(agentDefinitionKey)));
  }
}
