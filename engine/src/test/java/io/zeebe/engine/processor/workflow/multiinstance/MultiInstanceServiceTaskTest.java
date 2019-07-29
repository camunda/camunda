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
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.JobRecordValue;
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
  public void shouldCompleteBodyWhenAllJobsAreCompleted() {
    // given
    final List<String> inputCollection = Arrays.asList("a", "b", "c");

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, inputCollection)
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .limit(inputCollection.size())
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
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted()
                .withElementId(ELEMENT_ID))
        .hasSize(4);

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .filterRootScope()
                .limitToWorkflowInstanceCompleted())
        .extracting(Record::getIntent)
        .contains(WorkflowInstanceIntent.ELEMENT_COMPLETED);
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
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .limit(3)
        .exists();

    final List<JobRecordValue> jobs =
        ENGINE.jobs().withType(JOB_TYPE).activate().getValue().getJobs();

    // then
    assertThat(jobs)
        .hasSize(3)
        .extracting(j -> j.getVariables().get(INPUT_ELEMENT))
        .containsExactly(10, 20, 30);
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

    // then
    final List<JobRecordValue> jobs =
        ENGINE.jobs().withType(JOB_TYPE).activate().getValue().getJobs();

    assertThat(jobs)
        .hasSize(3)
        .flatExtracting(j -> j.getVariables().keySet())
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
    final List<JobRecordValue> jobs =
        ENGINE.jobs().withType(JOB_TYPE).activate().getValue().getJobs();

    // then
    assertThat(jobs)
        .hasSize(3)
        .extracting(j -> j.getVariables().get(INPUT_ELEMENT))
        .containsExactly(10, 20, 30);
  }
}
