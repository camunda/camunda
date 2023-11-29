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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class NativeUserTaskTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String FOLLOW_UP_DATE = "2023-02-28T08:16:23+02:00";
  private static final String DUE_DATE = "2023-02-28T09:16:23+02:00";

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
  public void shouldActivateUserTask() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limit(3))
        .extracting(Record::getRecordType, Record::getIntent)
        .containsSequence(
            tuple(RecordType.COMMAND, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    final Record<ProcessInstanceRecordValue> userTask =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    Assertions.assertThat(userTask.getValue())
        .hasElementId("task")
        .hasBpmnElementType(BpmnElementType.USER_TASK)
        .hasFlowScopeKey(processInstanceKey)
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldActivateUserTaskForCustomTenant() {
    // given
    final String tenantId = "foo";
    ENGINE.deployment().withXmlResource(process()).withTenantId(tenantId).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    // then
    final Record<ProcessInstanceRecordValue> userTask =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.USER_TASK)
            .withTenantId(tenantId)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasTenantId(tenantId);
  }

  @Test
  public void shouldNotCreateJob() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords().withProcessInstanceKey(processInstanceKey))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .contains(tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING));

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .count())
        .isEqualTo(0L);
  }
}
