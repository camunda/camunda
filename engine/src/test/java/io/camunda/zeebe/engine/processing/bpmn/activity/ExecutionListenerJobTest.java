/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.AssociatedJobType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ExecutionListenerJobTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance process() {
    return process(b -> {});
  }

  private static BpmnModelInstance process(final Consumer<UserTaskBuilder> consumer) {
    final var builder =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask("task").zeebeUserTask();

    consumer.accept(builder);

    return builder.endEvent().done();
  }

  @Test
  public void shouldCreateExecutionListenerJob() {
    // given
    ENGINE.deployment().withXmlClasspathResource("/processes/execution-listeners.bpmn").deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<ProcessInstanceRecordValue> jobsActivating =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    final Record<JobRecordValue> executionListenerTask =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(executionListenerTask.getValue())
        .hasElementInstanceKey(jobsActivating.getKey())
        .hasElementId(jobsActivating.getValue().getElementId())
        .hasProcessDefinitionKey(jobsActivating.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(jobsActivating.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(jobsActivating.getValue().getVersion())
        .hasAssociatedTo(AssociatedJobType.EXECUTION_LISTENER)
        .hasType("dmk_task_start_type_1");
  }

  @Test
  public void shouldCompleteExecutionListenerJob() {
    // given
    ENGINE.deployment().withXmlClasspathResource("/processes/execution-listeners.bpmn").deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_2").complete();

    final Record<ProcessInstanceRecordValue> taskActivated =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    final Record<JobRecordValue> secondListenerTask =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .skip(1)
            .getFirst();

    Assertions.assertThat(secondListenerTask.getValue())
        .hasElementInstanceKey(taskActivated.getKey())
        .hasElementId(taskActivated.getValue().getElementId())
        .hasProcessDefinitionKey(taskActivated.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(taskActivated.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(taskActivated.getValue().getVersion())
        .hasAssociatedTo(AssociatedJobType.EXECUTION_LISTENER)
        .hasType("dmk_task_start_type_2");
  }
}
