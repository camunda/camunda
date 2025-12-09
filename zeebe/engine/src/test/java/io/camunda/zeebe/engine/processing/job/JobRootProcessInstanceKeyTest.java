/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Set;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobRootProcessInstanceKeyTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String CHILD_PROCESS_ID = "child-process";
  private static final String GRANDCHILD_PROCESS_ID = "grandchild-process";
  private static final String JOB_TYPE = "test-job";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance simpleProcessWithServiceTask() {
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

  private static BpmnModelInstance childProcessWithCallActivity() {
    return Bpmn.createExecutableProcess(CHILD_PROCESS_ID)
        .startEvent()
        .callActivity("child-call", c -> c.zeebeProcessId(GRANDCHILD_PROCESS_ID))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance grandchildProcessWithServiceTask() {
    return Bpmn.createExecutableProcess(GRANDCHILD_PROCESS_ID)
        .startEvent()
        .serviceTask("grandchild-task", t -> t.zeebeJobType(JOB_TYPE))
        .endEvent()
        .done();
  }

  @Test
  public void shouldSetRootProcessInstanceKeyToProcessInstanceKeyForSimpleProcess() {
    // given
    ENGINE.deployment().withXmlResource(simpleProcessWithServiceTask()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(jobCreated.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasRootProcessInstanceKey(processInstanceKey)
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldSetRootProcessInstanceKeyToTopLevelParentForNestedCallActivities() {
    // given
    ENGINE
        .deployment()
        .withXmlResource("parent.bpmn", parentProcessWithCallActivity())
        .withXmlResource("child.bpmn", childProcessWithCallActivity())
        .withXmlResource("grandchild.bpmn", grandchildProcessWithServiceTask())
        .deploy();

    // when
    final long rootProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then - get the child process instance
    final Record<ProcessInstanceRecordValue> childProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(rootProcessInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final long childProcessInstanceKey = childProcessInstance.getKey();

    // get the grandchild process instance
    final Record<ProcessInstanceRecordValue> grandchildProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(childProcessInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final long grandchildProcessInstanceKey = grandchildProcessInstance.getKey();

    // Verify all three process instances are different
    assertThat(
            Set.of(rootProcessInstanceKey, childProcessInstanceKey, grandchildProcessInstanceKey))
        .hasSize(3);

    // get the job created in the grandchild process
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(grandchildProcessInstanceKey)
            .getFirst();

    Assertions.assertThat(jobCreated.getValue())
        .hasProcessInstanceKey(grandchildProcessInstanceKey)
        .hasRootProcessInstanceKey(rootProcessInstanceKey);
  }

  @Test
  public void shouldPropagateRootProcessInstanceKeyThroughAllJobIntents() {
    // given
    ENGINE
        .deployment()
        .withXmlResource("parent.bpmn", parentProcessWithCallActivity())
        .withXmlResource("child.bpmn", childProcessWithServiceTask())
        .deploy();

    // when
    final long rootProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<ProcessInstanceRecordValue> childProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(rootProcessInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final long childProcessInstanceKey = childProcessInstance.getKey();

    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(childProcessInstanceKey)
            .getFirst()
            .getKey();

    // Complete the job
    ENGINE.job().withKey(jobKey).complete();

    // then - verify all job events have the correct rootProcessInstanceKey
    assertThat(
            RecordingExporter.jobRecords().withProcessInstanceKey(childProcessInstanceKey).limit(2))
        .extracting(Record::getIntent, r -> r.getValue().getRootProcessInstanceKey())
        .containsExactly(
            tuple(JobIntent.CREATED, rootProcessInstanceKey),
            tuple(JobIntent.COMPLETED, rootProcessInstanceKey));
  }
}
