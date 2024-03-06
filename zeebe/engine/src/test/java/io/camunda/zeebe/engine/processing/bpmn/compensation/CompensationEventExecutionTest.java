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
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
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
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
        .extracting(
            Record::getIntent,
            r -> r.getValue().getCompensableActivityId(),
            r -> r.getValue().getThrowEventId())
        .containsSequence(
            tuple(CompensationSubscriptionIntent.CREATED, "ActivityToCompensate", ""),
            tuple(
                CompensationSubscriptionIntent.TRIGGERED,
                "ActivityToCompensate",
                "CompensationThrowEvent"),
            tuple(
                CompensationSubscriptionIntent.COMPLETED,
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
        .extracting(
            Record::getIntent,
            r -> r.getValue().getCompensableActivityScopeId(),
            r -> r.getValue().getCompensationHandlerId())
        .containsSubsequence(
            tuple(
                CompensationSubscriptionIntent.COMPLETED,
                "embedded-subprocess",
                "CompensationHandler2"),
            tuple(
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
    final var process =
        createModelFromClasspathResource("/compensation/compensation-multi-instance-activity.bpmn");
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSABLE_ACTIVITY)
        .limit(3)
        .forEach(job -> ENGINE.job().withKey(job.getKey()).complete());

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SERVICE_TASK_TYPE_COMPENSATION_HANDLER)
        .complete();

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(6))
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
}
