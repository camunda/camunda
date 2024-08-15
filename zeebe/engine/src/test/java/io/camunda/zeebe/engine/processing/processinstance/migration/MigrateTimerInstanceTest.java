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

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateTimerInstanceTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteTimerMigratedEvent() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .timerWithDuration("PT1M")
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary2")
                    .timerWithDuration("PT10M")
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.timerRecords(TimerIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the target element id is updated")
        .hasTargetElementId("boundary2")
        .describedAs("Expect that the due date is not changed")
        .hasDueDate(timerRecord.getDueDate());

    engine.increaseTime(Duration.ofMinutes(2));

    Assertions.assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the target element id is updated")
        .hasTargetElementId("boundary2")
        .describedAs("Expect that the due date is not changed")
        .hasDueDate(timerRecord.getDueDate());
  }

  @Test
  public void shouldMigrateMultipleTimerBoundaryEvents() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .timerWithDuration("PT1M")
                    .endEvent()
                    .moveToActivity("A")
                    .boundaryEvent("boundary2")
                    .timerWithDuration("PT2M")
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary3")
                    .timerWithDuration("PT10M")
                    .endEvent()
                    .moveToActivity("B")
                    .boundaryEvent("boundary4")
                    .timerWithDuration("PT15M")
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final List<Record<TimerRecordValue>> timerRecords =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .asList();

    final long firstDueDate = timerRecords.getFirst().getValue().getDueDate();
    final long secondDueDate = timerRecords.getLast().getValue().getDueDate();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary3")
        .addMappingInstruction("boundary2", "boundary4")
        .migrate();

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            TimerRecordValue::getProcessDefinitionKey,
            TimerRecordValue::getTargetElementId,
            TimerRecordValue::getDueDate)
        .describedAs("Expect that the timer boundary events are migrated")
        .containsExactly(
            Tuple.tuple(targetProcessDefinitionKey, "boundary4", firstDueDate),
            Tuple.tuple(targetProcessDefinitionKey, "boundary3", secondDueDate));

    engine.increaseTime(Duration.ofMinutes(3));

    Assertions.assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the target element id is updated")
        .hasTargetElementId("boundary3")
        .describedAs("Expect that the due date is not changed")
        .hasDueDate(secondDueDate);
  }

  @Test
  public void shouldMigrateOneOfMultipleTimerBoundaryEventsAndCancel() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .timerWithDuration("PT3M")
                    .endEvent()
                    .moveToActivity("A")
                    .boundaryEvent("boundary2")
                    .timerWithDuration("PT1M")
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary3")
                    .timerWithDuration("PT10M")
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final List<Record<TimerRecordValue>> timerRecords =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .asList();

    final long migratedDueDate = timerRecords.getLast().getValue().getDueDate();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary3")
        .migrate();

    // then
    // assert that the first boundary event is migrated
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .extracting(
            TimerRecordValue::getProcessDefinitionKey,
            TimerRecordValue::getTargetElementId,
            TimerRecordValue::getDueDate)
        .describedAs("Expect that the timer boundary events are migrated")
        .containsExactly(targetProcessDefinitionKey, "boundary3", migratedDueDate);

    // assert that the second timer event is canceled
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withHandlerNodeId("boundary2")
                .exists())
        .describedAs("Expect that the second timer boundary event is canceled")
        .isTrue();

    engine.increaseTime(Duration.ofMinutes(4));

    Assertions.assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the target element id is updated")
        .hasTargetElementId("boundary3")
        .describedAs("Expect that the due date is not changed")
        .hasDueDate(migratedDueDate);
  }

  @Test
  public void shouldMigrateOneOfMultipleTimerBoundaryEventsAndCreate() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .timerWithDuration("PT5M")
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary2")
                    .timerWithDuration("PT10M")
                    .endEvent()
                    .moveToActivity("B")
                    .boundaryEvent("boundary3")
                    .timerWithDuration("PT1M")
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final Record<TimerRecordValue> timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final long migratedDueDate = timerRecord.getValue().getDueDate();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary2")
        .migrate();

    // then
    // assert that the first boundary event is migrated
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .extracting(
            TimerRecordValue::getProcessDefinitionKey,
            TimerRecordValue::getTargetElementId,
            TimerRecordValue::getDueDate)
        .describedAs("Expect that the timer boundary events are migrated")
        .containsExactly(targetProcessDefinitionKey, "boundary2", migratedDueDate);

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withHandlerNodeId("boundary3")
                .exists())
        .describedAs("Expect that the second timer boundary event is created")
        .isTrue();

    final Record<TimerRecordValue> createdTimerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withHandlerNodeId("boundary3")
            .getFirst();

    final long createdTimerDueDate = createdTimerRecord.getValue().getDueDate();

    engine.increaseTime(Duration.ofMinutes(6));

    Assertions.assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasTargetElementId("boundary3")
        .hasDueDate(createdTimerDueDate);
  }

  @Test
  public void shouldMigrateToInterruptingStatus() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .cancelActivity(false)
                    .timerWithDuration("PT3M")
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary2")
                    .cancelActivity(true)
                    .timerWithDuration("PT5M")
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final Record<TimerRecordValue> timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final long migratedDueDate = timerRecord.getValue().getDueDate();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.timerRecords(TimerIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the target element id is updated")
        .hasTargetElementId("boundary2")
        .describedAs("Expect that the due date is not changed")
        .hasDueDate(migratedDueDate);

    engine.increaseTime(Duration.ofMinutes(3));

    Assertions.assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the target element id is updated")
        .hasTargetElementId("boundary2")
        .describedAs("Expect that the due date is not changed")
        .hasDueDate(migratedDueDate);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("B")
                .getFirst())
        .describedAs(
            "Expect that the element is terminated as we the boundary event is now interrupting")
        .isNotNull();
  }

  @Test
  public void shouldMigrateToNonInterruptingStatus() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .boundaryEvent("boundary1")
                    .cancelActivity(true)
                    .timerWithDuration("PT3M")
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("B", b -> b.zeebeJobType("B"))
                    .boundaryEvent("boundary2")
                    .cancelActivity(false)
                    .timerWithDuration("PT5M")
                    .endEvent()
                    .moveToActivity("B")
                    .userTask("C")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final Record<TimerRecordValue> timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final long migratedDueDate = timerRecord.getValue().getDueDate();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.timerRecords(TimerIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the target element id is updated")
        .hasTargetElementId("boundary2")
        .describedAs("Expect that the due date is not changed")
        .hasDueDate(migratedDueDate);

    engine.increaseTime(Duration.ofMinutes(3));

    Assertions.assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the target element id is updated")
        .hasTargetElementId("boundary2")
        .describedAs("Expect that the due date is not changed")
        .hasDueDate(migratedDueDate);

    engine.job().ofInstance(processInstanceKey).withType("A").complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("C")
                .getFirst()
                .getValue())
        .describedAs(
            "Expect that the element is activated as we the boundary event is now non-interrupting")
        .isNotNull();
  }
}
