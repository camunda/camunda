/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
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
  }

  @Test
  public void shouldCancelSuspendedProcessInstanceWithBufferedCommands() {
    // given - a timer that becomes due while suspended buffers its trigger
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT1S"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    ENGINE.increaseTime(Duration.ofSeconds(1));
    RecordingExporter.records()
        .withValueType(ValueType.BUFFERED_COMMAND)
        .withIntent(BufferedCommandIntent.BUFFERED)
        .filter(
            r ->
                ((BufferedCommandRecordValue) r.getValue()).getProcessInstanceKey()
                    == processInstanceKey)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then - the buffered trigger does not hold up the cancellation. That terminating the root
    // clears its buffered commands is covered by SuspensionAppliersTest
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(processId)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTerminateSuspendedCallActivityChildInstanceOnCascadedTermination() {
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

    // then - that it also clears the child's suspension marker is covered by
    // SuspensionAppliersTest
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(childInstanceKey)
                .withBpmnProcessId(childProcessId)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }
}
