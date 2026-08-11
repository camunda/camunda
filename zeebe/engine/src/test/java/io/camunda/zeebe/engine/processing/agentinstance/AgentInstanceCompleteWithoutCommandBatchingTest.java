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
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers batch completion with the engine's general command-batching effectively disabled ({@code
 * maxCommandsInBatch(1)}, mirroring {@code MigrateProcessInstanceConcurrentNoBatchingTest}), so
 * each self-chained {@code COMPLETE} command is dequeued in its own processing round instead of
 * being reprocessed eagerly within the same round as the one before it.
 */
public class AgentInstanceCompleteWithoutCommandBatchingTest {

  @ClassRule
  public static final EngineRule ENGINE = EngineRule.singlePartition().maxCommandsInBatch(1);

  private static final String PROCESS_ID = "process";
  private static final String FIRST_TASK_ID = "first-agent-task";
  private static final String SECOND_TASK_ID = "second-agent-task";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCompleteAgentInstancesOfProcessInstanceAcrossMultipleRounds() {
    // given — two agent instances belonging to the same process instance; with command batching
    // disabled, completing both requires the batch command to be dequeued and processed in two
    // separate rounds, each with its own source record position
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .parallelGateway("fork")
                .serviceTask(
                    FIRST_TASK_ID, t -> t.zeebeJobType("agent").zeebeAiAgentTaskDefinition())
                .parallelGateway("join")
                .endEvent()
                .moveToNode("fork")
                .serviceTask(
                    SECOND_TASK_ID, t -> t.zeebeJobType("other-agent").zeebeAiAgentTaskDefinition())
                .connectTo("join")
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var firstTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(FIRST_TASK_ID)
            .getFirst();
    final var secondTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SECOND_TASK_ID)
            .getFirst();
    final var firstAgentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(firstTaskInstance.getKey())
            .create()
            .getKey();
    final var secondAgentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(secondTaskInstance.getKey())
            .create()
            .getKey();

    // when — a single batch-completion command is issued for the process instance
    final var rejection =
        ENGINE.agentInstances().withProcessInstanceKey(processInstanceKey).complete();

    // then — the client's wait resolves to the closing rejection even though it took multiple
    // separate rounds to get there, since it isn't tied to the initial command's source position
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);

    // and — both agent instances were completed along the way
    assertThat(
            RecordingExporter.agentInstanceRecords(AgentInstanceIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2)
                .map(Record::getKey))
        .describedAs("Both agent instances are completed across separate processing rounds")
        .containsExactlyInAnyOrder(firstAgentInstanceKey, secondAgentInstanceKey);
  }
}
