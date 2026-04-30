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
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
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

    // when
    for (int i = 0; i < ITEMS.size(); i++) {
      completeJobByType(processInstanceKey, START_1, i);
      completeJobByType(processInstanceKey, SERVICE_TASK_TYPE, i);
    }

    // then
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

    // when
    for (int i = 0; i < ITEMS.size(); i++) {
      completeJobByType(processInstanceKey, START_1, i);
      completeJobByType(processInstanceKey, START_2, i);
      completeJobByType(processInstanceKey, START_3, i);
      completeJobByType(processInstanceKey, SERVICE_TASK_TYPE, i);
    }

    // then
    final var completedProcess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();
    final List<Tuple> expected = new ArrayList<>();
    for (int i = 0; i < ITEMS.size(); i++) {
      expected.add(tuple(START_1, JobListenerEventType.START));
      expected.add(tuple(START_2, JobListenerEventType.START));
      expected.add(tuple(START_3, JobListenerEventType.START));
    }
    assertThat(
            RecordingExporter.records()
                .limit(l -> l.getPosition() >= completedProcess.getPosition())
                .jobRecords()
                .withIntent(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .filter(r -> r.getValue().getJobKind() == JobKind.EXECUTION_LISTENER)
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

    // when
    final Record<JobRecordValue> beforeAllJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(BEFORE_ALL_1)
            .getFirst();

    // then
    assertThat(beforeAllJob.getValue().getJobKind()).isEqualTo(JobKind.EXECUTION_LISTENER);
    assertThat(beforeAllJob.getValue().getJobListenerEventType())
        .isEqualTo(JobListenerEventType.BEFORE_ALL);

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", ITEMS))
        .complete();
    completeAllServiceTasks(processInstanceKey, ITEMS.size());

    // then
    assertProcessCompleted(processInstanceKey);

    final Tuple expected = tuple(BEFORE_ALL_1, JobListenerEventType.BEFORE_ALL);
    assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .filter(r -> r.getValue().getJobKind() == JobKind.EXECUTION_LISTENER)
                .limit(1)
                .map(Record::getValue))
        .extracting(JobRecordValue::getType, JobRecordValue::getJobListenerEventType)
        .containsOnlyOnce(expected);

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

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", ITEMS))
        .complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(BEFORE_ALL_2).complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(BEFORE_ALL_3).complete();
    completeAllServiceTasks(processInstanceKey, ITEMS.size());

    // then
    assertProcessCompleted(processInstanceKey);

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

    // then
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
    // given
    final BpmnModelInstance process =
        process(
            t ->
                t.zeebeBeforeAllExecutionListener(BEFORE_ALL_1)
                    .zeebeBeforeAllExecutionListener(BEFORE_ALL_2));
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
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

    completeAllServiceTasks(processInstanceKey, secondItems.size());

    // then
    assertProcessCompleted(processInstanceKey);

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

  @Test
  public void shouldExposeBeforeAllListenerVariablesToInnerInstances() {
    // given
    final BpmnModelInstance process = process(t -> t.zeebeBeforeAllExecutionListener(BEFORE_ALL_1));
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final String extraVarName = "extra";
    final String extraVarValue = "from-before-all";
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", ITEMS, extraVarName, extraVarValue))
        .complete();

    final List<Map<String, Object>> innerJobVariables = new ArrayList<>();
    for (int i = 0; i < ITEMS.size(); i++) {
      RecordingExporter.jobRecords(JobIntent.CREATED)
          .withProcessInstanceKey(processInstanceKey)
          .withType(SERVICE_TASK_TYPE)
          .skip(i)
          .getFirst();

      final var batch =
          ENGINE.jobs().withType(SERVICE_TASK_TYPE).withMaxJobsToActivate(1).activate().getValue();
      final long activatedKey = batch.getJobKeys().getFirst();
      final JobRecordValue activated = batch.getJobs().getFirst();
      innerJobVariables.add(activated.getVariables());
      ENGINE.job().ofInstance(processInstanceKey).withKey(activatedKey).complete();
    }

    // then
    assertProcessCompleted(processInstanceKey);

    assertThat(innerJobVariables)
        .as(
            "every inner instance must see the non-collection variable '%s' set by the beforeAll listener",
            extraVarName)
        .hasSize(ITEMS.size())
        .allSatisfy(vars -> assertThat(vars).containsEntry(extraVarName, extraVarValue));
  }

  @Test
  public void shouldFireInnerBeforeAllListenerOncePerOuterIterationOnNestedMultiInstance() {
    // given
    final String outerBeforeAll = "outer-before-all";
    final String innerBeforeAll = "inner-before-all";
    final String innerTask = "inner-task";
    final List<Integer> outerItems = List.of(10, 20);
    final List<Integer> innerItems = List.of(1, 2);

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "outer-sub",
                outer ->
                    outer
                        .zeebeBeforeAllExecutionListener(outerBeforeAll)
                        .multiInstance(
                            m ->
                                m.sequential()
                                    .zeebeInputCollectionExpression("outerItems")
                                    .zeebeInputElement("outerItem"))
                        .embeddedSubProcess()
                        .startEvent()
                        .serviceTask(
                            "inner-task",
                            t ->
                                t.zeebeJobType(innerTask)
                                    .zeebeBeforeAllExecutionListener(innerBeforeAll)
                                    .multiInstance(
                                        m ->
                                            m.sequential()
                                                .zeebeInputCollectionExpression("innerItems")
                                                .zeebeInputElement("innerItem")))
                        .endEvent())
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(outerBeforeAll)
        .withVariables(Map.of("outerItems", outerItems))
        .complete();

    for (int outer = 0; outer < outerItems.size(); outer++) {
      final long innerBeforeAllJobKey =
          RecordingExporter.jobRecords(JobIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .withType(innerBeforeAll)
              .skip(outer)
              .getFirst()
              .getKey();
      ENGINE
          .job()
          .ofInstance(processInstanceKey)
          .withKey(innerBeforeAllJobKey)
          .withVariables(Map.of("innerItems", innerItems))
          .complete();
      for (int inner = 0; inner < innerItems.size(); inner++) {
        completeJobByType(processInstanceKey, innerTask, outer * innerItems.size() + inner);
      }
    }

    // then
    assertProcessCompleted(processInstanceKey);

    final List<Tuple> expected = new ArrayList<>();
    expected.add(tuple(outerBeforeAll, JobKind.EXECUTION_LISTENER));
    for (int i = 0; i < outerItems.size(); i++) {
      expected.add(tuple(innerBeforeAll, JobKind.EXECUTION_LISTENER));
      for (int j = 0; j < innerItems.size(); j++) {
        expected.add(tuple(innerTask, JobKind.BPMN_ELEMENT));
      }
    }
    assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(expected.size())
                .map(Record::getValue))
        .extracting(JobRecordValue::getType, JobRecordValue::getJobKind)
        .as("inner beforeAll fires once per outer iteration, before that iteration's tasks")
        .containsExactlyElementsOf(expected);
  }

  @Test
  public void shouldCancelBeforeAllListenerJobWhenProcessIsCanceled() {
    // given
    final BpmnModelInstance process = process(t -> t.zeebeBeforeAllExecutionListener(BEFORE_ALL_1));
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<JobRecordValue> beforeAllJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(BEFORE_ALL_1)
            .getFirst();
    assertThat(beforeAllJob.getValue().getJobKind()).isEqualTo(JobKind.EXECUTION_LISTENER);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();

    final Record<JobRecordValue> canceled =
        RecordingExporter.jobRecords(JobIntent.CANCELED)
            .withProcessInstanceKey(processInstanceKey)
            .withRecordKey(beforeAllJob.getKey())
            .withType(BEFORE_ALL_1)
            .limit(1)
            .getFirst();
    assertThat(canceled.getValue().getJobKind()).isEqualTo(JobKind.EXECUTION_LISTENER);
    assertThat(canceled.getValue().getJobListenerEventType())
        .isEqualTo(JobListenerEventType.BEFORE_ALL);
  }

  @Test
  public void shouldPopulateOutputCollectionAfterBeforeAllSuppliesInputCollection() {
    // given
    final String outputCollectionVar = "results";
    final String outputElementExpr = "= item * 10";

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                ELEMENT_ID,
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeBeforeAllExecutionListener(BEFORE_ALL_1)
                        .multiInstance(
                            m ->
                                m.sequential()
                                    .zeebeInputCollectionExpression("items")
                                    .zeebeInputElement("item")
                                    .zeebeOutputCollection(outputCollectionVar)
                                    .zeebeOutputElementExpression(outputElementExpr)))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", ITEMS))
        .complete();
    completeAllServiceTasks(processInstanceKey, ITEMS.size());

    // then
    assertProcessCompleted(processInstanceKey);

    final var outputCollectionValue =
        RecordingExporter.variableRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withName(outputCollectionVar)
            .withScopeKey(processInstanceKey)
            .getFirst()
            .getValue()
            .getValue();

    assertThat(outputCollectionValue)
        .as(
            "output collection must contain one evaluated entry per item supplied by the beforeAll listener")
        .isEqualTo("[10,20,30]");
  }

  @Test
  public void shouldTriggerBoundaryTimerEventOnMultiInstanceBodyAfterBeforeAllCompletes() {
    // given
    final String boundaryEventId = "boundary_timer";
    final String canceledEndEventId = "canceled";

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                ELEMENT_ID,
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        .zeebeBeforeAllExecutionListener(BEFORE_ALL_1)
                        .multiInstance(
                            m ->
                                m.parallel()
                                    .zeebeInputCollectionExpression("items")
                                    .zeebeInputElement("item")))
            .boundaryEvent(boundaryEventId, b -> b.cancelActivity(true).timerWithDuration("PT1H"))
            .endEvent(canceledEndEventId)
            .moveToActivity(ELEMENT_ID)
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", ITEMS))
        .complete();

    // wait for the boundary timer subscription to exist before advancing time
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withHandlerNodeId(boundaryEventId)
        .getFirst();

    ENGINE.increaseTime(Duration.ofHours(2));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
                .withElementId(ELEMENT_ID)
                .exists())
        .as("multi-instance body must be terminated by the interrupting boundary timer")
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(canceledEndEventId)
                .exists())
        .as("process must complete through the boundary-event path")
        .isTrue();

    assertProcessCompleted(processInstanceKey);
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
