/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.ActivityType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ExecutionListenerJobTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateExecutionListenerJob() {
    // given
    ENGINE.deployment().withXmlClasspathResource("/processes/execution-listeners.bpmn").deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertJobLifecycle(
        processInstanceKey,
        0,
        "dmk_task_start_type_1",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
  }

  @Test
  public void shouldCompleteExecutionListenerJob() {
    // given
    ENGINE.deployment().withXmlClasspathResource("/processes/execution-listeners.bpmn").deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when - then
    assertJobLifecycle(
        processInstanceKey,
        0,
        "dmk_task_start_type_1",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_1").complete();
    assertJobLifecycle(
        processInstanceKey,
        0,
        "dmk_task_start_type_1",
        JobIntent.COMPLETED,
        ActivityType.EXECUTION_LISTENER);

    // when - then
    assertJobLifecycle(
        processInstanceKey,
        1,
        "dmk_task_start_type_2",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_2").complete();
    assertJobLifecycle(
        processInstanceKey,
        1,
        "dmk_task_start_type_2",
        JobIntent.COMPLETED,
        ActivityType.EXECUTION_LISTENER);

    // when - then
    assertJobLifecycle(
        processInstanceKey,
        2,
        "dmk_task_start_type_3",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_3").complete();
    assertJobLifecycle(
        processInstanceKey,
        2,
        "dmk_task_start_type_3",
        JobIntent.COMPLETED,
        ActivityType.EXECUTION_LISTENER);

    // when - then
    assertJobLifecycle(
        processInstanceKey, 3, "dmk_task_type", JobIntent.CREATED, ActivityType.REGULAR);
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_type").complete();
    assertJobLifecycle(
        processInstanceKey, 3, "dmk_task_type", JobIntent.COMPLETED, ActivityType.REGULAR);

    // when - then
    assertJobLifecycle(
        processInstanceKey,
        4,
        "dmk_task_end_type_1",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_end_type_1").complete();
    assertJobLifecycle(
        processInstanceKey,
        4,
        "dmk_task_end_type_1",
        JobIntent.COMPLETED,
        ActivityType.EXECUTION_LISTENER);
  }

  void assertJobLifecycle(
      final long processInstanceKey,
      final long jobIndex,
      final String expectedJobType,
      final JobIntent expectedJobIntent,
      final ActivityType expectedActivityType) {
    final Record<ProcessInstanceRecordValue> activatingJob =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    final Record<JobRecordValue> jobRecord =
        RecordingExporter.jobRecords(expectedJobIntent)
            .withProcessInstanceKey(processInstanceKey)
            .skip(jobIndex)
            .getFirst();

    Assertions.assertThat(jobRecord.getValue())
        .hasElementInstanceKey(activatingJob.getKey())
        .hasElementId(activatingJob.getValue().getElementId())
        .hasProcessDefinitionKey(activatingJob.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(activatingJob.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(activatingJob.getValue().getVersion())
        .hasActivityType(expectedActivityType)
        .hasType(expectedJobType);
  }
}
