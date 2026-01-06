/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractThrowEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.BoundaryEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.EventSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CompensationSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.CompensationSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CompensationEventExecutionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "compensation-process";
  private static final String CHILD_PROCESS_ID = "child-process";
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

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER)
        .complete();

    // then
    final var compensationThrowEventActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.INTERMEDIATE_THROW_EVENT)
            .withEventType(BpmnEventType.COMPENSATION)
            .getFirst();
    assertHasTreePath(compensationThrowEventActivating, processInstanceKey);

    final var compensationActivityActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("ActivityToCompensate")
            .getFirst();

    final var compensationThrowEventActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.INTERMEDIATE_THROW_EVENT)
            .withEventType(BpmnEventType.COMPENSATION)
            .getFirst();

    final var compensationHandlerActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("CompensationHandler")
            .getFirst();

    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .extracting(Record::getValue)
        .extracting(
            CompensationSubscriptionRecordValue::getTenantId,
            CompensationSubscriptionRecordValue::getProcessInstanceKey,
            CompensationSubscriptionRecordValue::getProcessDefinitionKey,
            CompensationSubscriptionRecordValue::getCompensableActivityId,
            CompensationSubscriptionRecordValue::getCompensableActivityInstanceKey,
            CompensationSubscriptionRecordValue::getCompensableActivityScopeKey,
            CompensationSubscriptionRecordValue::getCompensationHandlerId)
        .containsOnly(
            tuple(
                compensationActivityActivated.getValue().getTenantId(),
                processInstanceKey,
                compensationActivityActivated.getValue().getProcessDefinitionKey(),
                "ActivityToCompensate",
                compensationActivityActivated.getKey(),
                compensationActivityActivated.getValue().getFlowScopeKey(),
                "CompensationHandler"));

    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .extracting(
            Record::getIntent,
            r -> r.getValue().getThrowEventId(),
            r -> r.getValue().getThrowEventInstanceKey(),
            r -> r.getValue().getCompensationHandlerInstanceKey())
        .containsSequence(
            tuple(CompensationSubscriptionIntent.CREATED, "", -1L, -1L),
            tuple(
                CompensationSubscriptionIntent.TRIGGERED,
                "CompensationThrowEvent",
                compensationThrowEventActivated.getKey(),
                compensationHandlerActivated.getKey()),
            tuple(
                CompensationSubscriptionIntent.COMPLETED,
                "CompensationThrowEvent",
                compensationThrowEventActivated.getKey(),
                compensationHandlerActivated.getKey()));
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
    final var compensationThrowEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.INTERMEDIATE_THROW_EVENT)
            .withEventType(BpmnEventType.COMPENSATION)
            .getFirst();
    assertHasTreePath(compensationThrowEvent, processInstanceKey);

    final var compensationBoundaryEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.BOUNDARY_EVENT)
            .withEventType(BpmnEventType.COMPENSATION)
            .getFirst();
    assertHasTreePath(compensationBoundaryEvent, processInstanceKey);

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
    final var compensationEndEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.END_EVENT)
            .withEventType(BpmnEventType.COMPENSATION)
            .getFirst();
    assertHasTreePath(compensationEndEvent, processInstanceKey);

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
        .extracting(Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(CompensationSubscriptionIntent.COMPLETED, "CompensationHandler"),
            tuple(CompensationSubscriptionIntent.COMPLETED, "CompensationHandler2"));
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
  }

  @Test
  public void shouldTerminateCompensationHandler() {
    // given
    final var process =
        createModelFromClasspathResource("/compensation/compensation-throw-event.bpmn");

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .complete();

    // when
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
        .extracting(Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(CompensationSubscriptionIntent.TRIGGERED, "CompensationHandler"),
            tuple(CompensationSubscriptionIntent.DELETED, "CompensationHandler"));
  }

  @Test
  public void
      shouldDeleteAllSubscriptionsWhenProcessIsCompletedWithoutTriggerCompensationHandler() {
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
      shouldDeleteAllSubscriptionsWhenProcessIsTerminatedWithoutTriggerCompensationHandler() {
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
        .extracting(Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .contains(
            tuple(CompensationSubscriptionIntent.TRIGGERED, "CompensationHandler"),
            tuple(CompensationSubscriptionIntent.TRIGGERED, "CompensationHandler2"));
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
        .extracting(Record::getIntent, r -> r.getValue().getCompensableActivityId())
        .contains(
            tuple(CompensationSubscriptionIntent.DELETED, "embedded-subprocess"),
            tuple(CompensationSubscriptionIntent.DELETED, "ActivityToCompensate"));
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
        .extracting(Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(CompensationSubscriptionIntent.COMPLETED, "CompensationHandler2"),
            tuple(CompensationSubscriptionIntent.DELETED, "CompensationHandler"));
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

    // when
    completeJobs(processInstanceKey, SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY, 3);

    completeJobs(processInstanceKey, SERVICE_TASK_TYPE_COMPENSATION_HANDLER, 3);

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(20))
        .extracting(Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(CompensationSubscriptionIntent.TRIGGERED, "CompensationHandler"),
            tuple(CompensationSubscriptionIntent.TRIGGERED, "CompensationHandler"),
            tuple(CompensationSubscriptionIntent.TRIGGERED, "CompensationHandler"),
            tuple(CompensationSubscriptionIntent.COMPLETED, "CompensationHandler"),
            tuple(CompensationSubscriptionIntent.COMPLETED, "CompensationHandler"),
            tuple(CompensationSubscriptionIntent.COMPLETED, "CompensationHandler"));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTriggerOnlyCorrectHandlerForMultiInstance() {
    // given
    final var process =
        createModelFromClasspathResource("/compensation/compensation-throw-error.bpmn");
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    completeJobs(processInstanceKey, SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY, 3);

    // when
    final AtomicInteger jobCount = new AtomicInteger(1);

    ENGINE
        .jobs()
        .withType("activity")
        .activate()
        .getValue()
        .getJobKeys()
        .forEach(
            jobKey -> {
              if (jobCount.get() == 2) {
                ENGINE.job().withKey(jobKey).throwError();
              } else {
                ENGINE.job().withKey(jobKey).complete();
              }
              jobCount.getAndIncrement();
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
        .extracting(Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(CompensationSubscriptionIntent.TRIGGERED, "CompensationHandler"),
            tuple(CompensationSubscriptionIntent.COMPLETED, "CompensationHandler"));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
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
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(6))
        .extracting(Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(CompensationSubscriptionIntent.TRIGGERED, ""),
            tuple(CompensationSubscriptionIntent.COMPLETED, "CompensationHandler"));
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
        .extracting(Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(CompensationSubscriptionIntent.COMPLETED, "CompensationHandler"),
            tuple(CompensationSubscriptionIntent.COMPLETED, "CompensationHandler2"),
            tuple(CompensationSubscriptionIntent.COMPLETED, ""));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
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
        .extracting(Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(CompensationSubscriptionIntent.CREATED, "CompensationHandler"),
            tuple(CompensationSubscriptionIntent.DELETED, "CompensationHandler"));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldNotTriggerHandlersForMultiInstanceInsideNotCompletedSubprocess() {
    // given
    final Consumer<SubProcessBuilder> subprocessLevel2 =
        subprocess ->
            subprocess
                .multiInstance(m -> m.zeebeInputCollectionExpression("[1,2,3]"))
                .embeddedSubProcess()
                .startEvent()
                .serviceTask("A", task -> task.zeebeJobType("A"))
                .boundaryEvent()
                .compensation(
                    compensation -> compensation.serviceTask("Undo-A").zeebeJobType("Undo-A"));

    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .subProcess(
                "subprocess-1",
                subprocess ->
                    subprocess
                        .embeddedSubProcess()
                        .startEvent()
                        .subProcess("subprocess-2", subprocessLevel2)
                        .serviceTask("B", task -> task.zeebeJobType("B"))
                        .endEvent())
            .parallelGateway("join")
            .moveToNode("fork")
            .serviceTask("C", task -> task.zeebeJobType("C"))
            .intermediateThrowEvent("compensation-throw-event")
            .compensateEventDefinition()
            .compensateEventDefinitionDone()
            .connectTo("join")
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    completeJobs(processInstanceKey, "A", 3);

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
    final Consumer<SubProcessBuilder> subprocessLevel2 =
        subprocess ->
            subprocess
                .embeddedSubProcess()
                .startEvent()
                .serviceTask("A", task -> task.zeebeJobType("A"))
                .boundaryEvent()
                .compensation(
                    compensation -> compensation.serviceTask("Undo-A").zeebeJobType("Undo-A"));

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
                        .subProcess("subprocess-2", subprocessLevel2)
                        .serviceTask("B", task -> task.zeebeJobType("B")))
            .parallelGateway("join")
            .moveToNode("fork")
            .serviceTask("C", task -> task.zeebeJobType("C"))
            .intermediateThrowEvent("compensation-throw-event")
            .compensateEventDefinition()
            .compensateEventDefinitionDone()
            .connectTo("join")
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    completeJobs(processInstanceKey, "A", 3);

    final var jobKeysOfTaskB =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("B")
            .limit(3)
            .map(Record::getKey)
            .toList();

    ENGINE.job().withKey(jobKeysOfTaskB.getFirst()).complete();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("C").complete();

    jobKeysOfTaskB.stream().skip(1).forEach(key -> ENGINE.job().withKey(key).complete());

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
  public void shouldCompleteThrowEventThatTriggeredCompensationHandler() {
    // given
    final Consumer<SubProcessBuilder> subprocessBuilder =
        subprocess ->
            subprocess
                .multiInstance(m -> m.zeebeInputCollectionExpression("[1,2,3]"))
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
                .intermediateThrowEvent(
                    "compensation-throw-event",
                    AbstractThrowEventBuilder::compensateEventDefinition)
                .endEvent()
                .zeebeOutputExpression("loopCounter", "completed");

    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess("subprocess", subprocessBuilder)
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    completeJobs(processInstanceKey, "A", 3);

    final List<Long> jobKeysUndoA =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("Undo-A")
            .limit(3)
            .map(Record::getKey)
            .toList();

    // when
    ENGINE.job().withKey(jobKeysUndoA.get(1)).complete();
    ENGINE.job().withKey(jobKeysUndoA.get(2)).complete();
    ENGINE.job().withKey(jobKeysUndoA.get(0)).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords())
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getName, VariableRecordValue::getValue)
        .containsSubsequence(
            tuple("loopCounter", "1"),
            tuple("loopCounter", "2"),
            tuple("loopCounter", "3"),
            tuple("completed", "2"),
            tuple("completed", "3"),
            tuple("completed", "1"));
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

  @Test
  public void shouldTriggerCompensationForMultiInstanceActivityOnlyOnce() {
    // given
    final var process =
        createModelFromClasspathResource("/compensation/compensation-multi-instance-activity.bpmn");
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeJobs(processInstanceKey, SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY, 3);

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER)
        .complete();

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .extracting(Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(CompensationSubscriptionIntent.TRIGGERED, "CompensationHandler"),
            tuple(CompensationSubscriptionIntent.COMPLETED, "CompensationHandler"));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTriggerCompensationAfterAllMultiInstanceActivitiesAreCompleted() {
    // given
    final var process =
        createModelFromClasspathResource(
            "/compensation/compensation-multi-instance-activity-parallel.bpmn");
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeJobs(processInstanceKey, SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY, 3);

    completeJobs(processInstanceKey, "activity", 1);

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER)
        .complete();

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .extracting(Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(CompensationSubscriptionIntent.CREATED, "CompensationHandler"),
            tuple(CompensationSubscriptionIntent.TRIGGERED, "CompensationHandler"),
            tuple(CompensationSubscriptionIntent.COMPLETED, "CompensationHandler"));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldNotTriggerCompensationIfMultiInstanceActivitiesAreNotCompleted() {
    // given
    final var process =
        createModelFromClasspathResource(
            "/compensation/compensation-multi-instance-activity-parallel.bpmn");
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var jobKeys =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
            .limit(3)
            .map(Record::getKey)
            .toList();

    // when
    ENGINE.job().withKey(jobKeys.getFirst()).complete();

    completeJobs(processInstanceKey, "activity", 1);

    // then
    jobKeys.stream().skip(1).forEach(key -> ENGINE.job().withKey(key).complete());

    assertThat(RecordingExporter.records().limitToProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.CREATED),
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.DELETED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.TRIGGERED));
  }

  @Test
  public void shouldTriggerCompensationForActivityOnIntermediateThrowEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask(
                "A",
                task ->
                    task.zeebeJobType("A")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .parallelGateway("join")
            .moveToNode("fork")
            .serviceTask(
                "B",
                task ->
                    task.zeebeJobType("B")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-B").zeebeJobType("Undo-B")))
            .connectTo("join")
            .intermediateThrowEvent("compensation-throw-event")
            .compensateEventDefinition()
            .activityRef("A")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple("Undo-B", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldTriggerCompensationForActivityOnEndEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask(
                "A",
                task ->
                    task.zeebeJobType("A")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .parallelGateway("join")
            .moveToNode("fork")
            .serviceTask(
                "B",
                task ->
                    task.zeebeJobType("B")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-B").zeebeJobType("Undo-B")))
            .connectTo("join")
            .endEvent("compensation-throw-event")
            .compensateEventDefinition()
            .activityRef("A")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple("Undo-B", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldTriggerCompensationForActivityTheSameAmountAsExecuted() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .zeebeOutputExpression("0", "iteration")
            .serviceTask(
                "A",
                task ->
                    task.zeebeJobType("A")
                        .zeebeOutputExpression("iteration + 1", "iteration")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .exclusiveGateway("loop")
            .defaultFlow()
            .connectTo("A")
            .moveToNode("loop")
            .conditionExpression("iteration > 1")
            .intermediateThrowEvent(
                "compensation-throw-event",
                event -> event.compensateEventDefinition().activityRef("A"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeJobs(processInstanceKey, "A", 2);

    // then
    completeJobs(processInstanceKey, "Undo-A", 2);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldNotTriggerCompensationForActivityAgain() {
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
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .parallelGateway("fork")
            .intermediateThrowEvent(
                "compensation-throw-event-1",
                event -> event.compensateEventDefinition().activityRef("A"))
            .moveToNode("fork")
            .intermediateThrowEvent(
                "compensation-throw-event-2",
                event -> event.compensateEventDefinition().activityRef("A"))
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event-2", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event-1", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event-1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event-2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .containsOnlyOnce(tuple("Undo-A", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldNotTriggerCompensationForActivityIfActive() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask(
                "A",
                task ->
                    task.zeebeJobType("A")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .moveToNode("fork")
            .serviceTask("B", task -> task.zeebeJobType("B"))
            .intermediateThrowEvent(
                "compensation-throw-event",
                event -> event.compensateEventDefinition().activityRef("A"))
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple("Undo-A", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldTriggerCompensationForSubprocess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .subProcess("subprocess")
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
            .subProcessDone()
            .parallelGateway("join")
            .moveToNode("fork")
            .serviceTask(
                "B",
                task ->
                    task.zeebeJobType("B")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-B").zeebeJobType("Undo-B")))
            .connectTo("join")
            .intermediateThrowEvent("compensation-throw-event")
            .compensateEventDefinition()
            .activityRef("subprocess")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple("Undo-B", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldTriggerCompensationForMultiInstanceSubprocess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .subProcess("subprocess")
            .multiInstance(m -> m.zeebeInputCollectionExpression("[1,2,3]"))
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
            .subProcessDone()
            .parallelGateway("join")
            .moveToNode("fork")
            .serviceTask(
                "B",
                task ->
                    task.zeebeJobType("B")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-B").zeebeJobType("Undo-B")))
            .connectTo("join")
            .intermediateThrowEvent("compensation-throw-event")
            .compensateEventDefinition()
            .activityRef("subprocess")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeJobs(processInstanceKey, "A", 3);
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    completeJobs(processInstanceKey, "Undo-A", 3);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple("Undo-B", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldNotTriggerCompensationForSubprocessAgain() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess("subprocess")
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
            .subProcessDone()
            .parallelGateway("fork")
            .intermediateThrowEvent(
                "compensation-throw-event-1",
                event -> event.compensateEventDefinition().activityRef("subprocess"))
            .moveToNode("fork")
            .intermediateThrowEvent(
                "compensation-throw-event-2",
                event -> event.compensateEventDefinition().activityRef("subprocess"))
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event-2", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event-1", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event-1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event-2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .containsOnlyOnce(tuple("Undo-A", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldNotTriggerCompensationForSubprocessIfActive() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .subProcess("subprocess")
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
            .subProcessDone()
            .moveToNode("fork")
            .serviceTask("B", task -> task.zeebeJobType("B"))
            .intermediateThrowEvent(
                "compensation-throw-event",
                event -> event.compensateEventDefinition().activityRef("subprocess"))
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("subprocess", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple("Undo-A", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldInvokeSubprocessCompensationHandler() {
    // given
    final Consumer<BoundaryEventBuilder> compensationSubprocess =
        compensation ->
            compensation
                .subProcess()
                .embeddedSubProcess()
                .startEvent()
                .serviceTask("B", t -> t.zeebeJobType("B"))
                .serviceTask("C", t -> t.zeebeJobType("C"))
                .endEvent();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "A",
                task -> task.zeebeJobType("A").boundaryEvent().compensation(compensationSubprocess))
            .endEvent()
            .compensateEventDefinition()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("C").complete();

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
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnElementType.SUB_PROCESS,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.SUB_PROCESS,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompensateSubprocess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                subprocess ->
                    subprocess
                        .embeddedSubProcess()
                        .startEvent()
                        .serviceTask("A", task -> task.zeebeJobType("A"))
                        .endEvent())
            .boundaryEvent()
            .compensation(compensation -> compensation.serviceTask("B").zeebeJobType("B"))
            .moveToActivity("subprocess")
            .endEvent()
            .compensateEventDefinition()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

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
                BpmnElementType.SUB_PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompensateSubprocessWithInnerCompensationHandler() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                subprocess ->
                    subprocess
                        .embeddedSubProcess()
                        .startEvent()
                        .serviceTask("A", task -> task.zeebeJobType("A"))
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .boundaryEvent()
            .compensation(compensation -> compensation.serviceTask("B").zeebeJobType("B"))
            .moveToActivity("subprocess")
            .endEvent("compensation-throw-event")
            .compensateEventDefinition()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("B", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldInvokeCallActivityCompensationHandler() {
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
                                    .callActivity("Undo-A")
                                    .zeebeProcessId(CHILD_PROCESS_ID)))
            .endEvent()
            .compensateEventDefinition()
            .done();

    final BpmnModelInstance childProcess =
        Bpmn.createExecutableProcess(CHILD_PROCESS_ID).startEvent().endEvent().done();

    ENGINE.deployment().withXmlResource(process).withXmlResource(childProcess).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .limit(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .extracting(
            r -> r.getValue().getBpmnProcessId(),
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getBpmnEventType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                PROCESS_ID,
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                PROCESS_ID,
                BpmnElementType.CALL_ACTIVITY,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                CHILD_PROCESS_ID,
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                CHILD_PROCESS_ID,
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                PROCESS_ID,
                BpmnElementType.CALL_ACTIVITY,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                PROCESS_ID,
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                PROCESS_ID,
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompensateCallActivity() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .callActivity(
                "A",
                callActivity ->
                    callActivity
                        .zeebeProcessId(CHILD_PROCESS_ID)
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .endEvent()
            .compensateEventDefinition()
            .done();

    final BpmnModelInstance childProcess =
        Bpmn.createExecutableProcess(CHILD_PROCESS_ID).startEvent().endEvent().done();

    ENGINE.deployment().withXmlResource(process).withXmlResource(childProcess).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").complete();

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
                BpmnElementType.CALL_ACTIVITY,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldNotTriggerCompensationIfCallActivityIsActive() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .callActivity(
                "A",
                callActivity ->
                    callActivity
                        .zeebeProcessId(CHILD_PROCESS_ID)
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .parallelGateway("join")
            .endEvent()
            .moveToNode("fork")
            .serviceTask("B", task -> task.zeebeJobType("B"))
            .intermediateThrowEvent(
                "compensation-throw-event", AbstractThrowEventBuilder::compensateEventDefinition)
            .connectTo("join")
            .endEvent()
            .done();

    final BpmnModelInstance childProcess =
        Bpmn.createExecutableProcess(CHILD_PROCESS_ID)
            .startEvent()
            .serviceTask("A", task -> task.zeebeJobType("A"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).withXmlResource(childProcess).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    final long childProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getKey();

    ENGINE.job().ofInstance(childProcessInstanceKey).withType("A").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple("Undo-A", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldNotTriggerCompensationForChildProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .callActivity(
                "A",
                callActivity ->
                    callActivity
                        .zeebeProcessId(CHILD_PROCESS_ID)
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .endEvent("compensation-throw-event")
            .compensateEventDefinition()
            .done();

    final BpmnModelInstance childProcess =
        Bpmn.createExecutableProcess(CHILD_PROCESS_ID)
            .startEvent()
            .serviceTask("B", task -> task.zeebeJobType("B"))
            .boundaryEvent()
            .compensation(compensation -> compensation.serviceTask("Undo-B").zeebeJobType("Undo-B"))
            .done();

    ENGINE.deployment().withXmlResource(process).withXmlResource(childProcess).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final long childProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getKey();

    // when
    ENGINE.job().ofInstance(childProcessInstanceKey).withType("B").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .limit(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .extracting(
            r -> r.getValue().getBpmnProcessId(),
            r -> r.getValue().getElementId(),
            Record::getIntent)
        .containsSubsequence(
            tuple(CHILD_PROCESS_ID, "B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, "A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, "compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(PROCESS_ID, "Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, "compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple(CHILD_PROCESS_ID, "Undo-B", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldInvokeMultiInstanceActivityCompensationHandler() {
    // given
    final Consumer<BoundaryEventBuilder> compensationHandler =
        compensation ->
            compensation
                .serviceTask("Undo-A")
                .zeebeJobType("Undo-A")
                .multiInstance()
                .zeebeInputCollectionExpression("[1,2,3]");

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "A",
                task -> task.zeebeJobType("A").boundaryEvent().compensation(compensationHandler))
            .endEvent("compensation-throw-event")
            .compensateEventDefinition()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    completeJobs(processInstanceKey, "Undo-A", 3);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getElementId(),
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getBpmnEventType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                "compensation-throw-event",
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                "Undo-A",
                BpmnElementType.MULTI_INSTANCE_BODY,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                "Undo-A",
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                "Undo-A",
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                "Undo-A",
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                "Undo-A",
                BpmnElementType.MULTI_INSTANCE_BODY,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                "compensation-throw-event",
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                PROCESS_ID,
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldInvokeMultiInstanceSubprocessCompensationHandler() {
    // given
    final Consumer<BoundaryEventBuilder> compensationHandler =
        compensation ->
            compensation
                .subProcess()
                .multiInstance(m -> m.zeebeInputCollectionExpression("[1,2,3]"))
                .embeddedSubProcess()
                .startEvent()
                .serviceTask("Undo-A")
                .zeebeJobType("Undo-A")
                .endEvent();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "A",
                task -> task.zeebeJobType("A").boundaryEvent().compensation(compensationHandler))
            .endEvent()
            .compensateEventDefinition()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    completeJobs(processInstanceKey, "Undo-A", 3);

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
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnElementType.MULTI_INSTANCE_BODY,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnElementType.SUB_PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.SUB_PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.SUB_PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.MULTI_INSTANCE_BODY,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldDeleteSubscriptionForTerminatedSubprocess() {
    // given
    final var process =
        createModelFromClasspathResource("/compensation/compensation-subprocess-terminated.bpmn");
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .complete();

    // then
    assertThat(RecordingExporter.records().limitToProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.CREATED),
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.DELETED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.TRIGGERED));
  }

  @Test
  public void shouldDeleteSubscriptionInTerminatedSubprocessScope() {
    // given
    final var process =
        createModelFromClasspathResource(
            "/compensation/compensation-subprocess-terminated-multiscope.bpmn");
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("C").complete();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("undoA").complete();

    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(7))
        .extracting(Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(CompensationSubscriptionIntent.CREATED, "undoA"),
            tuple(CompensationSubscriptionIntent.CREATED, "undoB"),
            tuple(CompensationSubscriptionIntent.CREATED, "undoC"),
            tuple(CompensationSubscriptionIntent.DELETED, "undoB"),
            tuple(CompensationSubscriptionIntent.DELETED, "undoC"),
            tuple(CompensationSubscriptionIntent.TRIGGERED, "undoA"),
            tuple(CompensationSubscriptionIntent.COMPLETED, "undoA"));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldDeleteSubscriptionInTerminatedSubprocessMultiInstanceScope() {
    // given
    final var process =
        createModelFromClasspathResource(
            "/compensation/compensation-multiinstance-subprocess-terminated.bpmn");
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeJobs(processInstanceKey, "A", 2);

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(6))
        .extracting(Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(CompensationSubscriptionIntent.CREATED, "undoA"),
            tuple(CompensationSubscriptionIntent.TRIGGERED, "undoA"),
            tuple(CompensationSubscriptionIntent.CREATED, "undoA"),
            tuple(CompensationSubscriptionIntent.TRIGGERED, "undoA"),
            tuple(CompensationSubscriptionIntent.DELETED, "undoA"),
            tuple(CompensationSubscriptionIntent.DELETED, "undoA"));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTriggerCompensationFromEventSubprocess() {
    // given
    final Consumer<EventSubProcessBuilder> compensationEventSubprocess =
        eventSubprocess ->
            eventSubprocess.startEvent().error().endEvent().compensateEventDefinition();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess("event-subprocess", compensationEventSubprocess)
            .startEvent()
            .serviceTask(
                "A",
                task ->
                    task.zeebeJobType("A")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .serviceTask("B", task -> task.zeebeJobType("B"))
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("B").withErrorCode("error").throwError();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").complete();

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
                BpmnElementType.EVENT_SUB_PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnElementType.SERVICE_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.END_EVENT,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.EVENT_SUB_PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.PROCESS,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTriggerCompensationFromEventSubprocessInsideSubprocess() {
    // given
    final Consumer<EventSubProcessBuilder> compensationEventSubprocess =
        eventSubprocess ->
            eventSubprocess
                .startEvent()
                .error()
                .endEvent("compensation-throw-event")
                .compensateEventDefinition();

    final Consumer<SubProcessBuilder> subprocessBuilder =
        subprocess ->
            subprocess
                .embeddedSubProcess()
                .eventSubProcess("event-subprocess", compensationEventSubprocess)
                .startEvent()
                .serviceTask(
                    "A",
                    task ->
                        task.zeebeJobType("A")
                            .boundaryEvent()
                            .compensation(
                                compensation ->
                                    compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
                .serviceTask("B", task -> task.zeebeJobType("B"));

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .subProcess("subprocess", subprocessBuilder)
            .parallelGateway("join")
            .moveToNode("fork")
            .serviceTask(
                "C",
                task ->
                    task.zeebeJobType("C")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-C").zeebeJobType("Undo-C")))
            .connectTo("join")
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("C").complete();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("B").withErrorCode("error").throwError();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple("Undo-C", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldTriggerCompensationFromEventSubprocessWithCompensationHandlerInside() {
    // given
    final Consumer<EventSubProcessBuilder> compensationEventSubprocess =
        eventSubprocess ->
            eventSubprocess
                .startEvent()
                .error()
                .serviceTask(
                    "C",
                    task ->
                        task.zeebeJobType("C")
                            .boundaryEvent()
                            .compensation(
                                compensation ->
                                    compensation.serviceTask("Undo-C").zeebeJobType("Undo-C")))
                .endEvent("compensation-throw-event")
                .compensateEventDefinition();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess("event-subprocess", compensationEventSubprocess)
            .startEvent()
            .serviceTask(
                "A",
                task ->
                    task.zeebeJobType("A")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .serviceTask("B", task -> task.zeebeJobType("B"))
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("B").withErrorCode("error").throwError();
    ENGINE.job().ofInstance(processInstanceKey).withType("C").complete();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-C").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("Undo-C", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldNotTriggerCompensationFromEventSubprocessInsideSubprocess() {
    // given
    final Consumer<SubProcessBuilder> subprocessBuilder =
        subprocess ->
            subprocess
                .embeddedSubProcess()
                .startEvent()
                .serviceTask(
                    "D",
                    task ->
                        task.zeebeJobType("D")
                            .boundaryEvent()
                            .compensation(
                                compensation ->
                                    compensation.serviceTask("Undo-D").zeebeJobType("Undo-D")))
                .endEvent("compensation-throw-event")
                .compensateEventDefinition();

    final Consumer<EventSubProcessBuilder> eventSubprocessBuilder =
        eventSubprocess ->
            eventSubprocess
                .startEvent()
                .error()
                .serviceTask(
                    "C",
                    task ->
                        task.zeebeJobType("C")
                            .boundaryEvent()
                            .compensation(
                                compensation ->
                                    compensation.serviceTask("Undo-C").zeebeJobType("Undo-C")))
                .subProcess("subprocess", subprocessBuilder);

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess("event-subprocess", eventSubprocessBuilder)
            .startEvent()
            .serviceTask(
                "A",
                task ->
                    task.zeebeJobType("A")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .serviceTask("B", task -> task.zeebeJobType("B"))
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("B").withErrorCode("error").throwError();
    ENGINE.job().ofInstance(processInstanceKey).withType("C").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("D").complete();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-D").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("subprocess", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("Undo-D", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("Undo-C", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldTriggerCompensationForActivityFromEventSubprocess() {
    // given
    final Consumer<EventSubProcessBuilder> compensationEventSubprocess =
        eventSubprocess ->
            eventSubprocess.startEvent().error().endEvent("compensation-throw-event");

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess("event-subprocess", compensationEventSubprocess)
            .startEvent()
            .serviceTask(
                "A",
                task ->
                    task.zeebeJobType("A")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .serviceTask(
                "B",
                task ->
                    task.zeebeJobType("B")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-B").zeebeJobType("Undo-B")))
            .serviceTask("C", task -> task.zeebeJobType("C"))
            .done();

    final EndEvent endEvent = process.getModelElementById("compensation-throw-event");
    endEvent.builder().compensateEventDefinition().activityRef("A");

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("C").withErrorCode("error").throwError();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple("Undo-B", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldTriggerCompensationForActivityFromEventSubprocessInsideSubprocess() {
    // given
    final Consumer<EventSubProcessBuilder> compensationEventSubprocess =
        eventSubprocess ->
            eventSubprocess.startEvent().error().endEvent("compensation-throw-event");

    final Consumer<SubProcessBuilder> subprocessBuilder =
        subprocess ->
            subprocess
                .embeddedSubProcess()
                .eventSubProcess("event-subprocess", compensationEventSubprocess)
                .startEvent()
                .serviceTask(
                    "B",
                    task ->
                        task.zeebeJobType("B")
                            .boundaryEvent()
                            .compensation(
                                compensation ->
                                    compensation.serviceTask("Undo-B").zeebeJobType("Undo-B")))
                .serviceTask(
                    "C",
                    task ->
                        task.zeebeJobType("C")
                            .boundaryEvent()
                            .compensation(
                                compensation ->
                                    compensation.serviceTask("Undo-C").zeebeJobType("Undo-C")))
                .serviceTask("D", task -> task.zeebeJobType("D"));

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
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .subProcess("subprocess", subprocessBuilder)
            .endEvent()
            .done();

    final EndEvent endEvent = process.getModelElementById("compensation-throw-event");
    endEvent.builder().compensateEventDefinition().activityRef("B");

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("C").complete();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("D").withErrorCode("error").throwError();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-B").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("Undo-B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple("Undo-A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("Undo-C", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldTriggerCompensationForActivityInsideEventSubprocess() {
    // given
    final Consumer<EventSubProcessBuilder> compensationEventSubprocess =
        eventSubprocess ->
            eventSubprocess
                .startEvent()
                .error()
                .serviceTask(
                    "C",
                    task ->
                        task.zeebeJobType("C")
                            .boundaryEvent()
                            .compensation(
                                compensation ->
                                    compensation.serviceTask("Undo-C").zeebeJobType("Undo-C")))
                .endEvent("compensation-throw-event");

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess("event-subprocess", compensationEventSubprocess)
            .startEvent()
            .serviceTask(
                "A",
                task ->
                    task.zeebeJobType("A")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .serviceTask("B", task -> task.zeebeJobType("B"))
            .done();

    final EndEvent endEvent = process.getModelElementById("compensation-throw-event");
    endEvent.builder().compensateEventDefinition().activityRef("C");

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("B").withErrorCode("error").throwError();
    ENGINE.job().ofInstance(processInstanceKey).withType("C").complete();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("Undo-C").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("Undo-C", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple("Undo-A", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldNotTriggerCompensationForActivityFromEventSubprocessIfActive() {
    // given
    final Consumer<EventSubProcessBuilder> compensationEventSubprocess =
        eventSubprocess ->
            eventSubprocess
                .startEvent()
                .interrupting(false)
                .signal("signal")
                .endEvent("compensation-throw-event");

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess("event-subprocess", compensationEventSubprocess)
            .startEvent()
            .serviceTask(
                "A",
                task ->
                    task.zeebeJobType("A")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .done();

    final EndEvent endEvent = process.getModelElementById("compensation-throw-event");
    endEvent.builder().compensateEventDefinition().activityRef("A");

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.signal().withSignalName("signal").broadcast();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple("Undo-A", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldTriggerCompensationHandlerInAdHocSubprocesses() {
    final var process =
        createModelFromClasspathResource("/compensation/compensation-adhoc-subprocess.bpmn");

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
                .limit(12))
        .extracting(Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .contains(
            tuple(CompensationSubscriptionIntent.TRIGGERED, "CompensationHandler"),
            tuple(CompensationSubscriptionIntent.TRIGGERED, "CompensationHandler2"));
  }

  @Test
  public void shouldNotTriggerCompensationIfAdHocSubProcessIsNotCompleted() {
    final var process =
        createModelFromClasspathResource("/compensation/compensation-single-adhoc-subprocess.bpmn");

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
                .limit(6))
        .extracting(Record::getIntent, r -> r.getValue().getCompensableActivityId())
        .contains(
            tuple(CompensationSubscriptionIntent.DELETED, "adhoc-subprocess"),
            tuple(CompensationSubscriptionIntent.DELETED, "adhoc-subprocess#innerInstance"),
            tuple(CompensationSubscriptionIntent.DELETED, "ActivityToCompensate"));
  }

  @Test
  public void shouldTriggerCompensationWithAdHocSubprocessesHandler() {
    final var process =
        createModelFromClasspathResource(
            "/compensation/compensation-adhoc-subprocess-handler.bpmn");

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
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER)
        .complete();

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .extracting(Record::getIntent, r -> r.getValue().getCompensationHandlerId())
        .contains(tuple(CompensationSubscriptionIntent.TRIGGERED, "adhoc-subprocess"));
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId())
        .containsSubsequence(
            "compensationHandler",
            "adhoc-subprocess#innerInstance",
            "adhoc-subprocess",
            PROCESS_ID);
  }

  private BpmnModelInstance createModelFromClasspathResource(final String classpath) {
    final var resourceAsStream = getClass().getResourceAsStream(classpath);
    return Bpmn.readModelFromStream(resourceAsStream);
  }

  private void completeJobs(final long processInstanceKey, final String jobType, final int number) {
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(jobType)
        .limit(number)
        .map(Record::getKey)
        .forEach(jobKey -> ENGINE.job().withKey(jobKey).complete());
  }

  private void assertHasTreePath(
      final Record<ProcessInstanceRecordValue> eventActivating, final long processInstanceKey) {
    final ProcessInstanceRecordValue recordValue = eventActivating.getValue();
    Assertions.assertThat(recordValue)
        .hasOnlyElementInstancePath(List.of(processInstanceKey, eventActivating.getKey()));
    Assertions.assertThat(recordValue)
        .hasOnlyProcessDefinitionPath(recordValue.getProcessDefinitionKey());
  }
}
