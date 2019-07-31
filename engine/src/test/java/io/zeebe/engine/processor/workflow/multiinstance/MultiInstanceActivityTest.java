/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.MultiInstanceLoopCharacteristicsBuilder;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.VariableIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MultiInstanceActivityTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String ELEMENT_ID = "task";
  private static final String JOB_TYPE = "test";
  private static final String INPUT_COLLECTION_VARIABLE = "items";
  private static final String INPUT_ELEMENT_VARIABLE = "item";
  private static final List<Integer> INPUT_COLLECTION = Arrays.asList(10, 20, 30);

  private static final Consumer<MultiInstanceLoopCharacteristicsBuilder> INPUT_VARIABLE_BUILDER =
      multiInstance(
          m ->
              m.zeebeInputCollection(INPUT_COLLECTION_VARIABLE)
                  .zeebeInputElement(INPUT_ELEMENT_VARIABLE));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameterized.Parameter(0)
  public String loopCharacteristics;

  @Parameterized.Parameter(1)
  public Consumer<MultiInstanceLoopCharacteristicsBuilder> miBuilder;

  @Parameterized.Parameter(2)
  public List<Tuple> expectedLifecycle;

  private static BpmnModelInstance workflow(
      final Consumer<MultiInstanceLoopCharacteristicsBuilder> builder) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(
            ELEMENT_ID,
            t -> t.zeebeTaskType(JOB_TYPE).multiInstance(INPUT_VARIABLE_BUILDER.andThen(builder)))
        .endEvent()
        .done();
  }

  @Parameterized.Parameters(name = "{0} multi-instance")
  public static Object[][] parameters() {
    return new Object[][] {
      {"parallel", multiInstance(m -> m.parallel()), parallelLifecycle()},
      {"sequential", multiInstance(m -> m.sequential()), sequentialLifecycle()},
    };
  }

  private static Consumer<MultiInstanceLoopCharacteristicsBuilder> multiInstance(
      final Consumer<MultiInstanceLoopCharacteristicsBuilder> builder) {
    return builder;
  }

  private static List<Tuple> parallelLifecycle() {
    return Arrays.asList(
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  private static List<Tuple> sequentialLifecycle() {
    return Arrays.asList(
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETING),
        tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldActivateActivitiesWithLoopCharacteristics() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_VARIABLE, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted()
                .withElementId(ELEMENT_ID))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSequence(expectedLifecycle);
  }

  @Test
  public void shouldActivateActivitiesForEachElement() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_VARIABLE, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted()
                .withElementId(ELEMENT_ID))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldCreateOneJobForEachElement() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_VARIABLE, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(INPUT_COLLECTION.size()))
        .hasSize(INPUT_COLLECTION.size())
        .extracting(Record::getValue)
        .extracting(JobRecordValue::getElementId)
        .containsOnly(ELEMENT_ID);
  }

  @Test
  public void shouldCompleteBodyWhenAllJobsAreCompleted() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_VARIABLE, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted()
                .withElementId(ELEMENT_ID))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldGoThroughMultiInstanceActivity() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_VARIABLE, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.START_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldSetInputElementVariable() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_VARIABLE, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED).withType(JOB_TYPE).limit(3))
        .flatExtracting(r -> r.getValue().getJobs())
        .extracting(j -> j.getVariables().get(INPUT_ELEMENT_VARIABLE))
        .containsExactlyElementsOf(INPUT_COLLECTION);

    final List<String> jsonInputCollection =
        INPUT_COLLECTION.stream().map(JsonUtil::toJson).collect(Collectors.toList());

    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withName(INPUT_ELEMENT_VARIABLE)
                .limit(3))
        .extracting(r -> r.getValue().getValue())
        .containsExactlyElementsOf(jsonInputCollection);
  }

  @Test
  public void shouldNotPropagateInputElementVariable() {
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_VARIABLE, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    final Record<WorkflowInstanceRecordValue> completedWorkflowInstance =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .filterRootScope()
            .getFirst();

    assertThat(
            RecordingExporter.variableRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withScopeKey(workflowInstanceKey)
                .limit(r -> r.getPosition() < completedWorkflowInstance.getPosition()))
        .extracting(r -> r.getValue().getName())
        .contains(INPUT_COLLECTION_VARIABLE)
        .doesNotContain(INPUT_ELEMENT_VARIABLE);
  }

  @Test
  public void shouldCancelJobsOnTermination() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_VARIABLE, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, 1);

    // when
    final Record<JobRecordValue> createdJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .skip(1)
            .getFirst();

    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(1))
        .hasSize(1)
        .extracting(Record::getKey)
        .containsExactly(createdJob.getKey());
  }

  @Test
  public void shouldTerminateInstancesOnTerminatingBody() {
    // given
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_VARIABLE, INPUT_COLLECTION)
            .create();

    final int completedJobs = INPUT_COLLECTION.size() - 1;
    completeJobs(workflowInstanceKey, completedJobs);

    // when
    RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementId(ELEMENT_ID)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .skip(completedJobs)
        .exists();

    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceTerminated()
                .withElementId(ELEMENT_ID))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSequence(
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldSkipIfCollectionIsEmpty() {
    // when
    ENGINE.deployment().withXmlResource(workflow(miBuilder)).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_VARIABLE, Collections.emptyList())
            .create();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId(ELEMENT_ID)
                .limit(4))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsExactly(
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_COMPLETED));

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
        .withXmlResource(
            workflow(
                miBuilder.andThen(
                    m ->
                        m.zeebeInputCollection(INPUT_COLLECTION_VARIABLE).zeebeInputElement(null))))
        .deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_VARIABLE, INPUT_COLLECTION)
            .create();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
                .withType(JOB_TYPE)
                .limit(INPUT_COLLECTION.size()))
        .flatExtracting(r -> r.getValue().getJobs())
        .flatExtracting(j -> j.getVariables().keySet())
        .containsOnly(INPUT_COLLECTION_VARIABLE);
  }

  @Test
  public void shouldIterateOverNestedInputCollection() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            workflow(
                miBuilder.andThen(
                    m -> m.zeebeInputCollection("nested." + INPUT_COLLECTION_VARIABLE))))
        .deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(
                "nested", Collections.singletonMap(INPUT_COLLECTION_VARIABLE, INPUT_COLLECTION))
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .limit(INPUT_COLLECTION.size())
        .exists();

    completeJobs(workflowInstanceKey, INPUT_COLLECTION.size());

    // then
    assertThat(
            RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
                .withType(JOB_TYPE)
                .limit(INPUT_COLLECTION.size()))
        .flatExtracting(r -> r.getValue().getJobs())
        .extracting(j -> j.getVariables().get(INPUT_ELEMENT_VARIABLE))
        .containsExactlyElementsOf(INPUT_COLLECTION);
  }

  private void completeJobs(final long workflowInstanceKey, final int count) {
    IntStream.range(0, count)
        .forEach(
            i -> {
              assertThat(
                      RecordingExporter.jobRecords(JobIntent.CREATED)
                          .withWorkflowInstanceKey(workflowInstanceKey)
                          .skip(i)
                          .exists())
                  .describedAs("Expected job %d/%d to be created", (i + 1), count)
                  .isTrue();

              ENGINE
                  .jobs()
                  .withType(JOB_TYPE)
                  .withMaxJobsToActivate(1)
                  .activate()
                  .getValue()
                  .getJobKeys()
                  .forEach(jobKey -> ENGINE.job().withKey(jobKey).complete());
            });
  }
}
