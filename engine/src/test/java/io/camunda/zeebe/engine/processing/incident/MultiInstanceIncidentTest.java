/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.incident;

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
                                        .zeebeOutputElementExpression("{x: undefined_var}")
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

    Assertions.assertThat(incident.getValue())
        .hasElementInstanceKey(elementInstance.getKey())
        .hasElementId(elementInstance.getValue().getElementId())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "failed to evaluate expression '"
                + INPUT_COLLECTION
                + "': no variable found for name '"
                + INPUT_COLLECTION
                + "'");
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

    Assertions.assertThat(incident.getValue())
        .hasElementInstanceKey(elementInstance.getKey())
        .hasElementId(elementInstance.getValue().getElementId())
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

    Assertions.assertThat(incident.getValue())
        .hasElementInstanceKey(elementInstance.getKey())
        .hasElementId(elementInstance.getValue().getElementId())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "failed to evaluate expression '{x: undefined_var}': "
                + "no variable found for name 'undefined_var'");
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
    // for more information see: https://github.com/camunda/zeebe/issues/6546
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
                .zeebeInputExpression("y", "y")
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
  public void shouldResolveIncidentDueToInputCollection() {
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
                                    b.sequential()
                                        .zeebeInputCollectionExpression(INPUT_COLLECTION)
                                        .zeebeInputElement(INPUT_ELEMENT)))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("multi-task")
            .withVariable(INPUT_COLLECTION, List.of(1, 2, 3))
            .create();

    final var activatedTask =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(ELEMENT_ID)
            .getFirst();

    ENGINE
        .variables()
        .ofScope(activatedTask.getKey())
        .withDocument(Collections.singletonMap(INPUT_COLLECTION, "not a list"))
        .update();

    completeNthJob(processInstanceKey, 1);

    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE
        .variables()
        .ofScope(activatedTask.getKey())
        .withDocument(Collections.singletonMap(INPUT_COLLECTION, List.of(1, 2, 3)))
        .update();

    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then
    completeNthJob(processInstanceKey, 2);
    completeNthJob(processInstanceKey, 3);

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .limitToProcessInstanceCompleted()
        .await();
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
        .hasErrorMessage("failed to evaluate expression 'x': no variable found for name 'x'")
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
}
