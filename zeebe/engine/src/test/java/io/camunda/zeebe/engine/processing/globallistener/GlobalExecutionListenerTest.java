/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/** Engine-level integration tests for global execution listeners. */
public class GlobalExecutionListenerTest {

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "serviceTask";
  private static final String SERVICE_TASK_JOB_TYPE = "service-task-job";
  private static final String GLOBAL_EL_TYPE = "global-el-job";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateGlobalExecutionListenerJobOnServiceTaskActivation() {
    // given
    engine
        .globalListener()
        .withId("global-el-start")
        .withType(GLOBAL_EL_TYPE)
        .withEventTypes("start")
        .withListenerType(GlobalListenerType.EXECUTION_LISTENER)
        .withElementTypes("serviceTask")
        .create();

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType(SERVICE_TASK_JOB_TYPE))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then: global EL job is created for service task start
    final var elJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(GLOBAL_EL_TYPE)
            .getFirst();

    assertThat(elJob.getValue()).hasJobKind(JobKind.EXECUTION_LISTENER);
    assertThat(elJob.getValue()).hasJobListenerEventType(JobListenerEventType.START);
  }

  @Test
  public void shouldNotCreateGlobalExecutionListenerJobForNonMatchingElementType() {
    // given: listener scoped to only userTask (not serviceTask)
    engine
        .globalListener()
        .withId("global-el-user-task")
        .withType(GLOBAL_EL_TYPE)
        .withEventTypes("start")
        .withListenerType(GlobalListenerType.EXECUTION_LISTENER)
        .withElementTypes("userTask")
        .create();

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType(SERVICE_TASK_JOB_TYPE))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then: the first job created is the service task job — no EL job was injected
    final var firstJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(firstJob.getValue()).hasJobKind(JobKind.BPMN_ELEMENT);
  }

  @Test
  public void shouldCreateGlobalExecutionListenerJobUsingCategoryScoping() {
    // given: listener scoped to "tasks" category (covers all task types including serviceTask)
    engine
        .globalListener()
        .withId("global-el-tasks")
        .withType(GLOBAL_EL_TYPE)
        .withEventTypes("start")
        .withListenerType(GlobalListenerType.EXECUTION_LISTENER)
        .withCategories("tasks")
        .create();

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType(SERVICE_TASK_JOB_TYPE))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then: global EL job for service task in "tasks" category
    final var elJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(GLOBAL_EL_TYPE)
            .getFirst();

    assertThat(elJob.getValue()).hasJobKind(JobKind.EXECUTION_LISTENER);
    assertThat(elJob.getValue()).hasJobListenerEventType(JobListenerEventType.START);
  }

  @Test
  public void shouldCompleteProcessAfterGlobalExecutionListenerJob() {
    // given
    engine
        .globalListener()
        .withId("global-el-start")
        .withType(GLOBAL_EL_TYPE)
        .withEventTypes("start")
        .withListenerType(GlobalListenerType.EXECUTION_LISTENER)
        .withElementTypes("serviceTask")
        .create();

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType(SERVICE_TASK_JOB_TYPE))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // complete EL job first
    engine.job().ofInstance(processInstanceKey).withType(GLOBAL_EL_TYPE).complete();

    // then: service task job is created after EL completes
    final var serviceTaskJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(SERVICE_TASK_JOB_TYPE)
            .getFirst();

    assertThat(serviceTaskJob.getValue()).hasJobKind(JobKind.BPMN_ELEMENT);

    // complete service task
    engine.job().ofInstance(processInstanceKey).withType(SERVICE_TASK_JOB_TYPE).complete();

    // process completes
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withIntent(
                    io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementId(PROCESS_ID)
                .exists())
        .isTrue();
  }
}
