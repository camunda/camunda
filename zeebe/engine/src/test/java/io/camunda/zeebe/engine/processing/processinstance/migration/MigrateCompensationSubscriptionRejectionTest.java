/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.engine.processing.processinstance.migration.MigrationTestUtil.extractProcessDefinitionKeyByProcessId;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.AbstractThrowEventBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.CompensationSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateCompensationSubscriptionRejectionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldRejectCompensationMigrationWhenBoundaryIsMappedToNonExistingBoundary() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .boundaryEvent(
                        "boundary1",
                        b ->
                            b.compensation(
                                c -> c.serviceTask("undoA", t -> t.zeebeJobType("undoA"))))
                    .moveToActivity("A")
                    .serviceTask("B", t -> t.zeebeJobType("B"))
                    .intermediateThrowEvent(
                        "boundary_throw", ic -> ic.compensateEventDefinition().activityRef("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("C", t -> t.zeebeJobType("C"))
                    .boundaryEvent(
                        "boundary2",
                        b ->
                            b.compensation(
                                c -> c.serviceTask("undoC", t -> t.zeebeJobType("undoC"))))
                    .moveToActivity("C")
                    .serviceTask("D", t -> t.zeebeJobType("D"))
                    .serviceTask("E", t -> t.zeebeJobType("E"))
                    .intermediateThrowEvent(
                        "boundary_throw", ic -> ic.compensateEventDefinition().activityRef("C"))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    RecordingExporter.compensationSubscriptionRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withIntent(CompensationSubscriptionIntent.CREATED)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("B", "D")
        .addMappingInstruction("boundary1", "boundary3")
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    Assertions.assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
              Expected to migrate process instance '%s' \
              but mapping instructions contain a non-existing target element id '%s'. \
              Elements provided in mapping instructions must exist \
              in the target process definition."""
                .formatted(processInstanceKey, "boundary3"))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectCompensationMigrationWhenBoundaryIsMappedToDifferentTypeBoundary() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .boundaryEvent(
                        "boundary1",
                        b ->
                            b.compensation(
                                c -> c.serviceTask("undoA", t -> t.zeebeJobType("undoA"))))
                    .moveToActivity("A")
                    .serviceTask("B", t -> t.zeebeJobType("B"))
                    .intermediateThrowEvent(
                        "boundary_throw", ic -> ic.compensateEventDefinition().activityRef("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("C", t -> t.zeebeJobType("C"))
                    .boundaryEvent("boundary2", b -> b.timerWithDuration("PT10M"))
                    .endEvent()
                    .moveToActivity("C")
                    .serviceTask("D", t -> t.zeebeJobType("D"))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    RecordingExporter.compensationSubscriptionRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withIntent(CompensationSubscriptionIntent.CREATED)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("B", "D")
        .addMappingInstruction("boundary1", "boundary2")
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    Assertions.assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
              Expected to migrate process instance with id '%s' \
              but compensation boundary event '%s' is mapped to a target boundary event '%s' \
              that has event type '%s' different than compensation boundary event type."""
                .formatted(processId, "boundary1", "boundary2", "TIMER"))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectCompensationMigrationWhenBoundaryEventFlowScopeIsDeeper() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .subProcess("subProcess1")
                    .embeddedSubProcess()
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .boundaryEvent(
                        "boundary1",
                        b ->
                            b.compensation(
                                c -> c.serviceTask("undoA", t -> t.zeebeJobType("undoA"))))
                    .moveToActivity("A")
                    .endEvent()
                    .subProcessDone()
                    .serviceTask("B", t -> t.zeebeJobType("B"))
                    .intermediateThrowEvent(
                        "boundary_throw", AbstractThrowEventBuilder::compensateEventDefinition)
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .subProcess(
                        "subProcess2",
                        s ->
                            s.embeddedSubProcess()
                                .startEvent()
                                .subProcess()
                                .embeddedSubProcess()
                                .startEvent()
                                .serviceTask("C", t -> t.zeebeJobType("C"))
                                .boundaryEvent(
                                    "boundary2",
                                    b ->
                                        b.compensation(
                                            c ->
                                                c.serviceTask(
                                                    "undoC", t -> t.zeebeJobType("undoC"))))
                                .moveToActivity("C")
                                .endEvent()
                                .subProcessDone()
                                .endEvent())
                    .serviceTask("D", t -> t.zeebeJobType("D"))
                    .intermediateThrowEvent(
                        "boundary_throw", AbstractThrowEventBuilder::compensateEventDefinition)
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    RecordingExporter.compensationSubscriptionRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withIntent(CompensationSubscriptionIntent.CREATED)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("B", "D")
        .addMappingInstruction("boundary1", "boundary2")
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    Assertions.assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            """
              Expected to migrate process instance with id '%s' \
              but the flow scope of compensation boundary event is changed. \
              Flow scope '%s' is not in the same level as '%s'. \
              The flow scope of a compensation boundary event cannot be changed during migration yet."""
                .formatted(processId, processId, "subProcess2"))
        .hasKey(processInstanceKey);
  }

  @Test
  public void shouldRejectCompensationMigrationWhenBoundaryEventFlowScopeIsShallower() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .subProcess(
                        "subProcess1",
                        s ->
                            s.embeddedSubProcess()
                                .startEvent()
                                .subProcess("subProcess1-1")
                                .embeddedSubProcess()
                                .startEvent()
                                .serviceTask("A", t -> t.zeebeJobType("A"))
                                .boundaryEvent(
                                    "boundary1",
                                    b ->
                                        b.compensation(
                                            c ->
                                                c.serviceTask(
                                                    "undoA", t -> t.zeebeJobType("undoA"))))
                                .moveToActivity("A")
                                .endEvent()
                                .subProcessDone()
                                .endEvent())
                    .serviceTask("B", t -> t.zeebeJobType("B"))
                    .intermediateThrowEvent(
                        "boundary_throw", AbstractThrowEventBuilder::compensateEventDefinition)
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .subProcess("subProcess2")
                    .embeddedSubProcess()
                    .startEvent()
                    .serviceTask("C", t -> t.zeebeJobType("C"))
                    .boundaryEvent(
                        "boundary2",
                        b ->
                            b.compensation(
                                c -> c.serviceTask("undoC", t -> t.zeebeJobType("undoC"))))
                    .moveToActivity("C")
                    .endEvent()
                    .subProcessDone()
                    .serviceTask("D", t -> t.zeebeJobType("D"))
                    .intermediateThrowEvent(
                        "boundary_throw", AbstractThrowEventBuilder::compensateEventDefinition)
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    RecordingExporter.compensationSubscriptionRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withIntent(CompensationSubscriptionIntent.CREATED)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("B", "D")
        .addMappingInstruction("boundary1", "boundary2")
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    Assertions.assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            """
              Expected to migrate process instance with id '%s' \
              but the flow scope of compensation boundary event is changed. \
              Flow scope '%s' is not in the same level as '%s'. \
              The flow scope of a compensation boundary event cannot be changed during migration yet."""
                .formatted(processId, "subProcess1", targetProcessId))
        .hasKey(processInstanceKey);
  }
}
