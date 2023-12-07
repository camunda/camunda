/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateProcessInstanceRejectionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectCommandWhenProcessInstanceIsUnknown() {
    // given
    final long unknownKey = 12345L;

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(unknownKey)
        .migration()
        .withTargetProcessDefinitionKey(1L)
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            String.format(
                "Expected to migrate process instance but no process instance found with key '%d'",
                unknownKey))
        .hasKey(unknownKey);
  }

  @Test
  public void shouldRejectCommandWhenTargetProcessDefinitionIsUnknown() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    final long unknownKey = 12345L;

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(unknownKey)
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            String.format(
                "Expected to migrate process instance to process definition but no process definition found with key '%d'",
                unknownKey))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectCommandWhenActiveElementIsNotMapped() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("task"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("process2")
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("task"))
                    .userTask("B")
                    .endEvent()
                    .done())
            .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    final long targetProcessDefinitionKey =
        extractTargetProcessDefinitionKey(deployment, "process2");

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            String.format(
                """
                Expected to migrate process instance '%d' \
                but no mapping instruction defined for active element with id 'A'. \
                Elements cannot be migrated without a mapping.""",
                processInstanceKey))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectCommandWhenActiveElementHasAJobIncident() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("jobType"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("process2")
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("jobType"))
                    .endEvent()
                    .done())
            .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    ENGINE.jobs().withType("jobType").withMaxJobsToActivate(1).activate();
    RecordingExporter.jobRecords(JobIntent.CREATED).withType("jobType").await();

    final Record<JobRecordValue> failedEvent =
        ENGINE.job().withType("jobType").ofInstance(processInstanceKey).withRetries(0).fail();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withJobKey(failedEvent.getKey())
            .getFirst();

    final long targetProcessDefinitionKey =
        extractTargetProcessDefinitionKey(deployment, "process2");

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "A")
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            String.format(
                """
                Expected to migrate process instance '%d' \
                but active element with id 'A' has an incident. \
                Elements cannot be migrated with an incident. \
                Please retry migration after resolving the incident.""",
                processInstanceKey))
        .hasKey(processInstanceKey);

    // after resolving the incident, the migration should succeed
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "A")
        .migrate();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .findAny())
        .describedAs("Expected to have migrated the process instance")
        .isPresent();
  }

  @Test
  public void shouldRejectCommandWhenActiveElementHasAProcessIncident() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask(
                        "A",
                        b ->
                            b.zeebeJobType("jobType")
                                .zeebeInputExpression("assert(x, x != null)", "y"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("process2")
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("jobType"))
                    .endEvent()
                    .done())
            .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final long targetProcessDefinitionKey =
        extractTargetProcessDefinitionKey(deployment, "process2");

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "A")
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            String.format(
                """
                Expected to migrate process instance '%d' \
                but active element with id 'A' has an incident. \
                Elements cannot be migrated with an incident. \
                Please retry migration after resolving the incident.""",
                processInstanceKey))
        .hasKey(processInstanceKey);

    // after resolving the incident, the migration should succeed
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 1)).update();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "A")
        .migrate();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .findAny())
        .describedAs("Expected to have migrated the process instance")
        .isPresent();
  }

  private static long extractTargetProcessDefinitionKey(
      final Record<DeploymentRecordValue> deployment, final String bpmnProcessId) {
    return deployment.getValue().getProcessesMetadata().stream()
        .filter(p -> p.getBpmnProcessId().equals(bpmnProcessId))
        .findAny()
        .orElseThrow()
        .getProcessDefinitionKey();
  }
}
