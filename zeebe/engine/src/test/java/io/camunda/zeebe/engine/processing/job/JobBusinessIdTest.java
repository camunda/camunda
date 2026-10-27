/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobBusinessIdTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String CHILD_PROCESS_ID = "child-process";
  private static final String JOB_TYPE = "task-type";
  private static final String BUSINESS_ID = "order-123";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance processWithServiceTask() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance parentProcessWithCallActivity() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .callActivity("call", c -> c.zeebeProcessId(CHILD_PROCESS_ID))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance childProcessWithServiceTask() {
    return Bpmn.createExecutableProcess(CHILD_PROCESS_ID)
        .startEvent()
        .serviceTask("child-task", t -> t.zeebeJobType(JOB_TYPE))
        .endEvent()
        .done();
  }

  @Test
  public void shouldInheritBusinessIdFromOwningProcessInstance() {
    // given
    ENGINE.deployment().withXmlResource(processWithServiceTask()).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withBusinessId(BUSINESS_ID).create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(jobCreated.getValue()).hasBusinessId(BUSINESS_ID);
  }

  @Test
  public void shouldInheritBusinessIdFromOwningChildProcessInstance() {
    // given
    ENGINE
        .deployment()
        .withXmlResource("parent.bpmn", parentProcessWithCallActivity())
        .withXmlResource("child.bpmn", childProcessWithServiceTask())
        .deploy();

    // when
    final long parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withBusinessId(BUSINESS_ID).create();

    // then - the job is created in the child instance, which inherited the business ID
    final Record<ProcessInstanceRecordValue> childProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(childProcessInstance.getKey())
            .getFirst();

    Assertions.assertThat(jobCreated.getValue()).hasBusinessId(BUSINESS_ID);
  }

  @Test
  public void shouldHaveEmptyBusinessIdWhenProcessInstanceHasNone() {
    // given
    ENGINE.deployment().withXmlResource(processWithServiceTask()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(jobCreated.getValue()).hasBusinessId("");
  }

  @Test
  public void shouldRetainBusinessIdWhenRetriesUpdated() {
    // given
    ENGINE.deployment().withXmlResource(processWithServiceTask()).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withBusinessId(BUSINESS_ID).create();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    ENGINE.job().withKey(jobKey).withRetries(5).updateRetries();

    // then - the business ID survives a retries update
    final Record<JobRecordValue> retriesUpdated =
        RecordingExporter.jobRecords(JobIntent.RETRIES_UPDATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(retriesUpdated.getValue()).hasBusinessId(BUSINESS_ID);
  }

  @Test
  public void shouldRetainBusinessIdWhenJobFails() {
    // given
    ENGINE.deployment().withXmlResource(processWithServiceTask()).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withBusinessId(BUSINESS_ID).create();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    ENGINE.jobs().withType(JOB_TYPE).activate();

    // when
    ENGINE.job().withKey(jobKey).withRetries(2).fail();

    // then - the business ID survives a non-CREATED (FAILED) state update
    final Record<JobRecordValue> jobFailed =
        RecordingExporter.jobRecords(JobIntent.FAILED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(jobFailed.getValue()).hasBusinessId(BUSINESS_ID);
  }
}
