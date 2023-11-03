/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class MigrateProcessInstanceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldMigrateProcess() {
    // Source process
    final String bpmnProcessId = "sourceProcess";
    final String serviceTaskElementId = "serviceTask1";
    final var sourceProcess =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .serviceTask(serviceTaskElementId, b -> b.zeebeJobType("task"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(sourceProcess).deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(bpmnProcessId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(serviceTaskElementId)
        .await();

    // Target process
    final String serviceTaskElementId2 = "serviceTask2";
    final var targetProcess =
        Bpmn.createExecutableProcess("targetProcess")
            .startEvent()
            .serviceTask(serviceTaskElementId, b -> b.zeebeJobType("task"))
            .serviceTask(serviceTaskElementId2, b -> b.zeebeJobType("task"))
            .endEvent()
            .done();

    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(targetProcess).deploy();

    final long targetProcDefKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcDefKey)
        .migrate();

    ENGINE.job().ofInstance(processInstanceKey).withType("task").complete();

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcDefKey);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(serviceTaskElementId2)
                .getFirst())
        .isNotNull();

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(serviceTaskElementId2)
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcDefKey);
  }

  @Test
  public void shouldMigrateProcessToAWithBoundaryEvent() {
    // Source process
    final String bpmnProcessId = "sourceProcess";
    final String serviceTaskElementId = "serviceTask1";
    final var sourceProcess =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .serviceTask(serviceTaskElementId, b -> b.zeebeJobType("task"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(sourceProcess).deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(bpmnProcessId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(serviceTaskElementId)
        .await();

    // Target process
    final String serviceTaskElementId2 = "serviceTask2";
    final var targetProcess =
        Bpmn.createExecutableProcess("targetProcess")
            .startEvent()
            .serviceTask(
                serviceTaskElementId,
                s ->
                    s.zeebeJobType("task")
                        .boundaryEvent(
                            "boundaryEvent",
                            t ->
                                t.timerWithDuration(Duration.ofMinutes(5))
                                    .userTask("userTask")
                                    .endEvent()))
            .serviceTask(serviceTaskElementId2, b -> b.zeebeJobType("task"))
            .endEvent()
            .done();

    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(targetProcess).deploy();

    final long targetProcDefKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcDefKey)
        .migrate();

    ENGINE.increaseTime(Duration.ofMinutes(5));

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundaryEvent")
                .getFirst())
        .isNotNull();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("userTask")
                .getFirst())
        .isNotNull();

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcDefKey);

    assert false;
  }

  @Test
  public void shouldMigrateProcessToNestingSubprocess() {
    // Source process
    final String bpmnProcessId = "sourceProcess";
    final String serviceTaskElementId = "serviceTask1";
    final var sourceProcess =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .serviceTask(serviceTaskElementId, b -> b.zeebeJobType("task"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(sourceProcess).deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(bpmnProcessId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(serviceTaskElementId)
        .await();

    // Target process
    final String serviceTaskElementId2 = "serviceTask2";
    final var targetProcess =
        Bpmn.createExecutableProcess("targetProcess")
            .startEvent()
            .subProcess(
                "subProcess",
                sp ->
                    sp.embeddedSubProcess()
                        .startEvent()
                        .serviceTask(serviceTaskElementId, b -> b.zeebeJobType("task"))
                        .endEvent())
            .serviceTask(serviceTaskElementId2, b -> b.zeebeJobType("task"))
            .endEvent()
            .done();

    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(targetProcess).deploy();

    final long targetProcDefKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcDefKey)
        .migrate();

    final Record<ProcessInstanceRecordValue> subProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("subProcess")
            .getFirst();
    Assertions.assertThat(subProcessInstance.getValue()).hasFlowScopeKey(processInstanceKey);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(serviceTaskElementId)
                .getFirst()
                .getValue())
        .hasFlowScopeKey(subProcessInstance.getKey());

    ENGINE.job().ofInstance(processInstanceKey).withType("task").complete();

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcDefKey);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("subProcess")
                .withRecordKey(subProcessInstance.getKey())
                .getFirst())
        .isNotNull();

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(serviceTaskElementId2)
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcDefKey);

    assert false;
  }
}
