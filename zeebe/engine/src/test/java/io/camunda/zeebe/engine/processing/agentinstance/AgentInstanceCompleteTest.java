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
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit-level coverage for the {@code AGENT_INSTANCE:COMPLETE} command/event round trip, driven
 * directly against a deployed agent instance rather than through a process instance lifecycle —
 * mirroring how {@code AgentHistoryDiscardTest} exercises {@code AgentHistoryDiscardProcessor}
 * directly without needing {@code BpmnJobBehavior}'s emit site. The end-to-end trigger point
 * (process instance completion/cancellation) is covered separately.
 */
public class AgentInstanceCompleteTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "service-task";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCompleteAgentInstanceAndSetStatusCompleted() {
    // given
    final var agentInstanceKey = deployAndCreateAgentInstance();

    // when
    final var completed = ENGINE.agentInstances().withAgentInstanceKey(agentInstanceKey).complete();

    // then
    assertThat(completed.getKey()).isEqualTo(agentInstanceKey);
    assertThat(completed.getValue().getStatus()).isEqualTo(AgentInstanceStatus.COMPLETED);
  }

  @Test
  public void shouldDeleteAgentInstanceFromStateOnCompleted() {
    // given
    final var agentInstanceKey = deployAndCreateAgentInstance();

    // when
    ENGINE.agentInstances().withAgentInstanceKey(agentInstanceKey).complete();

    // then
    assertThat(ENGINE.getProcessingState().getAgentInstanceState().getRecord(agentInstanceKey))
        .isNull();
  }

  @Test
  public void shouldRejectCompleteWhenAgentInstanceDoesNotExist() {
    // given
    final long unknownAgentInstanceKey = 9999L;

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(unknownAgentInstanceKey)
            .expectRejection()
            .complete();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason()).contains(String.valueOf(unknownAgentInstanceKey));
  }

  private long deployAndCreateAgentInstance() {
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
    return ENGINE
        .agentInstances()
        .withElementInstanceKey(serviceTaskInstance.getKey())
        .create()
        .getValue()
        .getAgentInstanceKey();
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
