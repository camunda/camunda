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
import io.zeebe.model.bpmn.builder.MultiInstanceLoopCharacteristicsBuilder;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.VariableIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class MultiInstanceServiceTaskTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String ELEMENT_ID = "task";
  private static final String JOB_TYPE = "test";
  private static final String INPUT_COLLECTION = "items";
  private static final String INPUT_ELEMENT = "item";

  private static final BpmnModelInstance WORKFLOW =
      workflow(b -> b.zeebeInputCollection(INPUT_COLLECTION).zeebeInputElement(INPUT_ELEMENT));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance workflow(
      final Consumer<MultiInstanceLoopCharacteristicsBuilder> builder) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(ELEMENT_ID, t -> t.zeebeTaskType(JOB_TYPE).multiInstance(builder))
        .endEvent()
        .done();
  }

  @Test
  public void shouldCreateOneElementInstanceForEachElement() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Arrays.asList(10, 20, 30))
            .create();

    // then
    final Record<WorkflowInstanceRecordValue> multiInstanceBody =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(ELEMENT_ID)
            .getFirst();

    Assertions.assertThat(multiInstanceBody.getValue())
        .hasBpmnElementType(BpmnElementType.SERVICE_TASK)
        .hasFlowScopeKey(workflowInstanceKey);

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId(ELEMENT_ID)
                .skip(1)
                .limit(3))
        .hasSize(3)
        .extracting(Record::getValue)
        .extracting(r -> tuple(r.getFlowScopeKey(), r.getBpmnElementType()))
        .containsOnly(tuple(multiInstanceBody.getKey(), BpmnElementType.SERVICE_TASK));
  }

  @Test
  public void shouldCreateOneJobForEachElement() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Arrays.asList(10, 20, 30))
            .create();

    // then
    final List<Long> elementInstanceKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(ELEMENT_ID)
            .skip(1)
            .limit(3)
            .map(Record::getKey)
            .collect(Collectors.toList());

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(3))
        .hasSize(3)
        .extracting(Record::getValue)
        .extracting(r -> tuple(r.getElementId(), r.getElementInstanceKey()))
        .containsExactly(
            tuple(ELEMENT_ID, elementInstanceKey.get(0)),
            tuple(ELEMENT_ID, elementInstanceKey.get(1)),
            tuple(ELEMENT_ID, elementInstanceKey.get(2)));
  }

  @Test
  public void shouldSetInputElementVariable() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Arrays.asList(10, 20, 30))
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .limit(3)
        .exists();

    // when
    ENGINE.jobs().withType(JOB_TYPE).activate();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.ACTIVATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(3))
        .hasSize(3)
        .extracting(r -> r.getValue().getVariables().get(INPUT_ELEMENT))
        .containsExactly(10, 20, 30);
  }

  @Test
  public void shouldWriteInputElementVariableRecord() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Arrays.asList(10, 20, 30))
            .create();

    // then
    final List<Long> elementInstanceKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(ELEMENT_ID)
            .skip(1)
            .limit(3)
            .map(Record::getKey)
            .collect(Collectors.toList());

    assertThat(
            RecordingExporter.variableRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withName(INPUT_ELEMENT)
                .limit(3))
        .hasSize(3)
        .extracting(r -> tuple(r.getValue().getValue(), r.getValue().getScopeKey(), r.getIntent()))
        .containsExactly(
            tuple("10", elementInstanceKey.get(0), VariableIntent.CREATED),
            tuple("20", elementInstanceKey.get(1), VariableIntent.CREATED),
            tuple("30", elementInstanceKey.get(2), VariableIntent.CREATED));
  }

  @Test
  public void shouldCompleteBodyWhenAllElementInstancesAreCompleted() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Arrays.asList(10, 20, 30))
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .limit(3)
        .exists();

    // when
    ENGINE
        .jobs()
        .withType(JOB_TYPE)
        .activate()
        .getValue()
        .getJobKeys()
        .forEach(jobKey -> ENGINE.job().withKey(jobKey).complete());

    // then
    final List<Long> elementInstanceKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(ELEMENT_ID)
            .limit(4)
            .map(Record::getKey)
            .collect(Collectors.toList());

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId(ELEMENT_ID)
                .limit(4))
        .hasSize(4)
        .extracting(Record::getKey)
        .containsExactly(
            elementInstanceKey.get(1),
            elementInstanceKey.get(2),
            elementInstanceKey.get(3),
            elementInstanceKey.get(0));

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .filterRootScope()
                .limitToWorkflowInstanceCompleted())
        .extracting(Record::getIntent)
        .contains(WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldSkipIfCollectionIsEmpty() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();

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
        .hasSize(4)
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
  public void shouldIgnoreInputElementVariableIfNotDefined() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(workflow(m -> m.zeebeInputCollection(INPUT_COLLECTION)))
        .deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Arrays.asList(10, 20, 30))
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .limit(3)
        .exists();

    ENGINE.jobs().withType(JOB_TYPE).activate();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.ACTIVATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(3))
        .hasSize(3)
        .flatExtracting(r -> r.getValue().getVariables().keySet())
        .containsOnly(INPUT_COLLECTION);
  }

  @Test
  public void shouldIterateOverNestedInputCollection() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            workflow(b -> b.zeebeInputCollection("nested.items").zeebeInputElement(INPUT_ELEMENT)))
        .deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("nested", Collections.singletonMap("items", Arrays.asList(10, 20, 30)))
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .limit(3)
        .exists();

    // when
    ENGINE.jobs().withType(JOB_TYPE).activate();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.ACTIVATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(3))
        .hasSize(3)
        .extracting(r -> r.getValue().getVariables().get(INPUT_ELEMENT))
        .containsExactly(10, 20, 30);
  }

  @Test
  public void shouldTerminateInstancesOnTerminatingBody() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Arrays.asList(10, 20, 30))
            .create();

    final List<Record<WorkflowInstanceRecordValue>> elementInstances =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(ELEMENT_ID)
            .limit(4)
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
                .limit(8))
        .hasSize(8)
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

  @Test
  public void shouldCancelJobsOnTermination() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Arrays.asList(10, 20, 30))
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .limit(3)
        .exists();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(3))
        .hasSize(3);
  }
}
