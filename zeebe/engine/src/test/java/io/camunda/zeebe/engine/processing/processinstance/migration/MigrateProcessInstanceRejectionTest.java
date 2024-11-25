/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
<<<<<<< HEAD
import java.time.Duration;
=======
import java.util.Map;
>>>>>>> 8368c937 (feat: backport of #25298 to main)
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
                    .timerWithDuration(Duration.ofDays(1))
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

    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withHandlerNodeId("boundary")
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
                but active element with id 'A' has one or more boundary events of types 'TIMER'. \
                Migrating active elements with boundary events of these types is not possible yet."""
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
                    .timerWithDuration(Duration.ofDays(1))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent("end")
                    .done())
            .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

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
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();
    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
                """
            Expected to migrate process instance '%s' \
            but target element with id 'A' has one or more boundary events of types 'TIMER'. \
            Migrating target elements with boundary events of these types is not possible yet."""
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
  public void
      shouldRejectCommandWhenActiveElementSubscribesToTheSameMessageBoundaryEventWithoutMapping() {
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
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();
    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
                """
            Expected to migrate process instance '%s' but active element with id 'A' attempts to \
            subscribe to a message it is already subscribed to with name 'message'. Migrating \
            active elements that subscribe to a message they are already subscribed to is not \
            possible yet. Please provide a mapping instruction to message catch event with id \
            'boundary' to migrate the respective message subscription."""
                .formatted(processInstanceKey))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectCommandWhenMessageBoundaryEventsAreMapped() {
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
        .addMappingInstruction("boundary", "boundary")
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
            Expected to migrate process instance '%s' but active element with id 'A' is mapped to \
            element with id 'A' that must be subscribed to mapped catch event with id 'boundary'. \
            Migrating active elements with mapped catch events is not possible yet."""
                .formatted(processInstanceKey))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectCommandWhenNativeUserTaskIsMappedToUserTask() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .userTask("A")
                    .zeebeUserTask()
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("process2")
                    .startEvent()
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
        .addMappingInstruction("A", "B")
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
              but active user task with id 'A' and implementation 'zeebe user task' is mapped to \
              an user task with id 'B' and different implementation 'job worker'. \
              Elements must be mapped to elements of the same implementation.""",
                processInstanceKey))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectCommandWhenUserTaskIsMappedToNativeUserTask() {
    // given
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .userTask("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess("process2")
                    .startEvent()
                    .userTask("B")
                    .zeebeUserTask()
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
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            String.format(
                """
              Expected to migrate process instance '%s' \
              but active user task with id 'A' and implementation 'job worker' is mapped to \
              an user task with id 'B' and different implementation 'zeebe user task'. \
              Elements must be mapped to elements of the same implementation.""",
                processInstanceKey))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectMigrationWhenSubprocessIsUnmapped() {
    // given
    final String processId = "process";
    final String targetProcessId = "process2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .subProcess(
                        "sub1",
                        s ->
                            s.embeddedSubProcess()
                                .startEvent()
                                .serviceTask("A", t -> t.zeebeJobType("task"))
                                .endEvent())
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .subProcess(
                        "sub2",
                        s ->
                            s.embeddedSubProcess()
                                .startEvent()
                                .serviceTask("B", t -> t.zeebeJobType("task"))
                                .endEvent())
                    .endEvent()
                    .moveToActivity("sub2")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractTargetProcessDefinitionKey(deployment, targetProcessId);
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        // subprocess is not mapped but only the service task
        .addMappingInstruction("A", "B")
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
                but no mapping instruction defined for active element with id 'sub1'. \
                Elements cannot be migrated without a mapping.""",
                processInstanceKey))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectWhenUnableToSubscribeToMessageBoundaryEvent() {
    // given
    final String processId = "process";
    final String targetProcessId = "process2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractTargetProcessDefinitionKey(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("A", "B")
            .expectRejection()
            .migrate();

    // then
    Assertions.assertThat(rejection)
        .describedAs("Expect that the message boundary event could not be subscribed")
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
                """
            Expected to migrate process instance '%s' but active element with id 'A' \
            is mapped to element with id 'B' that must be subscribed to a message catch event. \
            Failed to extract the correlation key for 'key': The value must be either a string or \
            a number, but was 'NULL'. The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'key'"""
                .formatted(processInstanceKey));
  }

