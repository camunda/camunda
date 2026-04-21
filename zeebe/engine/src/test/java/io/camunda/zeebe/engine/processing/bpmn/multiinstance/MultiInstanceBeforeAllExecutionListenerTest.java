/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for the {@code beforeAll} execution listener event type on multi-instance activities.
 *
 * <p>{@code beforeAll} listeners run on the enclosing multi-instance body <em>before</em> the
 * input collection or loop cardinality is evaluated and before any child instances are created.
 * Variables produced by these listeners are therefore available to the {@code inputCollection}
 * expression.
 */
public class MultiInstanceBeforeAllExecutionListenerTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String ELEMENT_ID = "task";
  private static final String BEFORE_ALL_EL_TYPE = "before-all-listener";
  private static final String SECOND_BEFORE_ALL_EL_TYPE = "before-all-listener-2";
  private static final String SERVICE_TASK_TYPE = "service-task";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  // ---------------------------------------------------------------------------
  // Basic lifecycle
  // ---------------------------------------------------------------------------

  @Test
  public void shouldCreateBeforeAllListenerJobBeforeChildInstancesAreCreated() {
    // given — inputCollection variable is not set; the beforeAll listener will set it
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                ELEMENT_ID,
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeBeforeAllExecutionListener(BEFORE_ALL_EL_TYPE)
                        .multiInstance(
                            m -> m.zeebeInputCollectionExpression("items").zeebeInputElement("item")))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — beforeAll listener job is created
    final Record<JobRecordValue> listenerJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(BEFORE_ALL_EL_TYPE)
            .getFirst();

    // then — job kind and event type are correct
    assertThat(listenerJob.getValue().getJobKind()).isEqualTo(JobKind.EXECUTION_LISTENER);
    assertThat(listenerJob.getValue().getJobListenerEventType())
        .isEqualTo(JobListenerEventType.BEFORE_ALL);

    // and no child instances exist yet (the multi-instance body is still ELEMENT_ACTIVATING)
    final long miBodyKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .getFirst()
            .getKey();

    final boolean anyChildActivating =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withFlowScopeKey(miBodyKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .exists();

    assertThat(anyChildActivating)
        .as("No child instances should be created before beforeAll listener completes")
        .isFalse();
  }

  @Test
  public void shouldSetInputCollectionVariableViaBeforeAllListener() {
    // given — a process where inputCollection depends on a variable produced by the beforeAll EL
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                ELEMENT_ID,
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeBeforeAllExecutionListener(BEFORE_ALL_EL_TYPE)
                        .multiInstance(
                            m ->
                                m.zeebeInputCollectionExpression("items")
                                    .zeebeInputElement("item")))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — the beforeAll listener completes with the inputCollection variable
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_EL_TYPE)
        .withVariables(Map.of("items", List.of("a", "b", "c")))
        .complete();

    // then — the multi-instance body activates and creates 3 child instances
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(ELEMENT_ID)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .limit(3)
                .count())
        .isEqualTo(3);
  }

  @Test
  public void shouldProcessMultipleBeforeAllListenersInOrder() {
    // given — two beforeAll listeners chained before child creation
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                ELEMENT_ID,
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeBeforeAllExecutionListener(BEFORE_ALL_EL_TYPE)
                        .zeebeBeforeAllExecutionListener(SECOND_BEFORE_ALL_EL_TYPE)
                        .multiInstance(
                            m ->
                                m.zeebeInputCollectionExpression("items")
                                    .zeebeInputElement("item")))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — first beforeAll listener runs
    final Record<JobRecordValue> firstJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(BEFORE_ALL_EL_TYPE)
            .getFirst();

    assertThat(firstJob.getValue().getJobListenerEventType())
        .isEqualTo(JobListenerEventType.BEFORE_ALL);

    // first listener sets the collection
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_EL_TYPE)
        .withVariables(Map.of("items", List.of("x")))
        .complete();

    // then — second beforeAll listener is created next (no child instances yet)
    final Record<JobRecordValue> secondJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(SECOND_BEFORE_ALL_EL_TYPE)
            .getFirst();

    assertThat(secondJob.getValue().getJobListenerEventType())
        .isEqualTo(JobListenerEventType.BEFORE_ALL);

    // no child instances until both listeners complete
    final long miBodyKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withFlowScopeKey(miBodyKey)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .exists())
        .as("No child instances before all beforeAll listeners complete")
        .isFalse();

    // when — second beforeAll listener completes
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(SECOND_BEFORE_ALL_EL_TYPE)
        .complete();

    // then — 1 child instance is created (items = ["x"] from first listener)
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(ELEMENT_ID)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .limit(1)
                .count())
        .isEqualTo(1);
  }

  @Test
  public void shouldCompleteProcessWithBeforeAllListener() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                ELEMENT_ID,
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeBeforeAllExecutionListener(BEFORE_ALL_EL_TYPE)
                        .multiInstance(
                            m ->
                                m.zeebeInputCollectionExpression("items")
                                    .zeebeInputElement("item")))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — beforeAll listener sets the collection with a single item
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_EL_TYPE)
        .withVariables(Map.of("items", List.of("only-item")))
        .complete();

    // and the service task job completes
    ENGINE.job().ofInstance(processInstanceKey).withType(SERVICE_TASK_TYPE).complete();

    // then — process instance completes
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .filter(r -> r.getValue().getBpmnElementType() == BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldCreateIncidentWhenInputCollectionMissingAfterBeforeAllListener() {
    // given — beforeAll listener does NOT set `items`; expect an incident on MI body
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                ELEMENT_ID,
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeBeforeAllExecutionListener(BEFORE_ALL_EL_TYPE)
                        .multiInstance(
                            m ->
                                m.zeebeInputCollectionExpression("items")
                                    .zeebeInputElement("item")))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — beforeAll listener completes without providing `items`
    ENGINE.job().ofInstance(processInstanceKey).withType(BEFORE_ALL_EL_TYPE).complete();

    // then — an incident is raised on the multi-instance body
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldExposeMiBodyJobWithCorrectEventTypeInRecords() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                ELEMENT_ID,
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeBeforeAllExecutionListener(BEFORE_ALL_EL_TYPE)
                        .multiInstance(
                            m ->
                                m.zeebeInputCollectionExpression("items")
                                    .zeebeInputElement("item")))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — complete the listener with 2 items
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_EL_TYPE)
        .withVariables(Map.of("items", List.of(1, 2)))
        .complete();

    // then — verify job record fields for the beforeAll EL job
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withType(BEFORE_ALL_EL_TYPE)
                .map(Record::getValue))
        .extracting(JobRecordValue::getJobKind, JobRecordValue::getJobListenerEventType)
        .containsExactly(tuple(JobKind.EXECUTION_LISTENER, JobListenerEventType.BEFORE_ALL));
  }
}

