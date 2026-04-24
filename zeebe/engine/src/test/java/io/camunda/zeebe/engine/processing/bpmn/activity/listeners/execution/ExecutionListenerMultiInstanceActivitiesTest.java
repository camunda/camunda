/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution;

import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.END_EL_TYPE;
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.START_EL_TYPE;
import static io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution.ExecutionListenerTest.createProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.MultiInstanceLoopCharacteristicsBuilder;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ExecutionListenerMultiInstanceActivitiesTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String ELEMENT_ID = "task";
  private static final String SERVICE_TASK_TYPE = "service-task";
  private static final String BEFORE_ALL_1 = "before-all-1";
  private static final String BEFORE_ALL_2 = "before-all-2";
  private static final String BEFORE_ALL_3 = "before-all-3";
  private static final String START_1 = "start-1";
  private static final String START_2 = "start-2";
  private static final String START_3 = "start-3";
  private static final List<Integer> ITEMS = List.of(1, 2, 3);
  private static final Collection<Integer> INPUT_COLLECTION = List.of(1, 2, 3, 4);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldInvokeExecutionListenerAroundSequentialMultiInstanceActivity() {
    // given
    final long processInstanceKey = createProcessInstance(ENGINE, buildMainProcessModel(true));

    // when: simulate execution for each instance in the multi-instance activity
    executeMultiInstanceActivity(processInstanceKey);

    // then
    assertExecutionListenerEvents(processInstanceKey);
  }

  @Test
  public void shouldInvokeExecutionListenerAroundParallelMultiInstanceActivity() {
    // given
    final long processInstanceKey = createProcessInstance(ENGINE, buildMainProcessModel(false));

    // when: simulate execution for each instance in the multi-instance activity
    executeMultiInstanceActivity(processInstanceKey);

    // then
    assertExecutionListenerEvents(processInstanceKey);
  }

  @Test
  public void shouldRunSingleStartListenerOnEachInnerInstance() {
    // given
    final BpmnModelInstance process = process(t -> t.zeebeStartExecutionListener(START_1));
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("items", ITEMS).create();

    // when — drive each inner instance: start EL → service task
    for (int i = 0; i < ITEMS.size(); i++) {
      completeJobByType(processInstanceKey, START_1, i);
      completeJobByType(processInstanceKey, SERVICE_TASK_TYPE, i);
    }

    // then — exactly one start EL job per inner instance, all marked as START
    assertProcessCompleted(processInstanceKey);

    assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withType(START_1)
                .limit(ITEMS.size())
                .map(Record::getValue))
        .extracting(JobRecordValue::getJobKind, JobRecordValue::getJobListenerEventType)
        .containsOnly(tuple(JobKind.EXECUTION_LISTENER, JobListenerEventType.START))
        .hasSize(ITEMS.size());
  }

  @Test
  public void shouldRunMultipleStartListenersInDeclarationOrderOnEachInstance() {
    // given
    final BpmnModelInstance process =
        process(
            t ->
                t.zeebeStartExecutionListener(START_1)
                    .zeebeStartExecutionListener(START_2)
                    .zeebeStartExecutionListener(START_3));
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("items", ITEMS).create();

    // when — for each inner instance: start_1 → start_2 → start_3 → service task
    for (int i = 0; i < ITEMS.size(); i++) {
      completeJobByType(processInstanceKey, START_1, i);
      completeJobByType(processInstanceKey, START_2, i);
      completeJobByType(processInstanceKey, START_3, i);
      completeJobByType(processInstanceKey, SERVICE_TASK_TYPE, i);
    }

    // then — start listeners executed in declared order, repeated per inner instance
    assertProcessCompleted(processInstanceKey);
    final List<Tuple> expected = new ArrayList<>();
    for (int i = 0; i < ITEMS.size(); i++) {
      expected.add(tuple(START_1, JobListenerEventType.START));
      expected.add(tuple(START_2, JobListenerEventType.START));
      expected.add(tuple(START_3, JobListenerEventType.START));
    }
    assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .filter(r -> r.getValue().getJobKind() == JobKind.EXECUTION_LISTENER)
                .limit(expected.size())
                .map(Record::getValue))
        .extracting(JobRecordValue::getType, JobRecordValue::getJobListenerEventType)
        .containsExactlyElementsOf(expected);
  }

  @Test
  public void shouldRunSingleBeforeAllListenerBeforeChildInstancesActivate() {
    // given
    final BpmnModelInstance process = process(t -> t.zeebeBeforeAllExecutionListener(BEFORE_ALL_1));
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — beforeAll listener job is created
    final Record<JobRecordValue> beforeAllJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(BEFORE_ALL_1)
            .getFirst();

    // then — it is the right kind of job
    assertThat(beforeAllJob.getValue().getJobKind()).isEqualTo(JobKind.EXECUTION_LISTENER);
    assertThat(beforeAllJob.getValue().getJobListenerEventType())
        .isEqualTo(JobListenerEventType.BEFORE_ALL);

    // when — listener completes with the input collection and all service-task jobs complete
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", ITEMS))
        .complete();
    completeAllServiceTasks(processInstanceKey, ITEMS.size());

    // then — process completes
    assertProcessCompleted(processInstanceKey);

    // contains only BEFORE_ALL_1
    final Tuple expected = tuple(BEFORE_ALL_1, JobListenerEventType.BEFORE_ALL);
    assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .filter(r -> r.getValue().getJobKind() == JobKind.EXECUTION_LISTENER)
                .limit(1)
                .map(Record::getValue))
        .extracting(JobRecordValue::getType, JobRecordValue::getJobListenerEventType)
        .containsOnlyOnce(expected);

    // and — the first child instance only activated AFTER the beforeAll listener completed
    final long miBodyKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .getFirst()
            .getKey();

    final long beforeAllCompletedPosition =
        RecordingExporter.jobRecords(JobIntent.COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(BEFORE_ALL_1)
            .getFirst()
            .getPosition();

    final long firstChildActivatingPosition =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withFlowScopeKey(miBodyKey)
            .getFirst()
            .getPosition();

    assertThat(firstChildActivatingPosition)
        .as("first child instance should only activate after the beforeAll listener completes")
        .isGreaterThan(beforeAllCompletedPosition);
  }

  @Test
  public void shouldRunMultipleBeforeAllListenersInDeclarationOrder() {
    // given
    final BpmnModelInstance process =
        process(
            t ->
                t.zeebeBeforeAllExecutionListener(BEFORE_ALL_1)
                    .zeebeBeforeAllExecutionListener(BEFORE_ALL_2)
                    .zeebeBeforeAllExecutionListener(BEFORE_ALL_3));
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — complete the listeners one by one (the first sets the collection)
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", ITEMS))
        .complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(BEFORE_ALL_2).complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(BEFORE_ALL_3).complete();
    completeAllServiceTasks(processInstanceKey, ITEMS.size());

    // then — process completes and beforeAll jobs were created/completed in declared order
    assertProcessCompleted(processInstanceKey);

    // contains only BEFORE_ALL_1 → BEFORE_ALL_2 → BEFORE_ALL_3
    final List<Tuple> expected = new ArrayList<>();
    expected.add(tuple(BEFORE_ALL_1, JobListenerEventType.BEFORE_ALL));
    expected.add(tuple(BEFORE_ALL_2, JobListenerEventType.BEFORE_ALL));
    expected.add(tuple(BEFORE_ALL_3, JobListenerEventType.BEFORE_ALL));
    assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .filter(r -> r.getValue().getJobKind() == JobKind.EXECUTION_LISTENER)
                .limit(expected.size())
                .map(Record::getValue))
        .extracting(JobRecordValue::getType, JobRecordValue::getJobListenerEventType)
        .containsExactlyElementsOf(expected);

    // and — the first child instance only activated AFTER all the beforeAll listener completed
    final long miBodyKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .getFirst()
            .getKey();

    final long beforeAllCompletedPosition =
        RecordingExporter.jobRecords(JobIntent.COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(BEFORE_ALL_3)
            .getFirst()
            .getPosition();

    final long firstChildActivatingPosition =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withFlowScopeKey(miBodyKey)
            .getFirst()
            .getPosition();

    assertThat(firstChildActivatingPosition)
        .as("first child instance should only activate after the beforeAll listener completes")
        .isGreaterThan(beforeAllCompletedPosition);
  }

  @Test
  public void shouldRunMultipleBeforeAllThenMultipleStartListenersInOrder() {
    // given
    final BpmnModelInstance process =
        process(
            t ->
                t.zeebeBeforeAllExecutionListener(BEFORE_ALL_1)
                    .zeebeBeforeAllExecutionListener(BEFORE_ALL_2)
                    .zeebeStartExecutionListener(START_1)
                    .zeebeStartExecutionListener(START_2));
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", ITEMS))
        .complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(BEFORE_ALL_2).complete();

    for (int i = 0; i < ITEMS.size(); i++) {
      completeJobByType(processInstanceKey, START_1, i);
      completeJobByType(processInstanceKey, START_2, i);
      completeJobByType(processInstanceKey, SERVICE_TASK_TYPE, i);
    }

    // then — beforeAlls fire once in order, then each instance runs its starts in order
    assertProcessCompleted(processInstanceKey);

    final List<Tuple> expected = new ArrayList<>();
    expected.add(tuple(BEFORE_ALL_1, JobListenerEventType.BEFORE_ALL));
    expected.add(tuple(BEFORE_ALL_2, JobListenerEventType.BEFORE_ALL));
    for (int i = 0; i < ITEMS.size(); i++) {
      expected.add(tuple(START_1, JobListenerEventType.START));
      expected.add(tuple(START_2, JobListenerEventType.START));
    }
    assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .filter(r -> r.getValue().getJobKind() == JobKind.EXECUTION_LISTENER)
                .limit(expected.size())
                .map(Record::getValue))
        .extracting(JobRecordValue::getType, JobRecordValue::getJobListenerEventType)
        .containsExactlyElementsOf(expected);
  }

  @Test
  public void shouldOverwriteVariableWhenMultipleBeforeAllListenersSetTheSameVariable() {
    // given — two beforeAll listeners that will both write to the same variable ("items")
    final BpmnModelInstance process =
        process(
            t ->
                t.zeebeBeforeAllExecutionListener(BEFORE_ALL_1)
                    .zeebeBeforeAllExecutionListener(BEFORE_ALL_2));
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — first listener sets items to a 2-element list, second overwrites with a 3-element one
    final List<Integer> firstItems = List.of(1, 2);
    final List<Integer> secondItems = List.of(10, 20, 30);

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", firstItems))
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_2)
        .withVariables(Map.of("items", secondItems))
        .complete();

    // and — drive the inner service-task jobs to completion using the overwritten collection size
    completeAllServiceTasks(processInstanceKey, secondItems.size());

    // then — process completes
    assertProcessCompleted(processInstanceKey);

    // and — exactly `secondItems.size()` inner instances were activated (i.e. the second write
    // fully overwrote the first one; values were not merged)
    final long innerInstancesCount =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(ELEMENT_ID)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .limit(secondItems.size())
            .count();

    assertThat(innerInstancesCount)
        .as(
            "second beforeAll listener should overwrite the first; collection has %d elements",
            secondItems.size())
        .isEqualTo(secondItems.size());

    final List<String> seenItemValues =
        RecordingExporter.variableRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withName("item")
            .limit(secondItems.size())
            .map(r -> r.getValue().getValue())
            .toList();

    assertThat(seenItemValues)
        .as("inner instances should iterate over the collection set by the LAST beforeAll listener")
        .containsExactly("10", "20", "30");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Builds a process with a multi-instance service task; lets a customizer attach listeners. */
  private static BpmnModelInstance process(final Consumer<ServiceTaskBuilder> listenerCustomizer) {
    return multiInstanceProcess(
        ELEMENT_ID,
        listenerCustomizer,
        m -> m.sequential().zeebeInputCollectionExpression("items").zeebeInputElement("item"));
  }

  /** Used by the legacy "around" tests; collection is hard-coded as a literal in the model. */
  private static BpmnModelInstance buildMainProcessModel(final boolean sequential) {
    return multiInstanceProcess(
        "service_task",
        t -> t.zeebeStartExecutionListener(START_EL_TYPE).zeebeEndExecutionListener(END_EL_TYPE),
        m -> {
          if (sequential) {
            m.sequential();
          } else {
            m.parallel();
          }
          m.zeebeInputCollectionExpression(INPUT_COLLECTION.toString());
        });
  }

  private static BpmnModelInstance multiInstanceProcess(
      final String elementId,
      final Consumer<ServiceTaskBuilder> serviceTaskCustomizer,
      final Consumer<MultiInstanceLoopCharacteristicsBuilder> miCustomizer) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(
            elementId,
            t -> {
              t.zeebeJobType(SERVICE_TASK_TYPE);
              serviceTaskCustomizer.accept(t);
              t.multiInstance(miCustomizer);
            })
        .endEvent()
        .done();
  }

  private static void executeMultiInstanceActivity(final long processInstanceKey) {
    for (int i = 0; i < INPUT_COLLECTION.size(); i++) {
      completeJobByType(processInstanceKey, START_EL_TYPE, i);
      completeJobByType(processInstanceKey, SERVICE_TASK_TYPE, i);
      completeJobByType(processInstanceKey, END_EL_TYPE, i);
    }
  }

  private static void assertExecutionListenerEvents(final long processInstanceKey) {
    final var elementInstanceKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("service_task")
            .withElementType(BpmnElementType.SERVICE_TASK)
            .limit(INPUT_COLLECTION.size())
            .map(Record::getKey)
            .toList();

    final var actual =
        RecordingExporter.records()
            .betweenProcessInstance(processInstanceKey)
            .withValueType(ValueType.JOB)
            .withIntent(JobIntent.COMPLETED)
            .map(Record::getValue)
            .map(JobRecordValue.class::cast)
            .filter(r -> r.getJobKind() == JobKind.EXECUTION_LISTENER)
            .toList();

    final var expected = new ArrayList<Tuple>();
    for (final Long elementInstanceKey : elementInstanceKeys) {
      expected.add(tuple(START_EL_TYPE, elementInstanceKey));
      expected.add(tuple(END_EL_TYPE, elementInstanceKey));
    }

    assertThat(actual)
        .extracting(JobRecordValue::getType, JobRecordValue::getElementInstanceKey)
        .hasSize(INPUT_COLLECTION.size() * 2)
        .containsExactlyElementsOf(expected);
  }

  private static void completeJobByType(
      final long processInstanceKey, final String taskType, final int index) {
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(taskType)
            .skip(index)
            .getFirst()
            .getKey();
    ENGINE.job().ofInstance(processInstanceKey).withKey(jobKey).complete();
  }

  private static void completeAllServiceTasks(final long processInstanceKey, final int count) {
    for (int i = 0; i < count; i++) {
      completeJobByType(processInstanceKey, SERVICE_TASK_TYPE, i);
    }
  }

  private static void assertProcessCompleted(final long processInstanceKey) {
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }
}
