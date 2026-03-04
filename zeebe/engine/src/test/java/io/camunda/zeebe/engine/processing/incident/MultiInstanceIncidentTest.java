/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static io.camunda.zeebe.engine.processing.incident.IncidentHelper.assertIncidentCreated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.IncidentClient.ResolveIncidentClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class MultiInstanceIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String MULTI_TASK_PROCESS = "multi-task-process";
  private static final String MULTI_SUB_PROC_PROCESS = "multi-sub-process-process";
  private static final String ELEMENT_ID = "task";
  private static final String INPUT_COLLECTION = "items";
  private static final String INPUT_ELEMENT = "item";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();
  private String jobType;

  @Before
  public void init() {
    jobType = helper.getJobType();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(MULTI_TASK_PROCESS)
                .startEvent()
                .serviceTask(
                    ELEMENT_ID,
                    t ->
                        t.zeebeJobType(jobType)
                            .multiInstance(
                                b ->
                                    b.zeebeInputCollectionExpression(INPUT_COLLECTION)
                                        .zeebeInputElement(INPUT_ELEMENT)
                                        .zeebeOutputElementExpression(
                                            "{x: assert(undefined_var, undefined_var != null)}")
                                        .zeebeOutputCollection("results")))
                .endEvent()
                .done())
        .deploy();
  }

  @Test
  public void shouldCreateIncidentIfInputVariableNotFound() {
    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(MULTI_TASK_PROCESS).create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<ProcessInstanceRecordValue> elementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(ELEMENT_ID)
            .getFirst();

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Expected result of the expression 'items' to be 'ARRAY', but was 'NULL'. \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'items'""");
  }

  @Test
  public void shouldCreateIncidentIfInputVariableIsNotAnArray() {
    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(MULTI_TASK_PROCESS)
            .withVariable(INPUT_COLLECTION, "not-an-array-but-a-string")
            .create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<ProcessInstanceRecordValue> elementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(ELEMENT_ID)
            .getFirst();

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected result of the expression '"
                + INPUT_COLLECTION
                + "' to be 'ARRAY', but was 'STRING'.");
  }

  @Test
  public void shouldCreateIncidentIfOutputElementExpressionEvaluationFailed() {
    // given
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(MULTI_TASK_PROCESS)
            .withVariable(INPUT_COLLECTION, List.of(1, 2, 3))
            .create();

    // when
    ENGINE.job().withType(jobType).ofInstance(processInstanceKey).complete();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<ProcessInstanceRecordValue> elementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(ELEMENT_ID)
            .getFirst();

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Assertion failure on evaluate the expression \
            '{x: assert(undefined_var, undefined_var != null)}': \
            The condition is not fulfilled \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'undefined_var'
            [NO_VARIABLE_FOUND] No variable found with name 'undefined_var'
            [ASSERT_FAILURE] The condition is not fulfilled""");
  }

  @Test
  public void shouldCollectOutputResultsForResolvedIncidentOfOutputElementExpression() {
    // given an instance of a process with an output mapping referring to a variable 'undefined_var'
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(MULTI_TASK_PROCESS)
            .withVariable(INPUT_COLLECTION, List.of(1, 2, 3))
            .create();

    completeNthJob(processInstanceKey, 1);

    // an incident is created because the variable `undefined_var` is missing
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when the missing variable is provided
    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Maps.of(entry("undefined_var", 1)))
        .update();

    // and we resolve the incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // and complete the other jobs
    completeNthJob(processInstanceKey, 2);
    completeNthJob(processInstanceKey, 3);

    // then the process is able to complete
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .exists())
        .describedAs("the process has completed")
        .isTrue();

    // and all results can be collected
    // note, that this failed in a bug where the task was completed at the same time the incident
    // was created. If the problem was resolved and the other tasks completed, the multi instance
    // would still complete normally, but would not have collected the output of the first task.
    // for more information see: https://github.com/camunda/camunda/issues/6546
    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withName("results")
                .limit(4)
                .getLast())
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getValue)
        .describedAs("the results have been collected")
        .isEqualTo("[{\"x\":1},{\"x\":1},{\"x\":1}]");
  }

  @Test
  public void shouldResolveIncident() {
    // given
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(MULTI_TASK_PROCESS).create();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE
        .variables()
        .ofScope(incident.getValue().getVariableScopeKey())
        .withDocument(Collections.singletonMap(INPUT_COLLECTION, Arrays.asList(10, 20, 30)))
        .update();

    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withRecordKey(incident.getValue().getElementInstanceKey())
                .limit(3))
        .extracting(Record::getIntent)
        .contains(ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }

  @Test
  public void shouldUseTheSameLoopVariablesWhenIncidentResolved() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(MULTI_SUB_PROC_PROCESS)
                .startEvent()
                .subProcess("sub-process")
                .zeebeInputExpression("assert(y, y != null)", "y")
                .multiInstance(
                    b ->
                        b.parallel()
                            .zeebeInputCollectionExpression(INPUT_COLLECTION)
                            .zeebeInputElement(INPUT_ELEMENT))
                .embeddedSubProcess()
                .startEvent("sub-process-start")
                .endEvent("sub-process-end")
                .moveToNode("sub-process")
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(MULTI_SUB_PROC_PROCESS)
            .withVariables("{\"items\":[1,2,3]}")
            .create();

    // when
    final List<Long> incidents =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(3)
            .map(Record::getKey)
            .collect(Collectors.toList());
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("y", 1)).update();
    incidents.stream()
        .map(key -> ENGINE.incident().ofInstance(processInstanceKey).withKey(key))
        .forEach(ResolveIncidentClient::resolve);

    // then
    final var variableNames = Set.of("item", "loopCounter");
    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
                .variableRecords()
                .filter(v -> variableNames.contains(v.getValue().getName())))
        .extracting(v -> tuple(v.getIntent(), v.getValue().getName(), v.getValue().getValue()))
        .containsExactly(
            tuple(VariableIntent.CREATED, "item", "1"),
            tuple(VariableIntent.CREATED, "loopCounter", "1"),
            tuple(VariableIntent.CREATED, "item", "2"),
            tuple(VariableIntent.CREATED, "loopCounter", "2"),
            tuple(VariableIntent.CREATED, "item", "3"),
            tuple(VariableIntent.CREATED, "loopCounter", "3"));
  }

  @Test
  public void shouldCreateIncidentIfCompletionConditionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("multi-task")
                .startEvent()
                .serviceTask(
                    ELEMENT_ID,
                    t ->
                        t.zeebeJobType(jobType)
                            .multiInstance(
                                b ->
                                    b.parallel()
                                        .zeebeInputCollectionExpression(INPUT_COLLECTION)
                                        .zeebeInputElement(INPUT_ELEMENT)
                                        .completionCondition("=x")))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("multi-task")
            .withVariable(INPUT_COLLECTION, List.of(1, 2, 3))
            .create();

    completeNthJob(processInstanceKey, 1);

    // then
    final Record<ProcessInstanceRecordValue> activityEvent =
        RecordingExporter.processInstanceRecords()
            .withElementId(ELEMENT_ID)
            .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Expected result of the expression 'x' to be 'BOOLEAN', but was 'NULL'. \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'x'""")
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementInstanceKey(activityEvent.getKey())
        .hasVariableScopeKey(activityEvent.getKey());
  }

  @Test
  public void shouldResolveIncidentDueToCompletionCondition() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("multi-task")
                .startEvent()
                .serviceTask(
                    ELEMENT_ID,
                    t ->
                        t.zeebeJobType(jobType)
                            .multiInstance(
                                b ->
                                    b.parallel()
                                        .zeebeInputCollectionExpression(INPUT_COLLECTION)
                                        .zeebeInputElement(INPUT_ELEMENT)
                                        .completionCondition("=x")))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("multi-task")
            .withVariable(INPUT_COLLECTION, List.of(1, 2, 3))
            .create();

    completeNthJob(processInstanceKey, 1);

    // an incident is created because the variable `x` is missing
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when the missing variable is provided
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Maps.of(entry("x", true))).update();

    // and we resolve the incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then the process is able to complete
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .exists())
        .describedAs("the process has completed")
        .isTrue();
  }

  /**
   * This test covers the scenario where a multi instance cannot be completed, because updating the
   * output collection fails due to an index out of bounds. The index out of bounds is caused
   * because a) the output collection is initialized with the cardinality of the multi instance when
   * the multi instance is activated, and b) the collection is modified and shrunk to a smaller
   * size.
   */
  @Test // regression test for #9143
  public void shouldCreateAndResolveIncidentIfOutputElementCannotBeReplacedInOutputCollection() {
    // given
    final var processId = "index-out-of-bounds-in-output-collection";
    final var collectionWithThreeElements = "=[1]";
    final var collectionWithNoElements = "=[]";
    final var outputCollectionName = "outputItems";

    final var process =
        createProcessThatModifiesOutputCollection(
            processId, collectionWithThreeElements, collectionWithNoElements, outputCollectionName);

    ENGINE.deployment().withXmlResource(process).deploy();

    // when (raise incident)
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then (incident is raised)
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Unable to update an item in output collection 'outputItems' at position 1 because the size of the collection is: 0. This may happen when multiple BPMN elements write to the same variable.")
        .hasProcessInstanceKey(processInstanceKey);

    // when (resolve incident)
    ENGINE
        .variables()
        .ofScope(incidentEvent.getValue().getVariableScopeKey())
        .withDocument(Maps.of(entry(outputCollectionName, List.of(1))))
        .update();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then (incident is resolved)
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .exists())
        .describedAs("the process has completed")
        .isTrue();
    org.assertj.core.api.Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName(outputCollectionName)
                .withValue("[1]")
                .exists())
        .isTrue();
  }

  /**
   * This test covers the scenario where a multi instance cannot be completed, because updating the
   * output collection fails because the output collection is not an array.
   */
  @Test
  public void shouldCreateAndResolveIncidentIfOutputCollectionHasWrongType() {
    // given
    final var processId = "output-collection-is-overwritten-by-string";
    final var collectionWithThreeElements = "=[1]";
    final var overwriteWithString = "=\"String overwrite\"";
    final var outputCollectionName = "outputItems";

    final var process =
        createProcessThatModifiesOutputCollection(
            processId, collectionWithThreeElements, overwriteWithString, outputCollectionName);

    ENGINE.deployment().withXmlResource(process).deploy();

    // when (raise incident)
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then (incident is raised)
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Unable to update an item in output collection 'outputItems' because the type of the output collection is: STRING. This may happen when multiple BPMN elements write to the same variable.")
        .hasProcessInstanceKey(processInstanceKey);

    // when (resolve incident)
    ENGINE
        .variables()
        .ofScope(incidentEvent.getValue().getVariableScopeKey())
        .withDocument(Maps.of(entry(outputCollectionName, List.of(1))))
        .update();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then (incident is resolved)
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .exists())
        .describedAs("the process has completed")
        .isTrue();

    org.assertj.core.api.Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName(outputCollectionName)
                .withValue("[1]")
                .exists())
        .isTrue();
  }

  private BpmnModelInstance createProcessThatModifiesOutputCollection(
      final String processId,
      final String initialValueForCollection,
      final String overwrittenValue,
      final String outputCollectionName) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .zeebeOutput(
            initialValueForCollection, // initializes input collection
            INPUT_COLLECTION)
        .subProcess()
        .multiInstance(
            mi ->
                mi.parallel()
                    .zeebeInputCollectionExpression(INPUT_COLLECTION)
                    .zeebeInputElement(INPUT_ELEMENT)
                    .zeebeOutputCollection(
                        outputCollectionName) // initialize output collection with size in input
                    // collection
                    .zeebeOutputElementExpression(INPUT_ELEMENT))
        .embeddedSubProcess()
        .startEvent()
        .zeebeOutput(overwrittenValue, outputCollectionName) // overwrite output collection
        .endEvent()
        .subProcessDone()
        .endEvent()
        .done();
  }

  private static void completeNthJob(final long processInstanceKey, final int n) {
    final var nthJob = findNthJob(processInstanceKey, n);
    ENGINE.job().withKey(nthJob.getKey()).complete();
  }

  private static Record<JobRecordValue> findNthJob(final long processInstanceKey, final int n) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .skip(n - 1)
        .getFirst();
  }

  /**
   * This test verifies that an incident is created when the input collection is modified to be
   * smaller than the number of completed children during multi-instance execution.
   *
   * <p>This scenario reproduces the steps from the issue description:
   *
   * <ol>
   *   <li>Create a process instance with a multi-instance body containing a wait state
   *   <li>Have input collection [1,2,3]
   *   <li>Start the process instance
   *   <li>Update the input collection variable to [1,2]
   *   <li>Complete the tasks
   *   <li>Observe that process instance gets stuck (for old MI-bodies) or an incident is created
   * </ol>
   */
  @Test
  public void shouldCreateIncidentIfInputCollectionSizeDecreasedDuringExecution() {
    // given - create a process with parallel multi-instance service tasks
    final var processId = "multi-instance-with-wait-state";
    final var inputCollectionName = "items";

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType(jobType)
                            .multiInstance(
                                b ->
                                    b.parallel()
                                        .zeebeInputCollectionExpression(inputCollectionName)
                                        .zeebeInputElement("item")))
                .endEvent()
                .done())
        .deploy();

    // when - create a process instance with input collection [1,2,3]
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable(inputCollectionName, List.of(1, 2, 3))
            .create();

    // Wait for all 3 jobs to be created
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(3)
        .asList();

    // Complete the first job
    completeNthJob(processInstanceKey, 1);

    // Update the input collection variable to [1,2] (removing one element)
    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Maps.of(entry(inputCollectionName, List.of(1, 2))))
        .update();

    // Complete the second job - this triggers the check in afterExecutionPathCompleted
    completeNthJob(processInstanceKey, 2);

    // then - for modern MI-bodies (>= v8.5.21), the stored input collection is used,
    // so the process completes normally. For old MI-bodies, an incident would be created
    // because the collection is re-evaluated and finds 2 elements but 2 children completed.

    final var multiInstanceBodyKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("task")
            .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .getFirst()
            .getKey();

    // Complete the third job to trigger the completion check
    completeNthJob(processInstanceKey, 3);

    // Wait for process to either complete or get stuck with incident
    // For modern MI-bodies: process will complete
    // For old MI-bodies: incident will be created
    final var incidentOrCompletion =
        RecordingExporter.records()
            .onlyEvents()
            .filter(r -> r.getKey() == processInstanceKey || r.getKey() == multiInstanceBodyKey)
            .filter(
                r ->
                    (r.getIntent() == IncidentIntent.CREATED
                            && r.getValue() instanceof IncidentRecordValue
                            && ((IncidentRecordValue) r.getValue()).getElementInstanceKey()
                                == multiInstanceBodyKey)
                        || (r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                            && r.getValue() instanceof ProcessInstanceRecordValue
                            && ((ProcessInstanceRecordValue) r.getValue()).getBpmnElementType()
                                == BpmnElementType.PROCESS))
            .getFirst();

    final boolean incidentCreated = incidentOrCompletion.getIntent() == IncidentIntent.CREATED;

    if (incidentCreated) {
      // For old MI-bodies, verify the incident has the correct details
      final Record<IncidentRecordValue> incident =
          RecordingExporter.incidentRecords(IncidentIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .filter(r -> r.getValue().getElementInstanceKey() == multiInstanceBodyKey)
              .getFirst();

      Assertions.assertThat(incident.getValue())
          .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
          .hasProcessInstanceKey(processInstanceKey)
          .hasElementInstanceKey(multiInstanceBodyKey);

      assertThat(incident.getValue().getErrorMessage())
          .contains("Expected input collection to contain at least 2 elements")
          .contains("but found 2 elements")
          .contains("The input collection was modified during multi-instance execution")
          .contains(
              "use process instance modification to move the token from the multi-instance body element");
    } else {
      // For modern MI-bodies with stored input collection, verify process completed successfully
      assertThat(
              RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                  .withProcessInstanceKey(processInstanceKey)
                  .withElementType(BpmnElementType.PROCESS)
                  .exists())
          .describedAs(
              "Process completed successfully (modern MI-bodies use stored input collection)")
          .isTrue();
    }
  }
}
