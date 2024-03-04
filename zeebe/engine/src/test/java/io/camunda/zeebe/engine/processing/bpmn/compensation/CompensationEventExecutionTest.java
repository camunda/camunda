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
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CompensationSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CompensationEventExecutionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "compensation-process";
  private static final String SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY = "compensableActivity";
  private static final String SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY2 = "compensableActivity2";
  private static final String SERVICE_TASK_TYPE_COMPENSATION_HANDLER = "compensationHandler";
  private static final String SERVICE_TASK_TYPE_COMPENSATION_HANDLER2 = "compensationHandler2";

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
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .complete();

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .extracting(
            Record::getValueType,
            Record::getIntent,
            r -> r.getValue().getTenantId(),
            r -> r.getValue().getProcessInstanceKey(),
            r -> r.getValue().getCompensableActivityId(),
            r -> r.getValue().getThrowEventId())
        .containsSequence(
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.CREATED,
                TenantOwned.DEFAULT_TENANT_IDENTIFIER,
                processInstanceKey,
                "ActivityToCompensate",
                ""),
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.TRIGGERED,
                TenantOwned.DEFAULT_TENANT_IDENTIFIER,
                processInstanceKey,
                "ActivityToCompensate",
                "CompensationThrowEvent"));
  }

  @Test
  public void shouldActivateAndCompleteCompensationHandlerForIntermediateThrowEvent() {
    // given
    final var process =
        createModelFromClasspathResource("/compensation/compensation-throw-event.bpmn");

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER)
        .complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getBpmnEventType(),
            Record::getIntent,
            r -> r.getValue().getElementId())
        .containsSubsequence(
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                "ActivityToCompensate"),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                "ActivityToCompensate"),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                "CompensationThrowEvent"),
            tuple(
                BpmnElementType.BOUNDARY_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATING,
                "CompensationBoundaryEvent"),
            tuple(
                BpmnElementType.BOUNDARY_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                "CompensationBoundaryEvent"),
            tuple(
                BpmnElementType.BOUNDARY_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETING,
                "CompensationBoundaryEvent"),
            tuple(
                BpmnElementType.BOUNDARY_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                "CompensationBoundaryEvent"),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATING,
                "CompensationHandler"),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                "CompensationHandler"),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                "CompensationHandler"),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                "CompensationThrowEvent"),
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                PROCESS_ID));

    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .extracting(Record::getValueType, Record::getIntent)
        .contains(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.COMPLETED));
  }

  @Test
  public void shouldActivateAndCompleteCompensationHandlerForEndEvent() {
    // given
    final var process =
        createModelFromClasspathResource("/compensation/compensation-end-event.bpmn");

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER)
        .complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getBpmnEventType(),
            Record::getIntent,
            r -> r.getValue().getElementId())
        .containsSubsequence(
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                "ActivityToCompensate"),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                "ActivityToCompensate"),
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                "CompensationEndEvent"),
            tuple(
                BpmnElementType.BOUNDARY_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATING,
                "CompensationBoundaryEvent"),
            tuple(
                BpmnElementType.BOUNDARY_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                "CompensationBoundaryEvent"),
            tuple(
                BpmnElementType.BOUNDARY_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETING,
                "CompensationBoundaryEvent"),
            tuple(
                BpmnElementType.BOUNDARY_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                "CompensationBoundaryEvent"),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATING,
                "CompensationHandler"),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                "CompensationHandler"),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                "CompensationHandler"),
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                "CompensationEndEvent"),
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                PROCESS_ID));

    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .extracting(Record::getValueType, Record::getIntent)
        .contains(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.COMPLETED));
  }

  @Test
  public void shouldActivateAndCompleteMultipleCompensationHandlerForThrowEvent() {
    // given
    final var process =
        createModelFromClasspathResource("/compensation/multiple-compensation-throw-event.bpmn");

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY2)
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER)
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER2)
        .complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getBpmnEventType(),
            Record::getIntent,
            r -> r.getValue().getElementId())
        .containsSubsequence(
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                "CompensationThrowEvent"),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                "CompensationHandler"),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                "CompensationHandler2"),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                "CompensationThrowEvent"),
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                PROCESS_ID));

    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(6))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.COMPLETED),
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.COMPLETED));
  }

  @Test
  public void shouldActivateAndCompleteMultipleCompensationHandlerForEndEvent() {
    // given
    final var process =
        createModelFromClasspathResource("/compensation/multiple-compensation-end-event.bpmn");

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY2)
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER)
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER2)
        .complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getBpmnEventType(),
            Record::getIntent,
            r -> r.getValue().getElementId())
        .containsSubsequence(
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                "CompensationEndEvent"),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                "CompensationHandler"),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                "CompensationHandler2"),
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                "CompensationEndEvent"),
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                PROCESS_ID));

    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(6))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.COMPLETED),
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.COMPLETED));
  }

  @Test
  public void shouldActivateAndTerminateCompensationHandlerForIntermediateThrowEvent() {
    // given
    final var process =
        createModelFromClasspathResource("/compensation/compensation-throw-event.bpmn");

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .complete();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getBpmnEventType(),
            Record::getIntent,
            r -> r.getValue().getElementId())
        .containsSubsequence(
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                "CompensationThrowEvent"),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_TERMINATED,
                "CompensationThrowEvent"),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_TERMINATED,
                "CompensationHandler"),
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_TERMINATED,
                PROCESS_ID));

    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .extracting(Record::getValueType, Record::getIntent)
        .contains(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.DELETED));
  }

  @Test
  public void shouldActivateAndTerminateCompensationHandlerForEndEvent() {
    // given
    final var process =
        createModelFromClasspathResource("/compensation/compensation-end-event.bpmn");

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .complete();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getBpmnEventType(),
            Record::getIntent,
            r -> r.getValue().getElementId())
        .containsSubsequence(
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                "CompensationEndEvent"),
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_TERMINATED,
                "CompensationEndEvent"),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_TERMINATED,
                "CompensationHandler"),
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_TERMINATED,
                PROCESS_ID));

    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .extracting(Record::getValueType, Record::getIntent)
        .contains(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.DELETED));
  }

  @Test
  public void shouldDeleteAllSubscriptionWhenProcessIsCompletedWithoutTriggerCompensationHandler() {
    // given
    final var process =
        createModelFromClasspathResource("/compensation/compensation-no-throw-event.bpmn");

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(Protocol.USER_TASK_JOB_TYPE).complete();

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .extracting(Record::getValueType, Record::getIntent)
        .contains(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.DELETED));
  }

  @Test
  public void
      shouldDeleteAllSubscriptionWhenProcessIsTerminatedWithoutTriggerCompensationHandler() {
    // given
    final var process =
        createModelFromClasspathResource(
            "/compensation/compensation-no-throw-event-terminate.bpmn");

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(Protocol.USER_TASK_JOB_TYPE).complete();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .extracting(Record::getValueType, Record::getIntent)
        .contains(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.DELETED));
  }

  @Test
  public void shouldTriggerCompensationHandlerInSubprocesses() {
    final var process =
        createModelFromClasspathResource("/compensation/compensation-embedded-subprocess.bpmn");

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY2)
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER)
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER2)
        .complete();

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(8))
        .extracting(
            Record::getValueType,
            Record::getIntent,
            r -> r.getValue().getCompensableActivityScopeId())
        .containsSubsequence(
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.TRIGGERED,
                "embedded-subprocess"),
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.TRIGGERED,
                "embedded-subprocess-2"));
  }

  @Test
  public void shouldNotTriggerCompensationIfSubprocessIsNotCompleted() {
    final var process =
        createModelFromClasspathResource(
            "/compensation/compensation-single-embedded-subprocess.bpmn");

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .complete();

    ENGINE.job().ofInstance(processInstanceKey).withType("completableActivity").complete();

    ENGINE.job().ofInstance(processInstanceKey).withType("NotActivableTask").complete();

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(4))
        .extracting(
            Record::getValueType, Record::getIntent, r -> r.getValue().getCompensableActivityId())
        .containsSubsequence(
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.DELETED,
                "embedded-subprocess"),
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.DELETED,
                "ActivityToCompensate"));
  }

  @Test
  public void shouldNotTriggerCompensationOnParentScope() {
    final var process =
        createModelFromClasspathResource(
            "/compensation/compensation-embedded-subprocess-parent.bpmn");

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY2)
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER2)
        .complete();

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(5))
        .extracting(
            Record::getValueType,
            Record::getIntent,
            r -> r.getValue().getCompensableActivityScopeId(),
            r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.COMPLETED,
                "embedded-subprocess",
                "CompensationHandler2"),
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.DELETED,
                "compensation-process",
                "CompensationHandler"));
  }

  @Test
  public void shouldNotCreateSubprocessSubscriptionWithoutChildSubscription() {
    // given
    final var process =
        createModelFromClasspathResource(
            "/compensation/subprocess-after-compensation-activity.bpmn");
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // When
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER)
        .complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getBpmnEventType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldInvokeCompensationHandlerTheSameAmountAsExecuted() {
    // given
    final var process =
        createModelFromClasspathResource("/compensation/compensation-multi-instance.bpmn");
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .jobs()
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .activate()
        .getValue()
        .getJobKeys()
        .forEach(jobKey -> ENGINE.job().withKey(jobKey).complete());

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("CompensationHandler")
        .limit(3)
        .map(Record::getKey)
        .forEach(jobKey -> ENGINE.job().withKey(jobKey).complete());

    // then
    assertThat(
            RecordingExporter.jobBatchRecords()
                .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
                .withIntent(JobBatchIntent.ACTIVATED)
                .getFirst()
                .getValue()
                .getJobs()
                .size())
        .isEqualTo(3);

    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(20))
        .extracting(
            Record::getValueType, Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.TRIGGERED,
                "CompensationHandler"),
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.TRIGGERED,
                "CompensationHandler"),
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.TRIGGERED,
                "CompensationHandler"),
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.COMPLETED,
                "CompensationHandler"),
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.COMPLETED,
                "CompensationHandler"),
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.COMPLETED,
                "CompensationHandler"));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getBpmnEventType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTriggerOnlyCorrectHandlerForMultiInstance() {
    // given
    final var process =
        createModelFromClasspathResource("/compensation/compensation-throw-error.bpmn");
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .jobs()
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .activate()
        .getValue()
        .getJobKeys()
        .forEach(jobKey -> ENGINE.job().withKey(jobKey).complete());

    final AtomicInteger i = new AtomicInteger(1);

    ENGINE
        .jobs()
        .withType("activity")
        .activate()
        .getValue()
        .getJobKeys()
        .forEach(
            jobKey -> {
              if (i.get() == 2) {
                ENGINE.job().withKey(jobKey).throwError();
              } else {
                ENGINE.job().withKey(jobKey).complete();
              }
              i.getAndIncrement();
            });

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER)
        .complete();

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(8))
        .extracting(
            Record::getValueType, Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.TRIGGERED,
                "CompensationHandler"),
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.COMPLETED,
                "CompensationHandler"));
  }

  @Test
  public void shouldTriggerCompensationHandlerOnlyOnce() {
    // given
    final var process =
        createModelFromClasspathResource("/compensation/compensation-multi-throw-event.bpmn");
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .complete();

    // When
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER)
        .complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getBpmnEventType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(6))
        .extracting(
            Record::getValueType, Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.TRIGGERED, ""),
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.COMPLETED,
                "CompensationHandler"));
  }

  @Test
  public void shouldCompleteSubprocessAfterAllCompensationHandlerAreCompleted() {
    // given
    final var process =
        createModelFromClasspathResource(
            "/compensation/compensation-subprocess-multi-handler.bpmn");
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .complete();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY2)
        .complete();

    // When
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER)
        .complete();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER2)
        .complete();

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(9))
        .extracting(
            Record::getValueType, Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.COMPLETED,
                "CompensationHandler"),
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.COMPLETED,
                "CompensationHandler2"),
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.COMPLETED, ""));
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getBpmnEventType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldNotTriggerCompensationHandlerIfTheParentSubprocessIsNotCompleted() {
    // given
    final var process =
        createModelFromClasspathResource("/compensation/compensation-multi-subprocess.bpmn");
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .complete();

    ENGINE.job().ofInstance(processInstanceKey).withType("activity2").complete();

    // When
    ENGINE.job().ofInstance(processInstanceKey).withType("activity").complete();

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(6))
        .extracting(
            Record::getValueType, Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.CREATED,
                "CompensationHandler"),
            tuple(
                ValueType.COMPENSATION_SUBSCRIPTION,
                CompensationSubscriptionIntent.DELETED,
                "CompensationHandler"));
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getBpmnEventType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldNotTriggerHandlersForMultiInstanceInsideNotCompletedSubprocess() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .subProcess()
            .embeddedSubProcess()
            .startEvent()
            .subProcess(
                "subprocess-2",
                subprocess ->
                    subprocess
                        .multiInstance(m -> m.zeebeInputCollectionExpression("[1,2,3]"))
                        .embeddedSubProcess()
                        .startEvent()
                        .serviceTask("A", task -> task.zeebeJobType("A"))
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .serviceTask("B")
            .zeebeJobType("B")
            .endEvent()
            .subProcessDone()
            .parallelGateway("join")
            .moveToNode("fork")
            .serviceTask("C")
            .zeebeJobType("C")
            .intermediateThrowEvent("compensation-throw-event")
            .compensateEventDefinition()
            .compensateEventDefinitionDone()
            .connectTo("join")
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType("A")
        .limit(3)
        .forEach(job -> ENGINE.job().withKey(job.getKey()).withType("A").complete());

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("C").complete();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("B", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple("Undo-A", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void
      shouldNotTriggerHandlersForSubprocessIfParentMultiInstancesSubprocessesAreNotCompleted() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .subProcess(
                "subprocess",
                subprocess ->
                    subprocess
                        .multiInstance(m -> m.zeebeInputCollectionExpression("[1,2,3]"))
                        .embeddedSubProcess()
                        .startEvent()
                        .subProcess(
                            "subprocess-2",
                            subprocess2 ->
                                subprocess2
                                    .embeddedSubProcess()
                                    .startEvent()
                                    .serviceTask("A", task -> task.zeebeJobType("A"))
                                    .boundaryEvent()
                                    .compensation(
                                        compensation ->
                                            compensation
                                                .serviceTask("Undo-A")
                                                .zeebeJobType("Undo-A")))
                        .serviceTask("B")
                        .zeebeJobType("B"))
            .parallelGateway("join")
            .moveToNode("fork")
            .serviceTask("C")
            .zeebeJobType("C")
            .intermediateThrowEvent("compensation-throw-event")
            .compensateEventDefinition()
            .compensateEventDefinitionDone()
            .connectTo("join")
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType("A")
        .limit(3)
        .forEach(job -> ENGINE.job().withKey(job.getKey()).withType("A").complete());

    final var jobKeys =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("B")
            .limit(3)
            .map(Record::getKey)
            .toList();

    ENGINE.job().withKey(jobKeys.getFirst()).complete();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("C").complete();

    jobKeys.stream().skip(1).forEach(key -> ENGINE.job().withKey(key).complete());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("B", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple("Undo-A", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldApplyInputMappingsOfCompensationHandler() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "A",
                task ->
                    task.zeebeJobType("A")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation
                                    .serviceTask("Undo-A")
                                    .zeebeJobType("Undo-A")
                                    .zeebeInputExpression("x + 1", "y")))
            .endEvent()
            .compensateEventDefinition()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("x", 1).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").complete();

    assertThat(RecordingExporter.records().limitToProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.TRIGGERED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(ValueType.VARIABLE, VariableIntent.CREATED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    final long compensationHandlerInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("Undo-A")
            .getFirst()
            .getKey();

    final var variableCreated =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("y")
            .getFirst();

    Assertions.assertThat(variableCreated.getValue())
        .hasScopeKey(compensationHandlerInstanceKey)
        .hasValue("2");
  }

  @Test
  public void shouldApplyOutputMappingsOfCompensationHandler() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "A",
                task ->
                    task.zeebeJobType("A")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation
                                    .serviceTask("Undo-A")
                                    .zeebeJobType("Undo-A")
                                    .zeebeOutputExpression("x + 1", "y")))
            .endEvent()
            .compensateEventDefinition()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").withVariable("x", 1).complete();

    // then
    assertThat(RecordingExporter.records().limitToProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.TRIGGERED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(ValueType.VARIABLE, VariableIntent.CREATED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.COMPLETED));

    final var variableCreated =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("y")
            .getFirst();

    Assertions.assertThat(variableCreated.getValue()).hasScopeKey(processInstanceKey).hasValue("2");
  }

  @Test
  public void shouldPropagateVariablesOfCompensationHandler() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess("subprocess")
            .zeebeInputExpression("0", "local")
            .embeddedSubProcess()
            .startEvent()
            .serviceTask(
                "A",
                task ->
                    task.zeebeJobType("A")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .endEvent()
            .compensateEventDefinition()
            .subProcessDone()
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("Undo-A")
        .withVariables(Map.ofEntries(Map.entry("local", 1), Map.entry("global", 2)))
        .complete();

    // then
    final long subprocessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("subprocess")
            .getFirst()
            .getKey();

    assertThat(
        RecordingExporter.records()
            .limitToProcessInstance(processInstanceKey)
            .variableRecords())
        .extracting(Record::getValue)
        .extracting(
            VariableRecordValue::getScopeKey,
            VariableRecordValue::getName,
            VariableRecordValue::getValue)
        .containsExactly(
            tuple(subprocessInstanceKey, "local", "0"),
            tuple(subprocessInstanceKey, "local", "1"),
            tuple(processInstanceKey, "global", "2"));
  }

  private BpmnModelInstance createModelFromClasspathResource(final String classpath) {
    final var resourceAsStream = getClass().getResourceAsStream(classpath);
    return Bpmn.readModelFromStream(resourceAsStream);
  }
}
