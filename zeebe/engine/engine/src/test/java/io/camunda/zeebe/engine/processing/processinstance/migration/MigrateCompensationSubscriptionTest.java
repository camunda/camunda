/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.engine.processing.processinstance.migration.MigrationTestUtil.extractProcessDefinitionKeyByProcessId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.AbstractThrowEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.BoundaryEventBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.CompensationSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.CompensationSubscriptionRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateCompensationSubscriptionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteCompensationMigratedEvent() {
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
        .addMappingInstruction("boundary1", "boundary2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withIntent(CompensationSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the compensable activity is updated")
        .hasCompensableActivityId("C")
        .describedAs("Expect that the compensation handler id is unchanged")
        .hasCompensationHandlerId("undoC");

    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    Assertions.assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withIntent(CompensationSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the compensation subscription can be triggered after migration")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasCompensableActivityId("C")
        .hasCompensationHandlerId("undoC");
  }

  @Test
  public void shouldWriteCompensationMigratedEventForCompensationInsideSubprocess() {
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
        .migrate();

    // then
    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withIntent(CompensationSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .describedAs("Expect that both compensation subscriptions are migrated")
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(
            CompensationSubscriptionRecordValue::getProcessDefinitionKey,
            CompensationSubscriptionRecordValue::getCompensableActivityId,
            CompensationSubscriptionRecordValue::getCompensationHandlerId)
        .containsExactlyInAnyOrder(
            tuple(targetProcessDefinitionKey, "C", "undoC"),
            tuple(targetProcessDefinitionKey, "subProcess2", ""));

    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withIntent(CompensationSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .describedAs("Expect that both compensation subscriptions are triggered")
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(
            CompensationSubscriptionRecordValue::getProcessDefinitionKey,
            CompensationSubscriptionRecordValue::getCompensableActivityId,
            CompensationSubscriptionRecordValue::getCompensationHandlerId)
        .containsExactlyInAnyOrder(
            tuple(targetProcessDefinitionKey, "C", "undoC"),
            tuple(targetProcessDefinitionKey, "subProcess2", ""));
  }

  @Test
  public void shouldWriteCompensationMigratedEventAndContinueInDifferentCompensationHandler() {
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
                                c ->
                                    c.subProcess("undoC")
                                        .embeddedSubProcess()
                                        .startEvent()
                                        .serviceTask("F", t -> t.zeebeJobType("F"))
                                        .endEvent()))
                    .moveToActivity("C")
                    .serviceTask("D", t -> t.zeebeJobType("D"))
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
        .addMappingInstruction("boundary1", "boundary2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withIntent(CompensationSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the compensable activity is updated")
        .hasCompensableActivityId("C")
        .describedAs("Expect that the compensation handler id is unchanged")
        .hasCompensationHandlerId("undoC");

    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    Assertions.assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withIntent(CompensationSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the compensation subscription can be triggered after migration")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasCompensableActivityId("C")
        .hasCompensationHandlerId("undoC");

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .withElementId("F")
                .getFirst())
        .describedAs("Expect that the updated compensation handler is activated")
        .isNotNull();
  }

  @Test
  public void shouldMigrateMultiInstanceCompensationHandler() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final Consumer<BoundaryEventBuilder> sourceCompensationHandler =
        compensation ->
            compensation
                .serviceTask("undoA")
                .zeebeJobType("undoA")
                .multiInstance()
                .zeebeInputCollectionExpression("[1,2,3]");

    final Consumer<BoundaryEventBuilder> targetCompensationHandler =
        compensation ->
            compensation
                .serviceTask("undoC")
                .zeebeJobType("undoC")
                .multiInstance()
                .zeebeInputCollectionExpression("[1,2,3]");

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        "A",
                        t ->
                            t.zeebeJobType("A")
                                .multiInstance(
                                    b ->
                                        b.zeebeInputCollectionExpression("[1,2,3]")
                                            .zeebeInputElement("index"))
                                .boundaryEvent("boundary1")
                                .compensation(sourceCompensationHandler))
                    .serviceTask("B", t -> t.zeebeJobType("B"))
                    .intermediateThrowEvent(
                        "boundary_throw", ic -> ic.compensateEventDefinition().activityRef("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask(
                        "C",
                        t ->
                            t.zeebeJobType("C")
                                .multiInstance(
                                    b ->
                                        b.zeebeInputCollectionExpression("[1,2,3]")
                                            .zeebeInputElement("index"))
                                .boundaryEvent("boundary2")
                                .compensation(targetCompensationHandler))
                    .serviceTask("D", t -> t.zeebeJobType("D"))
                    .intermediateThrowEvent(
                        "boundary_throw", ic -> ic.compensateEventDefinition().activityRef("C"))
                    .endEvent("multi_instance_target_process_end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final var jobs =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withType("A")
            .withProcessInstanceKey(processInstanceKey)
            .limit(3)
            .toList();

    jobs.forEach(job -> ENGINE.job().withKey(job.getKey()).complete());

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withElementId("B")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("B", "D")
        .addMappingInstruction("boundary1", "boundary2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withIntent(CompensationSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the compensable activity is updated")
        .hasCompensableActivityId("C")
        .describedAs("Expect that the compensation handler id is unchanged")
        .hasCompensationHandlerId("undoC");

    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    Assertions.assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withIntent(CompensationSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the compensation subscription can be triggered after migration")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasCompensableActivityId("C")
        .hasCompensationHandlerId("undoC");
  }

  @Test
  public void shouldUnsubscribeFromCompensationEvent() {
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
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);
    final long sourceProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId);

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
        .addMappingInstruction("B", "C")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withIntent(CompensationSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(sourceProcessDefinitionKey)
        .describedAs("Expect that the compensable activity is updated")
        .hasCompensableActivityId("A")
        .describedAs("Expect that the compensation handler id is unchanged")
        .hasCompensationHandlerId("undoA")
        .describedAs("Expect that the compensation boundary event in the source is deleted")
        .isNotNull();
  }

  @Test
  public void shouldSubscribeToCompensationEventAfterMigration() {
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
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("B", t -> t.zeebeJobType("B"))
                    .boundaryEvent(
                        "boundary1",
                        b ->
                            b.compensation(
                                c -> c.serviceTask("undoB", t -> t.zeebeJobType("undoB"))))
                    .moveToActivity("B")
                    .intermediateThrowEvent(
                        "boundary_throw", ic -> ic.compensateEventDefinition().activityRef("B"))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    Assertions.assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withIntent(CompensationSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is the target process definition")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the compensable activity is the one in the target")
        .hasCompensableActivityId("B")
        .describedAs("Expect that the compensation handler id is the one in the target")
        .hasCompensationHandlerId("undoB");

    ENGINE.job().ofInstance(processInstanceKey).withType("undoB").complete();

    Assertions.assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withIntent(CompensationSubscriptionIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the compensation boundary event in the target is completed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasCompensableActivityId("B")
        .hasCompensationHandlerId("undoB");
  }

  @Test
  public void shouldNotSubscribeToUnMappedCompensationEvent() {
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
                    .intermediateThrowEvent(
                        "boundary_throw", ic -> ic.compensateEventDefinition().activityRef("C"))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);
    final long sourceProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId);

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
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.compensationSubscriptionRecords()
                .withIntent(CompensationSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(sourceProcessDefinitionKey)
        .describedAs("Expect that the compensable activity is updated")
        .hasCompensableActivityId("A")
        .describedAs("Expect that the compensation handler id is unchanged")
        .hasCompensationHandlerId("undoA")
        .describedAs("Expect that the compensation boundary event in the source is deleted")
        .isNotNull();

    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary_throw")
                .exists())
        .describedAs("Expect that the compensation throw event is completed")
        .isTrue();

    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .withIntent(CompensationSubscriptionIntent.CREATED)
                .skip(1) // skip the first one, which is created for the source process
                .exists())
        .describedAs("Expect that the compensation boundary event in the target is not created")
        .isFalse();
  }
}
