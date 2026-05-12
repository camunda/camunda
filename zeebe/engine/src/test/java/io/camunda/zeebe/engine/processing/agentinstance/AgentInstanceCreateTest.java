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
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
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
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                    .endEvent()
                    .done())
            .deploy();
    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var serviceTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst();

    // when
    final var command =
        new AgentInstanceRecord().setElementInstanceKey(serviceTaskInstance.getKey());
    ENGINE.writeRecords(RecordToWrite.command().agentInstance(AgentInstanceIntent.CREATE, command));

    // then
    final var created =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(created.getValue().getElementInstanceKey()).isEqualTo(serviceTaskInstance.getKey());
    assertThat(created.getValue().getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(created.getValue().getElementId()).isEqualTo(SERVICE_TASK_ID);
    assertThat(created.getValue().getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(created.getValue().getProcessDefinitionVersion()).isEqualTo(1);
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

    assertThat(created.getValue().getChangedAttributes()).isEmpty();
  }

  @Test
  public void shouldRejectWhenElementInstanceNotFound() {
    // given
    final var nonExistingElementInstanceKey = 123456789L;

    // when
    final var command =
        new AgentInstanceRecord().setElementInstanceKey(nonExistingElementInstanceKey);
    ENGINE.writeRecords(RecordToWrite.command().agentInstance(AgentInstanceIntent.CREATE, command));

    // then
    final Record<?> rejection =
        RecordingExporter.agentInstanceRecords()
            .onlyCommandRejections()
            .withIntent(AgentInstanceIntent.CREATE)
            .getFirst();

    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason())
        .contains(String.valueOf(nonExistingElementInstanceKey));
  }
}
