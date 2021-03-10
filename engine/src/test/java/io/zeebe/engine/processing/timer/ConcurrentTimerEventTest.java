/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.timer;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.engine.util.RecordToWrite;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.TimerRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ConcurrentTimerEventTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private long processInstanceKey;
  private Record<TimerRecordValue> timerCreated;

  @Before
  public void setup() {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .intermediateCatchEvent("timer", e -> e.timerWithDuration("PT10S"))
                .done())
        .deploy();

    processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE.stop();
  }

  @Test
  public void shouldRejectTriggerCommandIfTimerIsCanceled() {
    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .timer(TimerIntent.CANCEL, timerCreated.getValue())
            .key(timerCreated.getKey()),
        RecordToWrite.command()
            .timer(TimerIntent.TRIGGER, timerCreated.getValue())
            .key(timerCreated.getKey()));

    ENGINE.start();

    // then
    final var rejection =
        RecordingExporter.timerRecords(TimerIntent.TRIGGER)
            .onlyCommandRejections()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectTriggerCommandIfTimerIsTriggered() {
    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .timer(TimerIntent.TRIGGER, timerCreated.getValue())
            .key(timerCreated.getKey()),
        RecordToWrite.command()
            .timer(TimerIntent.TRIGGER, timerCreated.getValue())
            .key(timerCreated.getKey()));

    ENGINE.start();

    // then
    final var rejection =
        RecordingExporter.timerRecords(TimerIntent.TRIGGER)
            .onlyCommandRejections()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectCancelCommandIfTimerIsTriggered() {
    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .timer(TimerIntent.TRIGGER, timerCreated.getValue())
            .key(timerCreated.getKey()),
        RecordToWrite.command()
            .timer(TimerIntent.CANCEL, timerCreated.getValue())
            .key(timerCreated.getKey()));

    ENGINE.start();

    // then
    final var rejection =
        RecordingExporter.timerRecords(TimerIntent.CANCEL)
            .onlyCommandRejections()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectTriggerCommandIfElementInstanceIsLeft() {
    // given
    final var processActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final var eventActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT)
            .getFirst();

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.CANCEL, processActivated.getValue())
            .key(processActivated.getKey()),
        RecordToWrite.event()
            .processInstance(ProcessInstanceIntent.ELEMENT_TERMINATING, processActivated.getValue())
            .key(processActivated.getKey())
            .causedBy(0),
        RecordToWrite.event()
            .processInstance(ProcessInstanceIntent.ELEMENT_TERMINATING, eventActivated.getValue())
            .key(eventActivated.getKey())
            .causedBy(1),
        RecordToWrite.command()
            .timer(TimerIntent.TRIGGER, timerCreated.getValue())
            .key(timerCreated.getKey()));

    ENGINE.start();

    // then
    final var rejection =
        RecordingExporter.timerRecords()
            .onlyCommandRejections()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
  }
}
