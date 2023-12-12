/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CompensationSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CompensationEventExecutionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "compensation-process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldExecuteAProcessWithCompensationIntermediateEvent() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask()
            .intermediateThrowEvent(
                "compensation-event",
                i -> i.compensateEventDefinition().compensateEventDefinitionDone())
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(Protocol.USER_TASK_JOB_TYPE).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent,
            r -> r.getValue().getBpmnEventType())
        .containsSubsequence(
            tuple(
                BpmnElementType.USER_TASK,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                BpmnEventType.UNSPECIFIED),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                BpmnEventType.COMPENSATION),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                BpmnEventType.COMPENSATION),
            tuple(
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                BpmnEventType.NONE),
            tuple(
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                BpmnEventType.UNSPECIFIED));
  }

  @Test
  public void shouldExecuteAProcessWithCompensationEndEvent() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask()
            .endEvent(
                "compensation-event",
                e -> e.compensateEventDefinition().compensateEventDefinitionDone())
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(Protocol.USER_TASK_JOB_TYPE).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent,
            r -> r.getValue().getBpmnEventType())
        .containsSubsequence(
            tuple(
                BpmnElementType.USER_TASK,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                BpmnEventType.UNSPECIFIED),
            tuple(
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                BpmnEventType.COMPENSATION),
            tuple(
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                BpmnEventType.COMPENSATION),
            tuple(
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                BpmnEventType.UNSPECIFIED));
  }

  @Test
  public void shouldCreateAndUpdateCompensationSubscriptionForCompletedTask() {
    // given
    final var process =
        createModelFromClasspathResource("/compensation/compensation-throw-event.bpmn");

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(Protocol.USER_TASK_JOB_TYPE).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent,
            r -> r.getValue().getBpmnEventType())
        .containsSubsequence(
            tuple(
                BpmnElementType.USER_TASK,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                BpmnEventType.UNSPECIFIED),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                BpmnEventType.COMPENSATION),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                BpmnEventType.COMPENSATION),
            tuple(
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                BpmnEventType.NONE),
            tuple(
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                BpmnEventType.UNSPECIFIED));

    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSequence(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.CREATED),
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.TRIGGERED));
  }

  private BpmnModelInstance createModelFromClasspathResource(final String classpath) {
    final var resourceAsStream = getClass().getResourceAsStream(classpath);
    return Bpmn.readModelFromStream(resourceAsStream);
  }
}
