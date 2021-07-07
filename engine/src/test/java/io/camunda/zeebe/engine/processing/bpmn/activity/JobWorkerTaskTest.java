/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.JobWorkerTaskBuilder;
import io.camunda.zeebe.engine.util.JobWorkerTaskBuilderProvider;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractJobWorkerTaskBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Verifies the behavior of tasks that are based on jobs and should be processed by job workers. For
 * example, service tasks.
 */
@RunWith(Parameterized.class)
public final class JobWorkerTaskTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter public JobWorkerTaskBuilder taskBuilder;

  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return JobWorkerTaskBuilderProvider.buildersAsParameters();
  }

  private BpmnModelInstance process(
      final Consumer<AbstractJobWorkerTaskBuilder<?, ?>> taskModifier) {
    final var processBuilder = Bpmn.createExecutableProcess(PROCESS_ID).startEvent();

    final var jobWorkerTaskBuilder = taskBuilder.build(processBuilder).id("task");
    taskModifier.accept(jobWorkerTaskBuilder);

    return jobWorkerTaskBuilder.endEvent().done();
  }

  @Test
  public void shouldActivateTask() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeJobType("test"))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(taskBuilder.getTaskType())
                .limit(3))
        .extracting(Record::getRecordType, Record::getIntent)
        .containsSequence(
            tuple(RecordType.COMMAND, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    final Record<ProcessInstanceRecordValue> taskActivating =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(taskBuilder.getTaskType())
            .getFirst();

    Assertions.assertThat(taskActivating.getValue())
        .hasElementId("task")
        .hasBpmnElementType(taskBuilder.getTaskType())
        .hasFlowScopeKey(processInstanceKey)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldCreateJob() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeJobType("test").zeebeJobRetries("5")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<ProcessInstanceRecordValue> taskActivated =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(taskBuilder.getTaskType())
            .getFirst();

    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(jobCreated.getValue())
        .hasType("test")
        .hasRetries(5)
        .hasElementInstanceKey(taskActivated.getKey())
        .hasElementId(taskActivated.getValue().getElementId())
        .hasProcessDefinitionKey(taskActivated.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(taskActivated.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(taskActivated.getValue().getVersion());
  }

  @Test
  public void shouldCreateJobWithCustomHeaders() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t -> t.zeebeJobType("test").zeebeTaskHeader("a", "b").zeebeTaskHeader("c", "d")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = jobCreated.getValue().getCustomHeaders();
    assertThat(customHeaders).hasSize(2).containsEntry("a", "b").containsEntry("c", "d");
  }

  @Test
  public void shouldCreateJobWithVariables() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(t -> t.zeebeJobType("taskWithVariables").zeebeInputExpression("x", "y")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("x", 1).create();

    // then
    final var variableCreated =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("y")
            .getFirst();

    Assertions.assertThat(variableCreated.getValue()).hasValue("1");

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    final List<JobRecordValue> activatedJobs =
        ENGINE.jobs().withType("taskWithVariables").activate().getValue().getJobs();

    assertThat(activatedJobs)
        .hasSize(1)
        .allSatisfy(
            job -> assertThat(job.getVariables()).containsEntry("x", 1).containsEntry("y", 1));
  }

  @Test
  public void shouldCompleteTask() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeJobType("test"))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("test").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(taskBuilder.getTaskType(), ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(taskBuilder.getTaskType(), ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCreateJobWithJobTypeExpression() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeJobTypeExpression("type"))).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("type", "test").create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(jobCreated.getValue()).hasType("test");
  }

  @Test
  public void shouldCreateJobWithJobRetriesExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeJobType("test").zeebeJobRetriesExpression("retries")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("retries", 8).create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(jobCreated.getValue()).hasRetries(8);
  }
}