<<<<<<< HEAD
=======
  @Test
  public void shouldRejectWhenUnableToSubscribeToTimerBoundaryEvent() {
    // given
    final String processId = "process";
    final String targetProcessId = "process2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary")
                    .timerWithDurationExpression("invalid_timer_expression")
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractTargetProcessDefinitionKey(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("A", "B")
            .expectRejection()
            .migrate();

    // then
    Assertions.assertThat(rejection)
        .describedAs("Expect that the message boundary event could not be subscribed")
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            """
            Expected to migrate process instance '%s' but active element with id 'A' \
            is mapped to element with id 'B' that must be subscribed to a catch event. \
            Expected result of the expression 'invalid_timer_expression' to be one of \
            '[DURATION, PERIOD, STRING]', but was 'NULL'. \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'invalid_timer_expression'"""
                .formatted(processInstanceKey));
  }

  @Test
  public void shouldRejectWhenUnableToSubscribeToSignalBoundaryEvent() {
    // given
    final String processId = "process";
    final String targetProcessId = "process2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary")
                    .signal(signal -> signal.nameExpression("invalid_signal_expression"))
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractTargetProcessDefinitionKey(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("A", "B")
            .expectRejection()
            .migrate();

    // then
    Assertions.assertThat(rejection)
        .describedAs("Expect that the message boundary event could not be subscribed")
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            """
            Expected to migrate process instance '%s' but active element with id 'A' \
            is mapped to element with id 'B' that must be subscribed to a catch event. \
            Expected result of the expression 'invalid_signal_expression' to be \
            'STRING', but was 'NULL'. \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'invalid_signal_expression'"""
                .formatted(processInstanceKey));
  }

  @Test
  public void shouldRejectMigrationThatChangesParallelMultiInstanceToSequential() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        "serviceTask1",
                        t ->
                            t.zeebeJobType("A")
                                .multiInstance(
                                    b ->
                                        b.parallel()
                                            .zeebeInputCollectionExpression("[1,2,3]")
                                            .zeebeInputElement("index")))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask(
                        "serviceTask2",
                        t ->
                            t.zeebeJobType("B")
                                .multiInstance(
                                    b ->
                                        b.sequential()
                                            .zeebeInputCollectionExpression("[1,2,3]")
                                            .zeebeInputElement("index")))
                    .endEvent()
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withType("A")
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .describedAs("Wait until all service tasks have activated")
        .hasSize(3);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("serviceTask1", "serviceTask2")
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
                but active element with id 'serviceTask1' has a different loop characteristics \
                than the target element with id 'serviceTask2'. \
                Both elements must have either sequential or parallel loop characteristics.""",
                processInstanceKey))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectMigrationThatChangesSequentialMultiInstanceToParallel() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        "serviceTask1",
                        t ->
                            t.zeebeJobType("A")
                                .multiInstance(
                                    b ->
                                        b.sequential()
                                            .zeebeInputCollectionExpression("[1,2,3]")
                                            .zeebeInputElement("index")))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask(
                        "serviceTask2",
                        t ->
                            t.zeebeJobType("B")
                                .multiInstance(
                                    b ->
                                        b.parallel()
                                            .zeebeInputCollectionExpression("[1,2,3]")
                                            .zeebeInputElement("index")))
                    .endEvent()
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withType("A")
                .withProcessInstanceKey(processInstanceKey)
                .limit(1))
        .describedAs("Wait until the first service tasks has activated")
        .hasSize(1);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("serviceTask1", "serviceTask2")
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
                but active element with id 'serviceTask1' has a different loop characteristics \
                than the target element with id 'serviceTask2'. \
                Both elements must have either sequential or parallel loop characteristics.""",
                processInstanceKey))
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
        extractProcessDefinitionKeyByProcessId(deployment, "process2");

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
        extractProcessDefinitionKeyByProcessId(deployment, "process2");

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

>>>>>>> 8368c937 (feat: backport of #25298 to main)
  private static long extractTargetProcessDefinitionKey(
      final Record<DeploymentRecordValue> deployment, final String bpmnProcessId) {
    return deployment.getValue().getProcessesMetadata().stream()
        .filter(p -> p.getBpmnProcessId().equals(bpmnProcessId))
        .findAny()
        .orElseThrow()
        .getProcessDefinitionKey();
  }
}
