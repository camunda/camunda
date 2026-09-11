/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.timer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class TimerSuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldBufferDueTimerAndFireOnResume() {
    // given
    final long processInstanceKey = deployAndStartProcessWithTimer(Strings.newRandomValidBpmnId());
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    ENGINE.increaseTime(Duration.ofSeconds(1));

    // then
    RecordingExporter.timerRecords(TimerIntent.SUSPENDED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then
    final var timerResumed =
        RecordingExporter.timerRecords(TimerIntent.RESUMED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final var triggered =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final var resumed =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.RESUMED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(timerResumed.getPosition()).isLessThan(triggered.getPosition());
    assertThat(triggered.getPosition()).isLessThan(resumed.getPosition());
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
    assertThat(bufferedTimerTriggerCount(processInstanceKey)).isEqualTo(1);
  }

  @Test
  public void shouldNotFireTwiceWhenDuplicateTriggerIsBufferedWhileSuspended() {
    // given
    final long processInstanceKey = deployAndStartProcessWithTimer(Strings.newRandomValidBpmnId());
    final var created =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    ENGINE.increaseTime(Duration.ofSeconds(1));
    RecordingExporter.timerRecords(TimerIntent.SUSPENDED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when - a second TRIGGER is also buffered; the due-date index is already gone
    ENGINE.writeRecords(
        RecordToWrite.command()
            .timer(TimerIntent.TRIGGER, created.getValue())
            .key(created.getKey()));
    RecordingExporter.records()
        .filter(
            r ->
                r.getValueType() == ValueType.BUFFERED_COMMAND
                    && r.getIntent() == BufferedCommandIntent.BUFFERED
                    && ((BufferedCommandRecordValue) r.getValue()).getProcessInstanceKey()
                        == processInstanceKey
                    && ((BufferedCommandRecordValue) r.getValue()).getIntent()
                        == TimerIntent.TRIGGER)
        .skip(1)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - only the first drained TRIGGER fires; the duplicate is NOT_FOUND
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .timerRecords()
                .withIntent(TimerIntent.TRIGGERED)
                .filter(r -> r.getValue().getProcessInstanceKey() == processInstanceKey)
                .count())
        .isEqualTo(1);
    final var duplicateRejection =
        RecordingExporter.timerRecords(TimerIntent.TRIGGER)
            .onlyCommandRejections()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(duplicateRejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldFireTimerThatBecomesDueAfterResume() {
    // given
    final long processInstanceKey = deployAndStartProcessWithTimer(Strings.newRandomValidBpmnId());
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // when
    ENGINE.increaseTime(Duration.ofSeconds(1));

    // then
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .timerRecords()
                .withIntent(TimerIntent.SUSPENDED)
                .count())
        .isZero();
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .timerRecords()
                .withIntent(TimerIntent.RESUMED)
                .count())
        .isZero();
  }

  @Test
  public void shouldCancelSuspendedTimer() {
    // given
    final long processInstanceKey = deployAndStartProcessWithTimer(Strings.newRandomValidBpmnId());
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    ENGINE.increaseTime(Duration.ofSeconds(1));
    RecordingExporter.timerRecords(TimerIntent.SUSPENDED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    RecordingExporter.timerRecords(TimerIntent.CANCELED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  @Test
  public void shouldBufferRepeatingTimerOnceWhileSuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(processId))
                .boundaryEvent("timer", b -> b.cancelActivity(false).timerWithCycle("R/PT1S"))
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    ENGINE.increaseTime(Duration.ofSeconds(5));
    RecordingExporter.timerRecords(TimerIntent.SUSPENDED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // then - no fire before the first due trigger is buffered
    assertThat(
            RecordingExporter.records()
                .limit(
                    r ->
                        r.getValueType() == ValueType.TIMER
                            && r.getIntent() == TimerIntent.SUSPENDED
                            && ((TimerRecordValue) r.getValue()).getProcessInstanceKey()
                                == processInstanceKey)
                .timerRecords()
                .withIntent(TimerIntent.TRIGGERED)
                .filter(r -> r.getValue().getProcessInstanceKey() == processInstanceKey)
                .count())
        .isZero();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();
    RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.job().ofInstance(processInstanceKey).withType(processId).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then - later cycles are not buffered while suspended
    assertThat(bufferedTimerTriggerCount(processInstanceKey)).isEqualTo(1);
  }

  private long bufferedTimerTriggerCount(final long processInstanceKey) {
    return RecordingExporter.records()
        .limitToProcessInstance(processInstanceKey)
        .filter(
            r ->
                r.getValueType() == ValueType.BUFFERED_COMMAND
                    && r.getIntent() == BufferedCommandIntent.BUFFERED
                    && ((BufferedCommandRecordValue) r.getValue()).getProcessInstanceKey()
                        == processInstanceKey
                    && ((BufferedCommandRecordValue) r.getValue()).getIntent()
                        == TimerIntent.TRIGGER)
        .count();
  }

  private long deployAndStartProcessWithTimer(final String processId) {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT1S"))
                .endEvent()
                .done())
        .deploy();
    return ENGINE.processInstance().ofBpmnProcessId(processId).create();
  }
}
