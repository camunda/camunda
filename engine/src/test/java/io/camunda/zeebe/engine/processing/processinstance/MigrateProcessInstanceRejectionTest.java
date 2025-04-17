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
import static org.assertj.core.api.Assertions.fail;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
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
                Elements cannot be migrated with an incident yet. \
                Please retry migration after resolving the incident.""",
                processInstanceKey))
        .hasKey(processInstanceKey);

    // after resolving the incident, the migration should succeed
    ENGINE.job().ofInstance(processInstanceKey).withType("jobType").withRetries(1).updateRetries();
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
                Elements cannot be migrated with an incident yet. \
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

  @Test
  public void shouldRejectCommandWhenAnyElementsMappedToADifferentBpmnElementType() {
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
                    .userTask("A")
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
              Expected to migrate process instance '%s' \
              but active element with id 'A' and type 'SERVICE_TASK' is mapped to \
              an element with id 'A' and different type 'USER_TASK'. \
              Elements must be mapped to elements of the same type.""",
                processInstanceKey))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectCommandWhenMappingInstructionContainsANonExistingSourceElementId() {
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

    final long targetProcessDefinitionKey =
        extractTargetProcessDefinitionKey(deployment, "process2");

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("B", "A")
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            String.format(
                """
              Expected to migrate process instance '%s' \
              but mapping instructions contain a non-existing source element id 'B'. \
              Elements provided in mapping instructions must exist \
              in the source process definition.""",
                processInstanceKey))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectCommandWhenMappingInstructionContainsANonExistingTargetElementId() {
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

    final long targetProcessDefinitionKey =
        extractTargetProcessDefinitionKey(deployment, "process2");

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            String.format(
                """
              Expected to migrate process instance '%s' \
              but mapping instructions contain a non-existing target element id 'B'. \
              Elements provided in mapping instructions must exist \
              in the target process definition.""",
                processInstanceKey))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectCommandWhenElementFlowScopeIsChangedInTargetProcessDefinition() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process")
                    .startEvent("start")
                    .serviceTask("A", t -> t.zeebeJobType("task"))
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("process2")
                    .startEvent("start")
                    .subProcess(
                        "sub",
                        s ->
                            s.embeddedSubProcess()
                                .startEvent()
                                .serviceTask("A", t -> t.zeebeJobType("task"))
                                .endEvent())
                    .endEvent("end")
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
              Expected to migrate process instance '%s' \
              but the flow scope of active element with id 'A' is changed. \
              The flow scope of the active element is expected to be 'process2' but was 'sub'. \
              The flow scope of an element cannot be changed during migration yet.""",
                processInstanceKey))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectCommandWhenElementFlowScopeIsChangedInTargetProcessDefinitionDeeper() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process")
                    .startEvent("start")
                    .serviceTask("A", t -> t.zeebeJobType("task"))
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("process2")
                    .startEvent("start")
                    .subProcess(
                        "sub1",
                        s ->
                            s.embeddedSubProcess()
                                .startEvent()
                                .subProcess(
                                    "sub2",
                                    s2 ->
                                        s2.embeddedSubProcess()
                                            .startEvent()
                                            .serviceTask("A", t -> t.zeebeJobType("task"))
                                            .endEvent())
                                .endEvent())
                    .endEvent("end")
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
              Expected to migrate process instance '%s' \
              but the flow scope of active element with id 'A' is changed. \
              The flow scope of the active element is expected to be 'process2' but was 'sub2'. \
              The flow scope of an element cannot be changed during migration yet.""",
                processInstanceKey))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectCommandWhenTheMigratedProcessInstanceIsAChildProcessInstance() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .callActivity("call", c -> c.zeebeProcessId("childProcess"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("childProcess")
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("process2")
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    RecordingExporter.processInstanceRecords()
        .withParentProcessInstanceKey(processInstanceKey)
        .withBpmnProcessId("childProcess")
        .await();

    final long childProcessInstanceKey =
        RecordingExporter.processInstanceRecords()
            .withParentProcessInstanceKey(processInstanceKey)
            .withBpmnProcessId("childProcess")
            .getFirst()
            .getKey();

    final long targetProcessDefinitionKey =
        extractTargetProcessDefinitionKey(deployment, "process2");

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(childProcessInstanceKey)
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
              Expected to migrate process instance '%s' \
              but process instance is a child process instance. \
              Child process instances cannot be migrated.""",
                childProcessInstanceKey))
        .hasKey(childProcessInstanceKey);
  }

  @Test
  public void shouldRejectCommandWhenSourceElementIdIsMappedInMultipleMappingInstructions() {
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
                    .serviceTask("B", t -> t.zeebeJobType("task"))
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
        .addMappingInstruction("A", "A")
        .addMappingInstruction("A", "B")
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            String.format(
                "Expected to migrate process instance '%s' but the mapping instructions contain duplicate source element ids '%s'.",
                processInstanceKey, "[A]"))
        .hasKey(processInstanceKey);
  }

  @Test
  public void
      shouldRejectCommandWhenTheMigratedProcessInstanceContainsATaskSubscribedToABoundaryEvent() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process")
                    .startEvent("start")
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .boundaryEvent("boundary")
                    .message(
                        m -> m.name("message").zeebeCorrelationKeyExpression("\"correlationKey\""))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("process2")
                    .startEvent("start")
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .endEvent("end")
                    .done())
            .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName("message")
        .withCorrelationKey("correlationKey")
        .await();

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
            """
              Expected to migrate process instance '%s' \
              but active element with id 'A' has a boundary event. \
              Migrating active elements with boundary events is not possible yet."""
                .formatted(processInstanceKey))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectCommandWhenTheMigratedProcessInstanceSubscribedToAnEventSubprocess() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process")
                    .eventSubProcess(
                        "eventSubProcess",
                        sub ->
                            sub.startEvent(
                                    "eventSubProcessStart",
                                    s ->
                                        s.message(
                                            m ->
                                                m.name("message")
                                                    .zeebeCorrelationKeyExpression(
                                                        "\"correlationKey\"")))
                                .endEvent())
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("process2")
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName("message")
        .withCorrelationKey("correlationKey")
        .await();

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
            "Expected to migrate process instance but process instance has an event subprocess. "
                + "Process instances with event subprocesses cannot be migrated yet.")
        .hasKey(processInstanceKey);
  }

  @Test
  public void
      shouldRejectCommandWhenTheTargetProcessDefinitionContainsATaskSubscribedToABoundaryEvent() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("process2")
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .boundaryEvent("boundary")
                    .message(
                        m -> m.name("message").zeebeCorrelationKeyExpression("\"correlationKey\""))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent("end")
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
            """
              Expected to migrate process instance '%s' \
              but target element with id 'A' has a boundary event. \
              Migrating target elements with boundary events is not possible yet."""
                .formatted(processInstanceKey))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectCommandWhenTheTargetProcessDefinitionSubscribedToAnEventSubprocess() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("process2")
                    .eventSubProcess(
                        "eventSubProcess",
                        sub ->
                            sub.startEvent(
                                    "eventSubProcessStart",
                                    s ->
                                        s.message(
                                            m ->
                                                m.name("message")
                                                    .zeebeCorrelationKeyExpression(
                                                        "\"correlationKey\"")))
                                .endEvent())
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
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
            "Expected to migrate process instance but target process has an event subprocess. "
                + "Target processes with event subprocesses cannot be migrated yet.")
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldNotStoreSubscriptionDeletionInTransientStateWhenRejectingCommand() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .boundaryEvent("boundary")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("process2")
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .boundaryEvent("boundary")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent("end")
                    .done())
            .deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process").withVariable("key", "key").create();

    final long targetProcessDefinitionKey =
        extractTargetProcessDefinitionKey(deployment, "process2");

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

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
    ENGINE
        .getProcessingState()
        .getPendingProcessMessageSubscriptionState()
        .visitPending(
            System.currentTimeMillis(),
            s -> {
              fail("Encountered a pending process message subscription.");
              return true;
            });
  }

  @Test
  public void shouldNotStoreSubscriptionCreationInTransientStateWhenRejectingCommand() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .boundaryEvent("boundary1")
                    .message(m -> m.name("message1").zeebeCorrelationKeyExpression("key1"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("process2")
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .boundaryEvent("boundary2")
                    .message(m -> m.name("message2").zeebeCorrelationKeyExpression("key2"))
                    .endEvent()
                    .moveToActivity("A")
                    .boundaryEvent("boundary1")
                    .message(m -> m.name("message1").zeebeCorrelationKeyExpression("key1"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent("end")
                    .done())
            .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("process")
            .withVariables(Map.of("key1", "key1", "key2", "key2"))
            .create();

    final long targetProcessDefinitionKey =
        extractTargetProcessDefinitionKey(deployment, "process2");

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

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
    ENGINE
        .getProcessingState()
        .getPendingProcessMessageSubscriptionState()
        .visitPending(
            System.currentTimeMillis(),
            s -> {
              fail("Encountered a pending process message subscription.");
              return true;
            });
  }

  @Test
  public void shouldRejectMigrationIfItBreaksMessageCardinality() {
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process1")
                    .startEvent("msg_start")
                    .message("msg")
                    .serviceTask("task1", t -> t.zeebeJobType("task"))
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("process2")
                    .startEvent("msg_start")
                    .message("msg")
                    .serviceTask("task2", t -> t.zeebeJobType("task"))
                    .done())
            .deploy();
    ENGINE.message().withName("msg").withCorrelationKey("cardinality").publish();
    final var processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.START_EVENT)
            .withElementId("msg_start")
            .withBpmnProcessId("process1")
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementType(BpmnElementType.START_EVENT)
        .withElementId("msg_start")
        .withBpmnProcessId("process2")
        .getFirst()
        .getValue()
        .getProcessInstanceKey();

    final long targetProcessDefinitionKey =
        extractTargetProcessDefinitionKey(deployment, "process2");

    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("task1", "task2")
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .describedAs(
            "Should be rejected since an instance with id 'process2' and correlation key 'cardinality' already exist")
        .hasRejectionReason(
            String.format(
                """
                Expected to migrate process instance '%d' \
                but target process definition '%s' has an active instance triggered by a message start event with correlation key '%s'. \
                Only one instance per correlation key is allowed for message start events.""",
                processInstanceKey, targetProcessDefinitionKey, "cardinality"))
        .hasKey(processInstanceKey);
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
