/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public final class SendTaskTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateIncidentForNotSupportedSendTaskImplementation() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .sendTask("task")
                // sendTask with publish message implementation isn't supported yet
                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("123"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // then
    final var incident =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("task")
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.UNKNOWN)
        .hasErrorMessage(
            "Currently, only job worker-based implementation is supported for 'sendTask'. "
                + "To recover, model the task using a 'zeebe:taskDefinition' with a valid job type, "
                + "deploy the updated process version, and migrate the instance to it.");
  }

  @Test
  public void shouldRecoverFromIncidentViaProcessMigration() {
    // given: deploy process with unsupported publish-message SendTask implementation
    final var processV1 =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .sendTask("task")
            .message(m -> m.name("msg").zeebeCorrelationKeyExpression("123")) // unsupported
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource("process-v1.bpmn", processV1).deploy();

    // create a process instance (definition version 1)
    final long instanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // wait for incident
    final var incident =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(instanceKey)
            .withElementId("task")
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    // deploy v2 process with a correct SendTask (job worker-based implementation)
    final var processV2 =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .sendTask("task", t -> t.zeebeJobType("send-task-job"))
            .endEvent()
            .done();

    final long newDefinitionKey =
        ENGINE
            .deployment()
            .withXmlResource("process-v2.bpmn", processV2)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst()
            .getProcessDefinitionKey();

    // when: migrate the instance to the new version
    ENGINE
        .processInstance()
        .withInstanceKey(instanceKey)
        .migration()
        .withTargetProcessDefinitionKey(newDefinitionKey)
        .addMappingInstruction("task", "task")
        .migrate();

    // resolve the incident after migration
    ENGINE.incident().ofInstance(instanceKey).withKey(incident.getKey()).resolve();

    // then: a job should be created for the SendTask
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(instanceKey)
                .withElementId("task")
                .withType("send-task-job")
                .exists())
        .as("Job for SendTask should be created after incident resolution")
        .isTrue();
  }

  @Test
  public void shouldCancelProcessWithIncidentOnUnsupportedSendTask() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .sendTask("task")
                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("123"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // wait for incident on unsupported sendTask
    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("task")
        .withErrorType(ErrorType.UNKNOWN)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated()
                .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED))
        .extracting(r -> r.getValue().getElementId())
        .as("SendTask and process instance should be terminated")
        .containsExactly("task", "process");

    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("task")
                .withErrorType(ErrorType.UNKNOWN)
                .exists())
        .as("Incident should be resolved during sendTask termination")
        .isTrue();
  }
}
