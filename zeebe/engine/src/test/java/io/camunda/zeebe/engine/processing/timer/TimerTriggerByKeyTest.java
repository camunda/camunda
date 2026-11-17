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
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * A client-issued trigger command addresses a waiting timer by its key (the engine side of {@code
 * POST /v2/timers/{timerKey}/trigger}) and fires it now, without its due date passing. This is what
 * lets a recording tool step a timer catch event on a cluster whose clock it does not control.
 */
public final class TimerTriggerByKeyTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldFireTimerWhenTriggeredByKey() {
    // given an instance whose timer is not due yet
    final String processId = deployTimerCatchProcess();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final Record<TimerRecordValue> created =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when a client-issued command addresses the timer by its key, as
    // POST /v2/timers/{timerKey}/trigger does
    final var command =
        RecordToWrite.command().timer(TimerIntent.TRIGGER, triggerByKey()).key(created.getKey());
    command.recordMetadata().requestId(1L).requestStreamId(1);
    ENGINE.writeRecords(command);

    // then the timer fires without its due date passing, completing the instance
    final Record<TimerRecordValue> triggered =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(triggered.getKey()).isEqualTo(created.getKey());
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  @Test
  public void shouldRejectTriggerForUnknownTimerKey() {
    // when a client-issued command addresses a timer key that does not exist
    final var command =
        RecordToWrite.command().timer(TimerIntent.TRIGGER, triggerByKey()).key(888_888L);
    command.recordMetadata().requestId(1L).requestStreamId(1);
    ENGINE.writeRecords(command);

    // then the command is rejected as not found
    final Record<TimerRecordValue> rejection =
        RecordingExporter.timerRecords(TimerIntent.TRIGGER)
            .onlyCommandRejections()
            .filter(r -> r.getKey() == 888_888L)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectClientTriggerByKeyWhileInstanceIsSuspended() {
    // given a suspended instance whose timer is still waiting
    final String processId = deployTimerCatchProcess();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final Record<TimerRecordValue> created =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when a client-issued trigger arrives for the timer
    final var command =
        RecordToWrite.command().timer(TimerIntent.TRIGGER, triggerByKey()).key(created.getKey());
    command.recordMetadata().requestId(1L).requestStreamId(1);
    ENGINE.writeRecords(command);

    // then it is rejected rather than buffered, so the caller is not left waiting for a resume
    final Record<TimerRecordValue> rejection =
        RecordingExporter.timerRecords(TimerIntent.TRIGGER)
            .onlyCommandRejections()
            .filter(r -> r.getKey() == created.getKey())
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
  }

  private static TimerRecord triggerByKey() {
    return new TimerRecord()
        .setElementInstanceKey(-1L)
        .setProcessInstanceKey(-1L)
        .setProcessDefinitionKey(-1L)
        .setDueDate(-1L)
        .setRepetitions(0)
        .setTargetElementId(BufferUtil.wrapString(""));
  }

  private static String deployTimerCatchProcess() {
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT1H"))
                .endEvent()
                .done())
        .deploy();
    return processId;
  }
}
