/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class AgentInstanceCreateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "service-task";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldEmitCreatedEventForValidCreateCommand() {
    // given
    final var processMetadata =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);

    // when
    final var created =
        ENGINE.agentInstances().withElementInstanceKey(serviceTaskInstance.getKey()).create();

    // then
    assertThat(created.getValue().getElementInstanceKey()).isEqualTo(serviceTaskInstance.getKey());
    assertThat(created.getValue().getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(created.getValue().getElementId()).isEqualTo(SERVICE_TASK_ID);
    assertThat(created.getValue().getProcessDefinitionKey())
        .isEqualTo(processMetadata.getProcessDefinitionKey());
    assertThat(created.getValue().getProcessDefinitionVersion())
        .isEqualTo(processMetadata.getVersion());
    assertThat(created.getValue().getTenantId())
        .isEqualTo(serviceTaskInstance.getValue().getTenantId());
    assertThat(created.getValue().getStatus()).isEqualTo(AgentInstanceStatus.INITIALIZING);

    assertThat(created.getValue().getMetrics().getInputTokens()).isZero();
    assertThat(created.getValue().getMetrics().getOutputTokens()).isZero();
    assertThat(created.getValue().getMetrics().getModelCalls()).isZero();
    assertThat(created.getValue().getMetrics().getToolCalls()).isZero();

    assertThat(created.getValue().getTools()).isEmpty();

    assertThat(created.getValue().getLimits().getMaxTokens()).isEqualTo(-1L);
    assertThat(created.getValue().getLimits().getMaxModelCalls()).isEqualTo(-1);
    assertThat(created.getValue().getLimits().getMaxToolCalls()).isEqualTo(-1);
  }

  @Test
  public void shouldMaterializeIdentityFieldsFromElementInstance() {
    // given
    final var customElementId = "my-agent";
    final var processMetadata =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .serviceTask(customElementId, t -> t.zeebeJobType("agent"))
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(customElementId)
            .getFirst();

    // when
    final var created =
        ENGINE.agentInstances().withElementInstanceKey(elementInstance.getKey()).create();

    // then
    assertThat(created.getValue().getElementInstanceKey()).isEqualTo(elementInstance.getKey());
    assertThat(created.getValue().getElementId()).isEqualTo(customElementId);
    assertThat(created.getValue().getBpmnProcessId()).isEqualTo(PROCESS_ID);
    assertThat(created.getValue().getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(created.getValue().getProcessDefinitionKey())
        .isEqualTo(processMetadata.getProcessDefinitionKey());
    assertThat(created.getValue().getProcessDefinitionVersion())
        .isEqualTo(processMetadata.getVersion());
    assertThat(created.getValue().getTenantId())
        .isEqualTo(elementInstance.getValue().getTenantId());
  }

  @Test
  public void shouldFetchVersionTagFromProcessState() {
    // given
    final var versionTag = "v1.2.3";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .versionTag(versionTag)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);

    // when
    final var created =
        ENGINE.agentInstances().withElementInstanceKey(serviceTaskInstance.getKey()).create();

    // then
    assertThat(created.getValue().getVersionTag()).isEqualTo(versionTag);
  }

  @Test
  public void shouldMaterializeStatusInitializingOnCreate() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);

    // when -- the command tries to set a different status; engine must ignore it.
    final var created =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withStatus(AgentInstanceStatus.THINKING)
            .create();

    // then
    assertThat(created.getValue().getStatus()).isEqualTo(AgentInstanceStatus.INITIALIZING);
  }

  @Test
  public void shouldDefaultMetricsToZeroAndToolsEmpty() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);

    // when -- even when the command carries non-default metrics, the engine resets them.
    final var created =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withMetrics(50L, 25L, 5, 3)
            .create();

    // then
    assertThat(created.getValue().getMetrics().getInputTokens()).isZero();
    assertThat(created.getValue().getMetrics().getOutputTokens()).isZero();
    assertThat(created.getValue().getMetrics().getModelCalls()).isZero();
    assertThat(created.getValue().getMetrics().getToolCalls()).isZero();
    assertThat(created.getValue().getTools()).isEmpty();
  }

  private static Record<ProcessInstanceRecordValue> awaitServiceTaskActivated(
      final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withElementId(SERVICE_TASK_ID)
        .getFirst();
  }
}
