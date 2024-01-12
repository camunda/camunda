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
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.ActivityType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.ProcessInstanceRecordStream;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
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
  public void shouldCompleteProcessWithExecutionListenerJob() {
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

    // then
    assertJobLifecycle(
        processInstanceKey,
        5,
        "dmk_task_end_type_2",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_end_type_2").complete();
    assertJobLifecycle(
        processInstanceKey,
        5,
        "dmk_task_end_type_2",
        JobIntent.COMPLETED,
        ActivityType.EXECUTION_LISTENER);

    final ProcessInstanceRecordStream processInstanceRecordStream =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limitToProcessInstanceCompleted();
    assertThat(processInstanceRecordStream)
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldRetryExecutionListener() {
    // given
    ENGINE.deployment().withXmlClasspathResource("/processes/execution-listeners.bpmn").deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("dmk_task_start_type_1")
        .withRetries(1)
        .fail();

    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_1").complete();

    assertJobLifecycle(
        processInstanceKey,
        1,
        "dmk_task_start_type_2",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
  }

  @Test
  public void shouldCreateIncidentForExecutionListener() {
    // given
    ENGINE.deployment().withXmlClasspathResource("/processes/execution-listeners.bpmn").deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("dmk_task_start_type_1")
        .withRetries(0)
        .fail();

    final Record<IncidentRecordValue> firstIncident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE.incident().ofInstance(processInstanceKey).withKey(firstIncident.getKey()).resolve();
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_1").complete();

    assertJobLifecycle(
        processInstanceKey,
        1,
        "dmk_task_start_type_2",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("dmk_task_start_type_2")
        .withRetries(0)
        .fail();

    final Record<IncidentRecordValue> secondIncident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .skip(1)
            .getFirst();

    ENGINE.incident().ofInstance(processInstanceKey).withKey(secondIncident.getKey()).resolve();
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_2").complete();

    assertJobLifecycle(
        processInstanceKey,
        2,
        "dmk_task_start_type_3",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
  }

  @Test
  public void shouldCompleteExecutionListenerJobWithVariables() {
    // given
    ENGINE.deployment().withXmlClasspathResource("/processes/execution-listeners.bpmn").deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertVariable(processInstanceKey, VariableIntent.CREATED, "se_output_a", "\"a\"");
    assertVariable(processInstanceKey, VariableIntent.CREATED, "st_input_var_b", "\"b\"");

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("dmk_task_start_type_1")
        .withVariables(Map.of("el_var_c", "c", "st_input_var_b", "b_updated"))
        .complete();

    // then
    assertVariable(processInstanceKey, VariableIntent.UPDATED, "st_input_var_b", "\"b_updated\"");
    assertVariable(processInstanceKey, VariableIntent.CREATED, "el_var_c", "\"c\"");

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_2").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_3").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_type").complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("dmk_task_end_type_1")
        .withVariable("se_output_a", "a_updated")
        .complete();

    // then
    assertVariable(processInstanceKey, VariableIntent.UPDATED, "se_output_a", "\"a_updated\"");

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_end_type_2").complete();

    // then
    assertVariable(
        processInstanceKey,
        VariableIntent.CREATED,
        "merged_es_out_var_and_st_input_var",
        "\"a_updated+b_updated\"");
    assertVariable(
        processInstanceKey,
        VariableIntent.CREATED,
        "merged_es_out_var_and_el_var",
        "\"a_updated+c\"");
  }

  void assertVariable(
      final long processInstanceKey,
      final VariableIntent intent,
      final String varName,
      final String expectedVarValue) {
    final Record<VariableRecordValue> variableRecordValueRecord =
        RecordingExporter.variableRecords(intent)
            .withProcessInstanceKey(processInstanceKey)
            .withName(varName)
            .getFirst();

    Assertions.assertThat(variableRecordValueRecord.getValue())
        .hasName(varName)
        .hasValue(expectedVarValue);
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
