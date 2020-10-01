/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.timer;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.engine.util.RecordToWrite;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
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

  private long workflowInstanceKey;
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

    workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("process").create();

    timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
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
            .withWorkflowInstanceKey(workflowInstanceKey)
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
            .withWorkflowInstanceKey(workflowInstanceKey)
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
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    Assertions.assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectTriggerCommandIfElementInstanceIsLeft() {
    // given
    final var processActivated =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final var eventActivated =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT)
            .getFirst();

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .workflowInstance(WorkflowInstanceIntent.CANCEL, processActivated.getValue())
            .key(processActivated.getKey()),
        RecordToWrite.event()
            .workflowInstance(
                WorkflowInstanceIntent.ELEMENT_TERMINATING, processActivated.getValue())
            .key(processActivated.getKey())
            .causedBy(0),
        RecordToWrite.event()
            .workflowInstance(WorkflowInstanceIntent.ELEMENT_TERMINATING, eventActivated.getValue())
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
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
  }
}
