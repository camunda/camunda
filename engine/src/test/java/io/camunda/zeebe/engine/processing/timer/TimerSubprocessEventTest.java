/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.timer;

import static org.assertj.core.api.Assertions.assertThat;import io.camunda.zeebe.engine.util.EngineRule;import io.camunda.zeebe.model.bpmn.Bpmn;import io.camunda.zeebe.model.bpmn.BpmnModelInstance;import io.camunda.zeebe.protocol.record.Assertions;import io.camunda.zeebe.protocol.record.Record;import io.camunda.zeebe.protocol.record.intent.TimerIntent;import io.camunda.zeebe.protocol.record.value.TimerRecordValue;import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.BeforeClass;import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class TimerSubprocessEventTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final BpmnModelInstance SUB_CYCLE_MODEL =
      Bpmn.createExecutableProcess("SUB_CYCLE_MODEL")
          .eventSubProcess("sub-process",  subProcess ->
              subProcess
                  .startEvent("x")
                  .interrupting(false)
                  .timerWithCycle("R/PT1M")
                  .endEvent()
          )
          .startEvent()
          .serviceTask("service_1", b -> b.zeebeJobType("type"))
          .endEvent()
          .done();

  private static final BpmnModelInstance SUB_TIME_DATE_MODEL =
      Bpmn.createExecutableProcess("SUB_TIME_DATE_MODEL")
          .eventSubProcess("sub-process",  subProcess ->
              subProcess
                  .startEvent("other-timer")
                  .interrupting(false)
                  .timerWithDateExpression("now() + duration(\"PT1M\")")
                  .endEvent()
          )
          .startEvent()
          .serviceTask("service_1", b -> b.zeebeJobType("type"))
          .endEvent()
          .done();

  private static final BpmnModelInstance SUB_DURATION_MODEL =
      Bpmn.createExecutableProcess("SUB_DURATION_MODEL")
          .eventSubProcess("sub-process",  subProcess ->
              subProcess
                  .startEvent("other-timer")
                  .interrupting(false)
                  .timerWithDuration("PT1M")
                  .endEvent()
          )
          .startEvent()
          .serviceTask("service_1", b -> b.zeebeJobType("type"))
          .endEvent()
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void init() {
    ENGINE.deployment().withXmlResource(SUB_DURATION_MODEL).deploy();
    ENGINE.deployment().withXmlResource(SUB_TIME_DATE_MODEL).deploy();
    ENGINE.deployment().withXmlResource(SUB_CYCLE_MODEL).deploy();
  }

  @Test
  public void shouldRecreateTimerWithCycle() {
    // given
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("SUB_CYCLE_MODEL").create();

    // when
    final Record<TimerRecordValue> timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE.increaseTime(Duration.ofSeconds(61));

    final Record<TimerRecordValue> timerRescheduled =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .getLast();

    // then
    assertThat(timerRescheduled.getKey()).isGreaterThan(timerCreated.getKey());
    Assertions.assertThat(timerRescheduled.getValue())
        .hasTargetElementId(timerCreated.getValue().getTargetElementId())
        .hasElementInstanceKey(timerCreated.getValue().getElementInstanceKey());

    assertThat(timerRescheduled.getValue().getDueDate())
        .isGreaterThanOrEqualTo(
            timerCreated.getValue().getDueDate() + Duration.ofSeconds(1).toMillis());
  }

  @Test
  public void shouldTriggerTimerDateTime() {
    // given
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("SUB_TIME_DATE_MODEL").create();

    // when
    final Record<TimerRecordValue> createdEvent =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE.increaseTime(Duration.ofMinutes(1));

    // then
    final Record<TimerRecordValue> triggeredEvent =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(triggeredEvent.getKey()).isEqualTo(createdEvent.getKey());
    assertThat(triggeredEvent.getValue()).isEqualTo(createdEvent.getValue());
    // Normally we don't guarantee that a timer gets triggered within a certain time-span. The only
    // guarantee we have is that the timer gets triggered after a specific point in time.
    // Because this is an isolated scenario we can test for this with relative accuracy so we do
    // assert this here with a between.
    assertThat(Duration.ofMillis(triggeredEvent.getTimestamp() - createdEvent.getTimestamp()))
        .isBetween(Duration.ofMinutes(1), Duration.ofMinutes(2));
  }

  @Test
  public void shouldTriggerTimerDuration() {
    // given
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("SUB_DURATION_MODEL").create();

    // when
    final Record<TimerRecordValue> createdEvent =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE.increaseTime(Duration.ofMinutes(1));

    // then
    final Record<TimerRecordValue> triggeredEvent =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(triggeredEvent.getKey()).isEqualTo(createdEvent.getKey());
    assertThat(triggeredEvent.getValue()).isEqualTo(createdEvent.getValue());
    // Normally we don't guarantee that a timer gets triggered within a certain time-span. The only
    // guarantee we have is that the timer gets triggered after a specific point in time.
    // Because this is an isolated scenario we can test for this with relative accuracy so we do
    // assert this here with a between.
    assertThat(Duration.ofMillis(triggeredEvent.getTimestamp() - createdEvent.getTimestamp()))
        .isBetween(Duration.ofMinutes(1), Duration.ofMinutes(2));
  }

  @Test
  public void shouldCancelTimer() {
    // given
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("SUB_DURATION_MODEL").create();

    // when
    final Record<TimerRecordValue> createdEvent =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withHandlerNodeId("other-timer")
            .getFirst();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    final Record<TimerRecordValue> canceledEvent =
        RecordingExporter.timerRecords(TimerIntent.CANCELED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(canceledEvent.getKey()).isEqualTo(createdEvent.getKey());
    assertThat(canceledEvent.getValue()).isEqualTo(createdEvent.getValue());
  }
}
