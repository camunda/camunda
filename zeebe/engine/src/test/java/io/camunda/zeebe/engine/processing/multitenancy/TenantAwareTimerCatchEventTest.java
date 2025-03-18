/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.IntermediateCatchEventBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class TenantAwareTimerCatchEventTest {

  private static final String PROCESS_ID = "process";
  private static final String TIMER_ID = "timer";
  private static final String TENANT = "tenant-a";

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withSecurityConfig(config -> config.getMultiTenancy().setEnabled(true));

  private static BpmnModelInstance processWithTimer(
      final Consumer<IntermediateCatchEventBuilder> consumer) {
    final var builder =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().intermediateCatchEvent(TIMER_ID);

    consumer.accept(builder);

    return builder.endEvent().done();
  }

  @Before
  public void init() {
    final var process = processWithTimer(c -> c.timerWithDuration("PT1M"));
    engine.deployment().withXmlResource(process).withTenantId(TENANT).deploy();
  }

  @Test
  public void shouldCreateTimer() {
    // when
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT).create();

    // then
    final var timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(timerRecord.getValue()).hasTenantId(TENANT);
  }

  @Test
  public void shouldTriggerTimer() {
    // given
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT).create();

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();

    // when
    engine.increaseTime(Duration.ofMinutes(1));

    // then
    assertThat(
            RecordingExporter.timerRecords()
                .withIntents(TimerIntent.TRIGGER, TimerIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .extracting(r -> r.getValue().getTenantId(), Record::getIntent)
        .containsSequence(tuple(TENANT, TimerIntent.TRIGGER), tuple(TENANT, TimerIntent.TRIGGERED));
  }

  @Test
  public void shouldCancelTimer() {
    // given
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT).create();

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .forAuthorizedTenants(TENANT)
        .cancel();

    // then
    final var canceledEvent =
        RecordingExporter.timerRecords(TimerIntent.CANCELED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(canceledEvent.getValue()).hasTenantId(TENANT);
  }

  @Test
  public void shouldRaiseIncident() {
    // given
    final var faultyDurationExpression = "today() + duration(\"P1D\")";
    final var process =
        processWithTimer(c -> c.timerWithDurationExpression(faultyDurationExpression));
    engine.deployment().withXmlResource(process).withTenantId(TENANT).deploy();

    // when
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT).create();

    // then
    final var incidentEvent =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    Assertions.assertThat(incidentEvent).hasTenantId(TENANT);
  }

  @Test
  public void shouldRescheduleTimer() {
    // given
    final var process =
        Bpmn.createExecutableProcess("RESCHEDULE_PROCESS")
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType("type"))
            .boundaryEvent(TIMER_ID)
            .cancelActivity(false)
            .timerWithCycle("R2/PT1M")
            .endEvent()
            .moveToNode("task")
            .endEvent()
            .done();

    engine.deployment().withXmlResource(process).withTenantId(TENANT).deploy();

    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("RESCHEDULE_PROCESS")
            .withTenantId(TENANT)
            .create();

    final var timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    engine.increaseTime(Duration.ofMinutes(1));

    final var timerRescheduled =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .getLast();

    // then
    assertThat(timerRescheduled.getKey()).isGreaterThan(timerCreated.getKey());
    Assertions.assertThat(timerRescheduled.getValue()).hasTenantId(TENANT);
  }

  @Test
  public void shouldApplyTenantToLifecycleEvents() {
    // given
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT).create();

    // when
    engine.increaseTime(Duration.ofMinutes(1));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementId("timer"))
        .extracting(r -> r.getValue().getTenantId(), Record::getIntent)
        .containsSequence(
            tuple(TENANT, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(TENANT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(TENANT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(TENANT, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(TENANT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(TENANT, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }
}
