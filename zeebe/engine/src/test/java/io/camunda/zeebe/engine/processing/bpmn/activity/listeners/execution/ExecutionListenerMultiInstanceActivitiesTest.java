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
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
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

  @Test
  public void shouldExposeBeforeAllListenerVariablesToInnerInstances() {
    // given — a multi-instance service task with a single beforeAll execution listener
    final BpmnModelInstance process = process(t -> t.zeebeBeforeAllExecutionListener(BEFORE_ALL_1));
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — beforeAll completes with the input collection AND an unrelated variable that the
    // inner instances are expected to see (i.e. not just the loop collection itself)
    final String extraVarName = "extra";
    final String extraVarValue = "from-before-all";
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", ITEMS, extraVarName, extraVarValue))
        .complete();

    // and — for each inner instance (sequential MI activates one at a time), activate it and
    // capture the variables visible to that inner job, then complete it
    final List<Map<String, Object>> innerJobVariables = new ArrayList<>();
    for (int i = 0; i < ITEMS.size(); i++) {
      // wait until this iteration's inner job is available, then activate exactly one
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

    // then — every inner instance saw the extra variable produced by the beforeAll listener,
    // proving non-collection variables are propagated into the inner scope
    assertProcessCompleted(processInstanceKey);

    assertThat(innerJobVariables)
        .as(
            "every inner instance must see the non-collection variable '%s' set by the beforeAll listener",
            extraVarName)
        .hasSize(ITEMS.size())
        .allSatisfy(vars -> assertThat(vars).containsEntry(extraVarName, extraVarValue));
  }

  @Test
  public void shouldCreateBeforeAllExecutionListenerJobWithMergedHeadersOnMultiInstanceBody() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                ELEMENT_ID,
                t ->
                    t.zeebeJobType(SERVICE_TASK_TYPE)
                        // base task headers
                        .zeebeTaskHeader("baseKey", "baseValue")
                        .zeebeTaskHeader("overrideKey", "baseOverride")
                        // beforeAll execution listener with its own headers
                        .zeebeExecutionListener(
                            l ->
                                l.eventType(
                                        io.camunda.zeebe.model.bpmn.instance.zeebe
                                            .ZeebeExecutionListenerEventType.beforeAll)
                                    .type(BEFORE_ALL_1)
                                    .zeebeTaskHeader("listenerKey", "listenerValue")
                                    .zeebeTaskHeader("overrideKey", "listenerOverride"))
                        .multiInstance(
                            m ->
                                m.parallel()
                                    .zeebeInputCollectionExpression(INPUT_COLLECTION.toString())
                                    .zeebeInputElement("item")))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when – the beforeAll job is created for the multi-instance body
    final JobRecordValue beforeAllJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(BEFORE_ALL_1)
            .getFirst()
            .getValue();

    // then – headers are merged: base + listener-specific, with listener overriding conflicts
    assertThat(beforeAllJob.getCustomHeaders())
        .containsOnly(
            Map.entry("baseKey", "baseValue"),
            Map.entry("listenerKey", "listenerValue"),
            Map.entry("overrideKey", "listenerOverride"));
  }

  @Test
  public void shouldRunBeforeAllListenersInOrderOnNestedMultiInstanceSubProcessAndServiceTask() {
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
                                m.parallel()
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
                                            m.parallel()
                                                .zeebeInputCollectionExpression("innerItems")
                                                .zeebeInputElement("innerItem")))
                        .endEvent())
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — drive the execution one job at a time, in the only valid order
    // (1) outer beforeAll fires once, supplies outerItems
    final long outerMiBodyKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .withElementId("outer-sub")
            .getFirst()
            .getKey();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(outerBeforeAll)
        .withVariables(Map.of("outerItems", outerItems))
        .complete();
    final long outerBeforeAllCompletedPosition =
        RecordingExporter.jobRecords(JobIntent.COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(outerBeforeAll)
            .getFirst()
            .getPosition();

    // assert the first outer-subprocess child instance (i.e. the first iteration of the
    // multi-instance body) only activates AFTER the outer beforeAll has completed
    final long firstOuterChildActivatingPosition =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .withFlowScopeKey(outerMiBodyKey)
            .getFirst()
            .getPosition();
    assertThat(firstOuterChildActivatingPosition)
        .as("first outer subprocess child instance must activate AFTER outer beforeAll completes")
        .isGreaterThan(outerBeforeAllCompletedPosition);

    // (2) for each outer iteration: wait for THIS iteration's inner beforeAll job, complete it,
    // then complete the inner-task jobs created for this iteration. Filtering uses `skip(outer)`
    // / `skip(outer * innerItems.size())` so we never re-touch records from previous iterations,
    // and limits are scoped to exactly the records expected per iteration.
    for (int outer = 0; outer < outerItems.size(); outer++) {
      // wait for THIS iteration's inner beforeAll job to be CREATED (skip ones from previous
      // iterations) and capture its key + position
      final var innerBeforeAllCreated =
          RecordingExporter.jobRecords(JobIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .withType(innerBeforeAll)
              .skip(outer)
              .getFirst();
      final long innerBeforeAllJobKey = innerBeforeAllCreated.getKey();
      final long innerBeforeAllCreatedPosition = innerBeforeAllCreated.getPosition();

      // complete the inner beforeAll for this iteration BY KEY (not by type) so we never touch a
      // listener from another iteration
      ENGINE
          .job()
          .ofInstance(processInstanceKey)
          .withKey(innerBeforeAllJobKey)
          .withVariables(Map.of("innerItems", innerItems))
          .complete();

      final long innerBeforeAllCompletedPosition =
          RecordingExporter.jobRecords(JobIntent.COMPLETED)
              .withProcessInstanceKey(processInstanceKey)
              .withType(innerBeforeAll)
              .skip(outer)
              .getFirst()
              .getPosition();

      // exactly innerItems.size() new inner-task jobs are created for THIS iteration; collect
      // them by skipping previous iterations and limiting to this iteration's window
      final List<Record<JobRecordValue>> thisIterationInnerTaskCreated =
          RecordingExporter.jobRecords(JobIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .withType(innerTask)
              .skip(outer * innerItems.size())
              .limit(innerItems.size())
              .toList();
      assertThat(thisIterationInnerTaskCreated)
          .as("exactly %d inner-task jobs are created per outer iteration", innerItems.size())
          .hasSize(innerItems.size());

      // ordering invariants for THIS iteration:
      // (a) inner beforeAll COMPLETED position is after CREATED
      assertThat(innerBeforeAllCompletedPosition)
          .as("inner beforeAll for outer iteration %d must complete after it was created", outer)
          .isGreaterThan(innerBeforeAllCreatedPosition);
      // (b) every inner-task job for this iteration is created AFTER inner beforeAll completed
      assertThat(thisIterationInnerTaskCreated)
          .extracting(Record::getPosition)
          .as(
              "all inner-task jobs of outer iteration %d must be created AFTER inner beforeAll completes",
              outer)
          .allMatch(pos -> pos > innerBeforeAllCompletedPosition);

      // complete this iteration's inner-task jobs by key (sequential, in the order they were
      // created)
      thisIterationInnerTaskCreated.forEach(
          r -> ENGINE.job().ofInstance(processInstanceKey).withKey(r.getKey()).complete());
    }

    // then — process completes
    assertProcessCompleted(processInstanceKey);

    // and — execution listener jobs were completed in EXACT order:
    // outer-before-all, (inner-before-all)*outerItems.size()
    final List<Tuple> expectedListenerSequence = new ArrayList<>();
    expectedListenerSequence.add(tuple(outerBeforeAll, JobListenerEventType.BEFORE_ALL));
    for (int i = 0; i < outerItems.size(); i++) {
      expectedListenerSequence.add(tuple(innerBeforeAll, JobListenerEventType.BEFORE_ALL));
    }
    assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .filter(r -> r.getValue().getJobKind() == JobKind.EXECUTION_LISTENER)
                .limit(expectedListenerSequence.size())
                .map(Record::getValue))
        .extracting(JobRecordValue::getType, JobRecordValue::getJobListenerEventType)
        .as(
            "outer beforeAll fires exactly once, then each outer iteration fires its inner beforeAll exactly once before any inner-task jobs are created")
        .containsExactlyElementsOf(expectedListenerSequence);

    // and — exactly outerItems.size() * innerItems.size() inner service-task instances ran
    final long completedInnerTaskCount =
        RecordingExporter.jobRecords(JobIntent.COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(innerTask)
            .limit(outerItems.size() * innerItems.size())
            .count();
    assertThat(completedInnerTaskCount).isEqualTo((long) outerItems.size() * innerItems.size());

    // and — there is exactly ONE outer multi-instance body and ONE inner multi-instance body per
    // outer iteration (i.e. inner MI bodies match outer iterations, not outer*inner)
    final long outerMiBodyCount =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .withElementId("outer-sub")
            .limit(1)
            .count();
    assertThat(outerMiBodyCount)
        .as("there must be exactly one outer multi-instance body activation")
        .isEqualTo(1);

    final long innerMiBodyCount =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .withElementId("inner-task")
            .limit(outerItems.size())
            .count();
    assertThat(innerMiBodyCount)
        .as(
            "there must be exactly one inner multi-instance body activation per outer iteration (sequential)")
        .isEqualTo(outerItems.size());
  }

  @Test
  public void shouldCreateIncidentWhenBeforeAllListenerJobFailsAndResumeAfterResolution() {
    // given — a multi-instance service task with a single beforeAll execution listener
    final BpmnModelInstance process = process(t -> t.zeebeBeforeAllExecutionListener(BEFORE_ALL_1));
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — the beforeAll listener job is failed with no remaining retries
    final long failedJobKey =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(BEFORE_ALL_1)
            .withRetries(0)
            .fail()
            .getKey();

    // then — an incident is raised against that listener job
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(incident.getValue().getJobKey()).isEqualTo(failedJobKey);
    assertThat(incident.getValue().getErrorType())
        .isEqualTo(ErrorType.EXECUTION_LISTENER_NO_RETRIES);

    // when — incident is resolved (retries restored, then incident resolved, then job completed)
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withRetries(1)
        .updateRetries();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", ITEMS))
        .complete();
    completeAllServiceTasks(processInstanceKey, ITEMS.size());

    // then — the process completes and the listener was created/completed exactly once
    assertProcessCompleted(processInstanceKey);

    assertThat(countJobRecords(processInstanceKey, BEFORE_ALL_1, JobIntent.CREATED))
        .as("beforeAll listener job must not be re-created after incident resolution")
        .isEqualTo(1);
    assertThat(countJobRecords(processInstanceKey, BEFORE_ALL_1, JobIntent.COMPLETED))
        .as("beforeAll listener job must complete exactly once after incident resolution")
        .isEqualTo(1);
  }

  @Test
  public void shouldCancelBeforeAllListenerJobWhenProcessIsCanceled() {
    // given — a multi-instance service task with a single beforeAll execution listener
    final BpmnModelInstance process = process(t -> t.zeebeBeforeAllExecutionListener(BEFORE_ALL_1));
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // and — the beforeAll listener job exists and is still pending
    final Record<JobRecordValue> beforeAllJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(BEFORE_ALL_1)
            .getFirst();
    assertThat(beforeAllJob.getValue().getJobKind()).isEqualTo(JobKind.EXECUTION_LISTENER);

    // when — the process instance is canceled while the listener is still pending
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then — the process is terminated
    assertThat(
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .exists())
        .isTrue();

    // and — the pending beforeAll listener job is canceled (not left orphaned)
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
  public void shouldNotDuplicateBeforeAllJobAfterEngineRestartWhilePending() {
    // given — a multi-instance service task with a single beforeAll execution listener
    final BpmnModelInstance process = process(t -> t.zeebeBeforeAllExecutionListener(BEFORE_ALL_1));
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // and — the beforeAll listener job is created but not yet completed
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .getFirst();

    // when — engine snapshots and reprocesses (simulates a restart)
    ENGINE.snapshot();
    ENGINE.reprocess();

    // and — the (single) listener job is completed once after the restart
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", ITEMS))
        .complete();
    completeAllServiceTasks(processInstanceKey, ITEMS.size());

    // then — the process completes, the listener job was not duplicated, and the first child
    // instance still only activates after that single completion
    assertProcessCompleted(processInstanceKey);

    assertThat(countJobRecords(processInstanceKey, BEFORE_ALL_1, JobIntent.CREATED))
        .as("beforeAll listener job must not be re-created after engine restart")
        .isEqualTo(1);
    assertThat(countJobRecords(processInstanceKey, BEFORE_ALL_1, JobIntent.COMPLETED))
        .as("beforeAll listener job must be completed exactly once after engine restart")
        .isEqualTo(1);

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
        .as("first child instance still activates only after the (single) beforeAll completion")
        .isGreaterThan(beforeAllCompletedPosition);
  }

  @Test
  public void shouldCompleteMultiInstanceBodyImmediatelyWhenBeforeAllSuppliesEmptyCollection() {
    // given — a multi-instance service task with a single beforeAll execution listener
    final BpmnModelInstance process = process(t -> t.zeebeBeforeAllExecutionListener(BEFORE_ALL_1));
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — the beforeAll listener completes, supplying an EMPTY input collection
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", List.of()))
        .complete();

    // then — the process completes cleanly without ever activating an inner instance
    assertProcessCompleted(processInstanceKey);

    // and — the multi-instance body itself activated and then completed
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
                .withElementId(ELEMENT_ID)
                .exists())
        .as("multi-instance body must complete even when beforeAll supplies an empty collection")
        .isTrue();

    // and — no inner SERVICE_TASK was ever activated, and no service-task job was ever created
    final var recordsForInstance =
        RecordingExporter.records().betweenProcessInstance(processInstanceKey).toList();

    assertThat(recordsForInstance)
        .as("no inner multi-instance child may be activated when the collection is empty")
        .filteredOn(r -> r.getValueType() == ValueType.PROCESS_INSTANCE)
        .filteredOn(r -> r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .map(r -> ((ProcessInstanceRecordValue) r.getValue()))
        .noneMatch(
            v ->
                v.getBpmnElementType() == BpmnElementType.SERVICE_TASK
                    && ELEMENT_ID.equals(v.getElementId()));

    assertThat(recordsForInstance)
        .as("no inner service-task job may be created when the collection is empty")
        .filteredOn(r -> r.getValueType() == ValueType.JOB)
        .filteredOn(r -> r.getIntent() == JobIntent.CREATED)
        .map(r -> ((JobRecordValue) r.getValue()).getType())
        .doesNotContain(SERVICE_TASK_TYPE);
  }

  @Test
  public void shouldPopulateOutputCollectionAfterBeforeAllSuppliesInputCollection() {
    // given — a multi-instance service task with a beforeAll listener AND an output collection
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

    // when — beforeAll listener supplies the input collection, then each inner instance runs
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", ITEMS))
        .complete();
    completeAllServiceTasks(processInstanceKey, ITEMS.size());

    // then — the process completes
    assertProcessCompleted(processInstanceKey);

    // and — the output collection variable is propagated to the process scope and contains one
    // entry per input element, each evaluated by the output-element expression (item * 10)
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
  public void shouldCancelBeforeAllListenerJobWhenMultiInstanceBodyIsTerminatedViaModification() {
    // given — a multi-instance service task with a single beforeAll execution listener
    final BpmnModelInstance process = process(t -> t.zeebeBeforeAllExecutionListener(BEFORE_ALL_1));
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // and — the beforeAll listener job exists and is still pending
    final Record<JobRecordValue> beforeAllJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(BEFORE_ALL_1)
            .getFirst();
    assertThat(beforeAllJob.getValue().getJobKind()).isEqualTo(JobKind.EXECUTION_LISTENER);

    final long miBodyElementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .withElementId(ELEMENT_ID)
            .getFirst()
            .getKey();

    // when — the multi-instance body element instance is terminated via process modification
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(miBodyElementInstanceKey)
        .modify();

    // then — the multi-instance body is terminated and the process completes (no other tokens)
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
                .withElementId(ELEMENT_ID)
                .exists())
        .isTrue();

    // and — the pending beforeAll listener job is canceled (not left orphaned)
    final Record<JobRecordValue> canceled =
        RecordingExporter.jobRecords(JobIntent.CANCELED)
            .withProcessInstanceKey(processInstanceKey)
            .withRecordKey(beforeAllJob.getKey())
            .withType(BEFORE_ALL_1)
            .getFirst();
    assertThat(canceled.getValue().getJobKind()).isEqualTo(JobKind.EXECUTION_LISTENER);
    assertThat(canceled.getValue().getJobListenerEventType())
        .isEqualTo(JobListenerEventType.BEFORE_ALL);

    // and — the listener never completed and no inner SERVICE_TASK was ever activated
    final var recordsForInstance =
        RecordingExporter.records().betweenProcessInstance(processInstanceKey).toList();

    assertThat(recordsForInstance)
        .as("a terminated beforeAll listener must never be observed as COMPLETED")
        .filteredOn(r -> r.getValueType() == ValueType.JOB)
        .filteredOn(r -> r.getIntent() == JobIntent.COMPLETED)
        .map(r -> ((JobRecordValue) r.getValue()).getType())
        .doesNotContain(BEFORE_ALL_1);

    assertThat(recordsForInstance)
        .as("no inner multi-instance child may be activated when beforeAll never completed")
        .filteredOn(r -> r.getValueType() == ValueType.PROCESS_INSTANCE)
        .filteredOn(r -> r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .map(r -> ((ProcessInstanceRecordValue) r.getValue()))
        .noneMatch(
            v ->
                v.getBpmnElementType() == BpmnElementType.SERVICE_TASK
                    && ELEMENT_ID.equals(v.getElementId()));
  }

  @Test
  public void shouldRunBeforeAllListenerOnMultiInstanceReceiveTask() {
    // given — a multi-instance receive task with a beforeAll listener; the input element doubles
    // as the message correlation key so each iteration awaits its own message
    final String receiveTaskElementId = "receive_task";
    final String messageName = "test-message";
    final List<String> correlationKeys = List.of("k1", "k2", "k3");

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .receiveTask(
                receiveTaskElementId,
                r ->
                    r.message(m -> m.name(messageName).zeebeCorrelationKeyExpression("item"))
                        .zeebeBeforeAllExecutionListener(BEFORE_ALL_1)
                        .multiInstance(
                            mi ->
                                mi.parallel()
                                    .zeebeInputCollectionExpression("items")
                                    .zeebeInputElement("item")))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<JobRecordValue> beforeAllJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(BEFORE_ALL_1)
            .getFirst();
    assertThat(beforeAllJob.getValue().getJobKind()).isEqualTo(JobKind.EXECUTION_LISTENER);

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", correlationKeys))
        .complete();

    final var subscriptionsCreated =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withMessageName(messageName)
            .limit(correlationKeys.size())
            .toList();
    assertThat(subscriptionsCreated)
        .as("exactly one message subscription is created per inner receive-task instance")
        .hasSize(correlationKeys.size());

    correlationKeys.forEach(
        key ->
            ENGINE
                .message()
                .withName(messageName)
                .withCorrelationKey(key)
                .withTimeToLive(0)
                .publish());

    assertProcessCompleted(processInstanceKey);
  }

  @Test
  public void shouldRunBeforeAllListenerOnMultiInstanceBusinessRuleTask() {
    // given — a multi-instance business rule task in job-worker mode with a beforeAll listener
    final String businessRuleTaskElementId = "business_rule_task";
    final String businessRuleJobType = "business-rule-job";

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .businessRuleTask(
                businessRuleTaskElementId,
                t ->
                    t.zeebeJobType(businessRuleJobType)
                        .zeebeBeforeAllExecutionListener(BEFORE_ALL_1)
                        .multiInstance(
                            m ->
                                m.parallel()
                                    .zeebeInputCollectionExpression("items")
                                    .zeebeInputElement("item")))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<JobRecordValue> beforeAllJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(BEFORE_ALL_1)
            .getFirst();
    assertThat(beforeAllJob.getValue().getJobKind()).isEqualTo(JobKind.EXECUTION_LISTENER);

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", ITEMS))
        .complete();

    for (int i = 0; i < ITEMS.size(); i++) {
      completeJobByType(processInstanceKey, businessRuleJobType, i);
    }

    assertProcessCompleted(processInstanceKey);

    final long beforeAllCompletedPosition =
        RecordingExporter.jobRecords(JobIntent.COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(BEFORE_ALL_1)
            .getFirst()
            .getPosition();

    final var businessRuleJobsCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(businessRuleJobType)
            .limit(ITEMS.size())
            .map(Record::getPosition)
            .toList();

    assertThat(businessRuleJobsCreated)
        .as("exactly %d business-rule jobs must be created", ITEMS.size())
        .hasSize(ITEMS.size())
        .as("every business-rule job must be created AFTER the beforeAll listener completes")
        .allMatch(pos -> pos > beforeAllCompletedPosition);
  }

  @Test
  public void shouldTriggerBoundaryTimerEventOnMultiInstanceBodyAfterBeforeAllCompletes() {
    // given — a multi-instance service task with a beforeAll listener AND an interrupting timer
    // boundary attached to the activity (semantically attached to the multi-instance body)
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

    // when — the beforeAll listener completes (this is when the MI body activates and subscribes
    // to its boundary events)
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", ITEMS))
        .complete();

    // wait for the boundary timer subscription to actually exist before advancing time
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withHandlerNodeId(boundaryEventId)
        .getFirst();

    ENGINE.increaseTime(Duration.ofHours(2));

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

  @Test
  public void shouldNotSubscribeBoundaryEventWhileBeforeAllListenerIsPending() {
    // given — a multi-instance service task with a beforeAll listener AND an interrupting timer
    // boundary
    final String boundaryEventId = "boundary_timer";

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
            .endEvent("canceled")
            .moveToActivity(ELEMENT_ID)
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // and — the beforeAll listener is pending
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .getFirst();

    // when — the beforeAll listener completes (gives the bounded search below a clear end marker)
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BEFORE_ALL_1)
        .withVariables(Map.of("items", ITEMS))
        .complete();

    // then — between process start and beforeAll completion, NO boundary-event timer was created.
    // The MI body subscribes to its events in onActivate, which only runs after the listener
    // completes; this test documents that observable contract.
    final long beforeAllCompletedPosition =
        RecordingExporter.jobRecords(JobIntent.COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(BEFORE_ALL_1)
            .getFirst()
            .getPosition();

    final boolean anyBoundaryTimerCreatedBeforeListenerCompleted =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withHandlerNodeId(boundaryEventId)
            .limit(1)
            .map(Record::getPosition)
            .findFirst()
            .map(pos -> pos < beforeAllCompletedPosition)
            .orElse(false);

    assertThat(anyBoundaryTimerCreatedBeforeListenerCompleted)
        .as("boundary timer must not be subscribed while the beforeAll listener is still pending")
        .isFalse();

    // cleanup so the shared engine state stays clean for sibling tests
    completeAllServiceTasks(processInstanceKey, ITEMS.size());
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

  private static long countJobRecords(
      final long processInstanceKey, final String jobType, final JobIntent intent) {
    return RecordingExporter.records()
        .betweenProcessInstance(processInstanceKey)
        .withValueType(ValueType.JOB)
        .withIntent(intent)
        .map(Record::getValue)
        .map(JobRecordValue.class::cast)
        .filter(v -> jobType.equals(v.getType()))
        .count();
  }
}
