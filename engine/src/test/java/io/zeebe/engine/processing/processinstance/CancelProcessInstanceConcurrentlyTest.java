/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.workflowinstance;

import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.CANCEL;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_TERMINATED;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.engine.util.RecordToWrite;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class CancelWorkflowInstanceConcurrentlyTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String ELEMENT_ID = "task";
  private static final String JOB_TYPE = "test";
  private static final String INPUT_COLLECTION_VARIABLE = "items";

  private static final BpmnModelInstance SEQUENTIAL_FLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .parallelGateway("fork")
          .serviceTask(ELEMENT_ID, t -> t.zeebeJobType(JOB_TYPE))
          .serviceTask("task-after", t -> t.zeebeJobType("nope"))
          .done();

  private static final BpmnModelInstance PARALLEL_FLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .parallelGateway("fork")
          .serviceTask(ELEMENT_ID, t -> t.zeebeJobType(JOB_TYPE))
          .endEvent()
          .moveToLastGateway()
          .serviceTask("parallel-task", t -> t.zeebeJobType("nope"))
          .endEvent()
          .done();

  private static final BpmnModelInstance SUB_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .subProcess(
              "sub-process",
              s ->
                  s.embeddedSubProcess()
                      .startEvent()
                      .parallelGateway("fork")
                      .serviceTask(ELEMENT_ID, t -> t.zeebeJobType(JOB_TYPE))
                      .endEvent()
                      .moveToLastGateway()
                      .serviceTask("parallel-task", t -> t.zeebeJobType("nope"))
                      .endEvent())
          .done();

  private static final BpmnModelInstance MULTI_INSTANCE =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask(
              ELEMENT_ID,
              t ->
                  t.zeebeJobType(JOB_TYPE)
                      .multiInstance(
                          m ->
                              m.parallel()
                                  .zeebeInputCollectionExpression(INPUT_COLLECTION_VARIABLE)))
          .serviceTask("task-after", t -> t.zeebeJobType("nope"))
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String description;

  @Parameter(1)
  public BpmnModelInstance workflow;

  @Parameter(2)
  public int expectedActivatableJobs;

  @Parameter(3)
  public List<String> expectedTerminatedElementIds;

  private long workflowInstanceKey;
  private Record<JobRecordValue> createdJob;
  private Record<WorkflowInstanceRecordValue> activityActivated;

  @Parameters(name = "{0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {"sequential flow", SEQUENTIAL_FLOW, 1, Arrays.asList(PROCESS_ID)},
      {"parallel flow", PARALLEL_FLOW, 2, Arrays.asList("parallel-task", PROCESS_ID)},
      {"sub-process", SUB_PROCESS, 2, Arrays.asList("parallel-task", "sub-process", PROCESS_ID)},
      {"multi-instance", MULTI_INSTANCE, 2, Arrays.asList(ELEMENT_ID, ELEMENT_ID, PROCESS_ID)},
    };
  }

  @Before
  public void init() {
    ENGINE.deployment().withXmlResource(workflow).deploy();

    workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION_VARIABLE, Arrays.asList("one", "two"))
            .create();

    activityActivated =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(ELEMENT_ID)
            .withIntent(ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    // wait for all jobs to appear
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(expectedActivatableJobs))
        .hasSize(expectedActivatableJobs);

    createdJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withType(JOB_TYPE)
            .getFirst();

    ENGINE.stop();
  }

  @Test
  public void shouldCancelAfterJobComplete() {
    // given
    ENGINE.writeRecords(
        RecordToWrite.command()
            .job(JobIntent.COMPLETE, createdJob.getValue())
            .key(createdJob.getKey()),
        RecordToWrite.command()
            .workflowInstance(CANCEL, new WorkflowInstanceRecord())
            .key(workflowInstanceKey));

    // when
    ENGINE.start();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceTerminated()
                .filter(
                    r -> r.getIntent() == ELEMENT_TERMINATED || r.getIntent() == ELEMENT_COMPLETED))
        .extracting(r -> r.getValue().getElementId())
        .containsSubsequence(expectedTerminatedElementIds)
        .contains(ELEMENT_ID);
  }

  @Test
  public void shouldCancelAfterJobCompleted() {
    // given
    ENGINE.writeRecords(
        RecordToWrite.command()
            .job(JobIntent.COMPLETE, createdJob.getValue())
            .key(createdJob.getKey()),
        RecordToWrite.event()
            .job(JobIntent.COMPLETED, createdJob.getValue())
            .key(createdJob.getKey())
            .causedBy(0),
        RecordToWrite.command()
            .workflowInstance(CANCEL, new WorkflowInstanceRecord())
            .key(workflowInstanceKey));

    // when
    ENGINE.start();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceTerminated()
                .filter(r -> r.getIntent() == ELEMENT_TERMINATED))
        .extracting(r -> r.getValue().getElementId())
        .containsSubsequence(expectedTerminatedElementIds);
  }

  @Test
  public void shouldCancelAfterElementCompleting() {
    // given
    ENGINE.writeRecords(
        RecordToWrite.command()
            .job(JobIntent.COMPLETE, createdJob.getValue())
            .key(createdJob.getKey()),
        RecordToWrite.event()
            .job(JobIntent.COMPLETED, createdJob.getValue())
            .key(createdJob.getKey())
            .causedBy(0),
        RecordToWrite.event()
            .workflowInstance(
                WorkflowInstanceIntent.ELEMENT_COMPLETING, activityActivated.getValue())
            .key(activityActivated.getKey())
            .causedBy(1),
        RecordToWrite.command()
            .workflowInstance(CANCEL, new WorkflowInstanceRecord())
            .key(workflowInstanceKey));

    // when
    ENGINE.start();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceTerminated()
                .filter(r -> r.getIntent() == ELEMENT_TERMINATED))
        .extracting(r -> r.getValue().getElementId())
        .containsSubsequence(expectedTerminatedElementIds);
  }

  @Test
  public void shouldCancelAfterElementCompleted() {
    // given
    ENGINE.writeRecords(
        RecordToWrite.command()
            .job(JobIntent.COMPLETE, createdJob.getValue())
            .key(createdJob.getKey()),
        RecordToWrite.event()
            .job(JobIntent.COMPLETED, createdJob.getValue())
            .key(createdJob.getKey())
            .causedBy(0),
        RecordToWrite.event()
            .workflowInstance(
                WorkflowInstanceIntent.ELEMENT_COMPLETING, activityActivated.getValue())
            .key(activityActivated.getKey())
            .causedBy(1),
        RecordToWrite.event()
            .workflowInstance(
                WorkflowInstanceIntent.ELEMENT_COMPLETED, activityActivated.getValue())
            .key(activityActivated.getKey())
            .causedBy(2),
        RecordToWrite.command()
            .workflowInstance(CANCEL, new WorkflowInstanceRecord())
            .key(workflowInstanceKey));

    // when
    ENGINE.start();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceTerminated()
                .filter(r -> r.getIntent() == ELEMENT_TERMINATED))
        .extracting(r -> r.getValue().getElementId())
        .containsSubsequence(expectedTerminatedElementIds);
  }
}
