/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.VariableIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.VariableRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MultiInstanceActivityTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String ELEMENT_ID = "mi-activity";
  private static final String INPUT_COLLECTION = "items";
  private static final String INPUT_ELEMENT = "item";
  private static final String JOB_TYPE = "test";
  private static final String MESSAGE_NAME = "message";

  private static final BpmnModelInstance SERVICE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask(
              ELEMENT_ID,
              t ->
                  t.zeebeTaskType(JOB_TYPE)
                      .multiInstance(
                          b ->
                              b.zeebeInputCollection(INPUT_COLLECTION)
                                  .zeebeInputElement(INPUT_ELEMENT)))
          .endEvent()
          .done();

  private static final BpmnModelInstance SUB_PROCESS_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .subProcess(
              ELEMENT_ID,
              s ->
                  s.multiInstance(
                      b ->
                          b.zeebeInputCollection(INPUT_COLLECTION)
                              .zeebeInputElement(INPUT_ELEMENT)))
          .embeddedSubProcess()
          .startEvent()
          .serviceTask("task", t -> t.zeebeTaskType(JOB_TYPE))
          .endEvent()
          .done();

  private static final BpmnModelInstance RECEIVE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask(
              ELEMENT_ID,
              t ->
                  t.message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(INPUT_ELEMENT))
                      .multiInstance(
                          b ->
                              b.zeebeInputCollection(INPUT_COLLECTION)
                                  .zeebeInputElement(INPUT_ELEMENT)))
          .endEvent()
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameterized.Parameter(0)
  public BpmnModelInstance workflow;

  @Parameterized.Parameter(1)
  public BpmnElementType expectedElementType;

  @Parameterized.Parameters(name = "multi-instance {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      {SERVICE_TASK_WORKFLOW, BpmnElementType.SERVICE_TASK},
      {SUB_PROCESS_WORKFLOW, BpmnElementType.SUB_PROCESS},
      {RECEIVE_TASK_WORKFLOW, BpmnElementType.RECEIVE_TASK},
    };
  }

  @Before
  public void init() {
    ENGINE.deployment().withXmlResource(workflow).deploy();
  }

  @Test
  public void shouldCreateOneInstanceForEachElement() {
    // when
    final List<String> inputCollection = Arrays.asList("a", "b", "c");
    final int collectionSize = inputCollection.size();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, inputCollection)
            .create();

    // then
    final Record<WorkflowInstanceRecordValue> multiInstanceBody =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(ELEMENT_ID)
            .getFirst();

    Assertions.assertThat(multiInstanceBody.getValue())
        .hasBpmnElementType(expectedElementType)
        .hasFlowScopeKey(workflowInstanceKey);

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId(ELEMENT_ID)
                .skip(1)
                .limit(collectionSize))
        .hasSize(collectionSize)
        .extracting(Record::getValue)
        .extracting(r -> tuple(r.getFlowScopeKey(), r.getBpmnElementType()))
        .containsOnly(tuple(multiInstanceBody.getKey(), expectedElementType));
  }

  @Test
  public void shouldSetInputElementVariable() {
    // when
    final List<Integer> inputCollection = Arrays.asList(4, 5, 6);
    final int collectionSize = inputCollection.size();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, inputCollection)
            .create();

    // then
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withName(INPUT_ELEMENT)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(collectionSize))
        .hasSize(collectionSize)
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getValue)
        .containsExactly("4", "5", "6");
  }

  @Test
  public void shouldSkipIfCollectionIsEmpty() {
    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Collections.emptyList())
            .create();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId(ELEMENT_ID)
                .limit(4))
        .extracting(Record::getIntent)
        .containsExactly(
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .filterRootScope()
                .limitToWorkflowInstanceCompleted())
        .extracting(Record::getIntent)
        .contains(WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldTerminateInstancesOnTerminatingBody() {
    // given
    final List<String> inputCollection = Arrays.asList("a", "b", "c");
    final int collectionSize = inputCollection.size();
    final int elementInstanceCount = collectionSize + 1;

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, inputCollection)
            .create();

    final List<Record<WorkflowInstanceRecordValue>> elementInstances =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(ELEMENT_ID)
            .limit(elementInstanceCount)
            .asList();

    final Record<WorkflowInstanceRecordValue> multiInstanceBody = elementInstances.get(0);
    final List<Record<WorkflowInstanceRecordValue>> innerInstances = elementInstances.subList(1, 4);

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId(ELEMENT_ID)
                .skipUntil(r -> r.getPosition() > innerInstances.get(2).getPosition())
                .limit(elementInstanceCount * 2))
        .extracting(r -> tuple(r.getKey(), r.getIntent()))
        .containsExactly(
            tuple(multiInstanceBody.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(innerInstances.get(0).getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(innerInstances.get(1).getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(innerInstances.get(2).getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(innerInstances.get(0).getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(innerInstances.get(1).getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(innerInstances.get(2).getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(multiInstanceBody.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATED));

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .filterRootScope()
                .limitToWorkflowInstanceTerminated())
        .extracting(Record::getIntent)
        .contains(WorkflowInstanceIntent.ELEMENT_TERMINATED);
  }
}
