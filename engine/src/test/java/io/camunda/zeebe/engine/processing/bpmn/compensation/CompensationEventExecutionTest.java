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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
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
    ENGINE.job().ofInstance(processInstanceKey).withType(Protocol.USER_TASK_JOB_TYPE).complete();

    final long jobKeyOfCompensationHandler =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withElementId("CompensationHandler")
            .getFirst()
            .getKey();

    ENGINE.job().withKey(jobKeyOfCompensationHandler).complete();

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
                BpmnElementType.USER_TASK,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                "ActivityToCompensate"),
            tuple(
                BpmnElementType.USER_TASK,
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
                BpmnElementType.USER_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATING,
                "CompensationHandler"),
            tuple(
                BpmnElementType.USER_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                "CompensationHandler"),
            tuple(
                BpmnElementType.USER_TASK,
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
    ENGINE.job().ofInstance(processInstanceKey).withType(Protocol.USER_TASK_JOB_TYPE).complete();

    final long jobKeyOfCompensationHandler =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withElementId("CompensationHandler")
            .getFirst()
            .getKey();

    ENGINE.job().withKey(jobKeyOfCompensationHandler).complete();

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
                BpmnElementType.USER_TASK,
                BpmnEventType.UNSPECIFIED,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                "ActivityToCompensate"),
            tuple(
                BpmnElementType.USER_TASK,
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
                BpmnElementType.USER_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATING,
                "CompensationHandler"),
            tuple(
                BpmnElementType.USER_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                "CompensationHandler"),
            tuple(
                BpmnElementType.USER_TASK,
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
        .withKey(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withElementId("ActivityToCompensate")
                .getFirst()
                .getKey())
        .complete();

    ENGINE
        .job()
        .withKey(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withElementId("ActivityToCompensate2")
                .getFirst()
                .getKey())
        .complete();

    final long jobKeyOfCompensationHandler =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withElementId("CompensationHandler")
            .getFirst()
            .getKey();

    ENGINE.job().withKey(jobKeyOfCompensationHandler).complete();

    final long jobKeyOfCompensationHandler2 =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withElementId("CompensationHandler2")
            .getFirst()
            .getKey();

    ENGINE.job().withKey(jobKeyOfCompensationHandler2).complete();

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
                BpmnElementType.USER_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                "CompensationHandler"),
            tuple(
                BpmnElementType.USER_TASK,
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
        .withKey(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withElementId("ActivityToCompensate")
                .getFirst()
                .getKey())
        .complete();

    ENGINE
        .job()
        .withKey(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withElementId("ActivityToCompensate2")
                .getFirst()
                .getKey())
        .complete();

    final long jobKeyOfCompensationHandler =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withElementId("CompensationHandler")
            .getFirst()
            .getKey();

    ENGINE.job().withKey(jobKeyOfCompensationHandler).complete();

    final long jobKeyOfCompensationHandler2 =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withElementId("CompensationHandler2")
            .getFirst()
            .getKey();

    ENGINE.job().withKey(jobKeyOfCompensationHandler2).complete();

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
                BpmnElementType.USER_TASK,
                BpmnEventType.COMPENSATION,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                "CompensationHandler"),
            tuple(
                BpmnElementType.USER_TASK,
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
    ENGINE.job().ofInstance(processInstanceKey).withType(Protocol.USER_TASK_JOB_TYPE).complete();

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
                BpmnElementType.USER_TASK,
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
    ENGINE.job().ofInstance(processInstanceKey).withType(Protocol.USER_TASK_JOB_TYPE).complete();

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
                BpmnElementType.USER_TASK,
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
  public void shouldTriggerCompensationHandlerInTheRightOrderForSubprocesses() {
    final var process =
        createModelFromClasspathResource("/compensation/compensation-embedded-subprocess.bpmn");

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    ENGINE.job().ofInstance(processInstanceKey).withType(Protocol.USER_TASK_JOB_TYPE).complete();
    ENGINE
        .job()
        .withKey(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withElementId("ActivityToCompensate2")
                .getFirst()
                .getKey())
        .complete();

    ENGINE
        .job()
        .withKey(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withElementId("CompensationHandler")
                .getFirst()
                .getKey())
        .complete();

    ENGINE
        .job()
        .withKey(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withElementId("CompensationHandler2")
                .getFirst()
                .getKey())
        .complete();

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(6))
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

  private BpmnModelInstance createModelFromClasspathResource(final String classpath) {
    final var resourceAsStream = getClass().getResourceAsStream(classpath);
    return Bpmn.readModelFromStream(resourceAsStream);
  }
}
