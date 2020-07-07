/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ServiceTaskTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance workflow(final Consumer<ServiceTaskBuilder> consumer) {
    final var builder = Bpmn.createExecutableProcess(PROCESS_ID).startEvent().serviceTask("task");

    consumer.accept(builder);

    return builder.endEvent().done();
  }

  @Test
  public void shouldCreateJobFromServiceTaskWithJobTypeExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(workflow(t -> t.zeebeJobTypeExpression("\"test\"")))
        .deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATE)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    Assertions.assertThat(job.getValue()).hasType("test");
  }

  @Test
  public void shouldCreateJobFromServiceTaskWithJobRetriesExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(workflow(t -> t.zeebeJobType("test").zeebeJobRetriesExpression("5+3")))
        .deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATE)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    Assertions.assertThat(job.getValue()).hasRetries(8);
  }

  @Test
  public void shouldActivateServiceTask() {
    // given
    ENGINE.deployment().withXmlResource(workflow(t -> t.zeebeJobType("test"))).deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .limit(2))
        .extracting(Record::getIntent)
        .containsSequence(
            WorkflowInstanceIntent.ELEMENT_ACTIVATING, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final Record<WorkflowInstanceRecordValue> serviceTask =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    Assertions.assertThat(serviceTask.getValue())
        .hasElementId("task")
        .hasBpmnElementType(BpmnElementType.SERVICE_TASK)
        .hasFlowScopeKey(workflowInstanceKey)
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldCreateJob() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(workflow(t -> t.zeebeJobType("test").zeebeJobRetries("5")))
        .deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<WorkflowInstanceRecordValue> taskActivated =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATE)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    Assertions.assertThat(job.getValue())
        .hasType("test")
        .hasRetries(5)
        .hasElementInstanceKey(taskActivated.getKey())
        .hasElementId(taskActivated.getValue().getElementId())
        .hasWorkflowKey(taskActivated.getValue().getWorkflowKey())
        .hasBpmnProcessId(taskActivated.getValue().getBpmnProcessId())
        .hasWorkflowDefinitionVersion(taskActivated.getValue().getVersion());
  }

  @Test
  public void shouldCreateJobWithWorkflowInstanceAndCustomHeaders() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            workflow(
                t -> t.zeebeJobType("test").zeebeTaskHeader("a", "b").zeebeTaskHeader("c", "d")))
        .deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATE)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders).hasSize(2).containsEntry("a", "b").containsEntry("c", "d");
  }

  @Test
  public void shouldCompleteServiceTask() {
    // given
    ENGINE.deployment().withXmlResource(workflow(t -> t.zeebeJobType("test"))).deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(workflowInstanceKey).withType("test").complete();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldResolveIncidentsWhenTerminating() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(workflow(t -> t.zeebeJobTypeExpression("nonexisting_variable")))
        .deploy();
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("foo", 10).create();
    assertThat(
            RecordingExporter.incidentRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2))
        .extracting(Record::getIntent)
        .containsExactly(IncidentIntent.CREATE, IncidentIntent.CREATED);

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceTerminated())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATED));

    assertThat(
            RecordingExporter.incidentRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(3))
        .extracting(Record::getIntent)
        .containsExactly(IncidentIntent.CREATE, IncidentIntent.CREATED, IncidentIntent.RESOLVED);
  }
}
