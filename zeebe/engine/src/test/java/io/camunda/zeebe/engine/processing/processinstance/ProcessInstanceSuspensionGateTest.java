/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.ArrayList;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ProcessInstanceSuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCancelProcessInstanceWhileSuspended() {
    // given - cancellation (PROCESS category) must be able to complete on a suspended instance
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().userTask().endEvent().done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    final Record<ProcessInstanceRecordValue> terminated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(processId)
            .getFirst();
    Assertions.assertThat(terminated.getValue()).hasProcessInstanceKey(processInstanceKey);
    assertThat(ENGINE.getProcessingState().getSuspensionState().isSuspended(processInstanceKey))
        .describedAs("terminating a suspended root must clear its suspension marker")
        .isFalse();
  }

  @Test
  public void shouldClearBufferedCommandsWhenCancellingSuspendedProcessInstance() {
    // given - a suspended instance with a buffered command left over from while it was SUSPENDED
    // (seeded directly: buffering a real internal command is exercised by other suites, this test
    // only needs a buffered entry to exist to verify termination cleans it up)
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().userTask().endEvent().done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    final var suspensionState =
        ((MutableProcessingState) ENGINE.getProcessingState()).getSuspensionState();
    final long bufferedCommandKey = Long.MAX_VALUE - processInstanceKey;
    suspensionState.bufferCommand(
        bufferedCommandKey,
        new BufferedCommandRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setCommandKey(bufferedCommandKey));

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then - no orphan buffered command remains for the now-terminated root instance
    final List<Long> remainingBufferedCommands = new ArrayList<>();
    suspensionState.visitBufferedCommands(
        processInstanceKey, (key, command) -> remainingBufferedCommands.add(key));
    assertThat(remainingBufferedCommands)
        .describedAs("terminating a suspended root must clear its buffered commands")
        .isEmpty();
  }

  @Test
  public void shouldClearSuspensionStateForCallActivityChildInstanceOnCascadedTermination() {
    // given - a call activity child instance, suspended independently of its parent (suspension
    // does not cascade), then terminated as a side effect of the parent being cancelled
    final String parentProcessId = Strings.newRandomValidBpmnId();
    final String childProcessId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId).startEvent().userTask().endEvent().done())
        .deploy();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .deploy();
    final long parentInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();
    final long childInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withBpmnProcessId(childProcessId)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();
    ENGINE.processInstance().withInstanceKey(childInstanceKey).suspend();

    // when - cancelling the root cascades termination down into the suspended child instance
    ENGINE.processInstance().withInstanceKey(parentInstanceKey).cancel();

    // then
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
        .withProcessInstanceKey(childInstanceKey)
        .withBpmnProcessId(childProcessId)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst();
    assertThat(ENGINE.getProcessingState().getSuspensionState().isSuspended(childInstanceKey))
        .describedAs(
            "terminating a suspended call activity child instance must clear its suspension"
                + " marker too, since suspension is not restricted to top-level root instances")
        .isFalse();
  }
}
