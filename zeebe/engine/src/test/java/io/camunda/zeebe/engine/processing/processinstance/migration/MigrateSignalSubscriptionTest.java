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
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.SignalSubscriptionRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.groups.Tuple;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateSignalSubscriptionTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteSignalMigratedEvent() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String sourceSignalName = helper.getSignalName();

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .signal(sourceSignalName)
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary2")
                    .signal("signal2")
                    .endEvent("target_process_signal_end")
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName(sourceSignalName)
        .await();

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
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.MIGRATED)
                .withSignalName(sourceSignalName)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the catch event id is updated")
        .hasCatchEventId("boundary2");

    engine.signal().withSignalName(sourceSignalName).broadcast();

    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the catch event id is updated")
        .hasCatchEventId("boundary2")
        .describedAs("Expect that the signal name is not changed")
        .hasSignalName(sourceSignalName);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_signal_end")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process instance is continued in the target process")
        .isNotNull();
  }

  @Test
  public void shouldMigrateMultipleSignalBoundaryEvents() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String sourceSignalName1 = helper.getSignalName();
    final String sourceSignalName2 = helper.getSignalName() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .signal(sourceSignalName1)
                    .endEvent()
                    .moveToActivity("A")
                    .boundaryEvent("boundary2")
                    .signal(sourceSignalName2)
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary3")
                    .signal("signal3")
                    .endEvent("target_process_signal_end1")
                    .moveToActivity("B")
                    .boundaryEvent("boundary4")
                    .signal("signal4")
                    .endEvent("target_process_signal_end2")
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName(sourceSignalName1)
        .await();

    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName(sourceSignalName2)
        .await();

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
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.MIGRATED)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            SignalSubscriptionRecordValue::getBpmnProcessId,
            SignalSubscriptionRecordValue::getCatchEventId,
            SignalSubscriptionRecordValue::getSignalName)
        .describedAs("Expect that the signal boundary events are migrated")
        .containsExactly(
            Tuple.tuple(targetProcessId, "boundary3", sourceSignalName1),
            Tuple.tuple(targetProcessId, "boundary4", sourceSignalName2));

    // only one signal event is broadcasted because signal events are interrupting
    // broadcasting signal for sourceSignalName2 will have no affect
    engine.signal().withSignalName(sourceSignalName1).broadcast();

    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the catch event id is updated")
        .hasCatchEventId("boundary3")
        .describedAs("Expect that the signal name is not changed")
        .hasSignalName(sourceSignalName1);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_signal_end1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process instance is continued in the target process")
        .isNotNull();

    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .skip(1)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the catch event id is updated")
        .hasCatchEventId("boundary4")
        .describedAs("Expect that the signal name is not changed")
        .hasSignalName(sourceSignalName2);
  }

  @Test
  public void shouldMigrateOneOfMultipleSignalBoundaryEventsAndDelete() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String sourceSignalName1 = helper.getSignalName();
    final String sourceSignalName2 = helper.getSignalName() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .signal(sourceSignalName1)
                    .endEvent()
                    .moveToActivity("A")
                    .boundaryEvent("boundary2")
                    .signal(sourceSignalName2)
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary3")
                    .signal("signal3")
                    .endEvent("target_process_signal_end")
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName(sourceSignalName1)
        .await();
    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName(sourceSignalName2)
        .await();

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
    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.MIGRATED)
                .withSignalName(sourceSignalName1)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the catch event id is updated")
        .hasCatchEventId("boundary3");

    // assert that the second signal event is deleted
    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .withBpmnProcessId(processId)
                .withCatchEventId("boundary2")
                .getFirst())
        .describedAs("Expect that the second signal boundary event is deleted")
        .isNotNull();

    engine.signal().withSignalName(sourceSignalName1).broadcast();

    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .skip(1)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the catch event id is updated")
        .hasCatchEventId("boundary3")
        .describedAs("Expect that the signal name is not changed")
        .hasSignalName(sourceSignalName1);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_signal_end")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process instance is continued in the target process")
        .isNotNull();
  }

  @Test
  public void shouldMigrateOneOfMultipleSignalBoundaryEventsAndCreate() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String sourceSignalName = helper.getSignalName();
    final String targetSignalName = helper.getSignalName() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .signal(sourceSignalName)
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary2")
                    .signal("signal2")
                    .endEvent()
                    .moveToActivity("B")
                    .boundaryEvent("boundary3")
                    .signal(targetSignalName)
                    .endEvent("target_process_signal_end")
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName(sourceSignalName)
        .await();

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
    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.MIGRATED)
                .withSignalName(sourceSignalName)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the catch event id is updated")
        .hasCatchEventId("boundary2");

    // assert that the second signal event is created
    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
                .skip(1)
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasCatchEventId("boundary3")
        .hasSignalName(targetSignalName);

    engine.signal().withSignalName(targetSignalName).broadcast();

    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .withSignalName(targetSignalName)
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasCatchEventId("boundary3");

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_signal_end")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process instance is continued in the target process")
        .isNotNull();
  }

  @Test
  public void shouldResubscribeToSignalEvent() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String sourceSignalName = helper.getSignalName();
    final String targetSignalName = helper.getSignalName() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .signal(sourceSignalName)
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary2")
                    .signal(targetSignalName)
                    .endEvent("target_process_signal_end")
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName(sourceSignalName)
        .withCatchEventId("boundary1")
        .await();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .withBpmnProcessId(processId)
                .withCatchEventId("boundary1")
                .withSignalName(sourceSignalName)
                .getFirst())
        .describedAs("Expect that the signal boundary event in the source is deleted")
        .isNotNull();

    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
                .withSignalName(targetSignalName)
                .withCatchEventId("boundary2")
                .getFirst()
                .getValue())
        .describedAs("Expect that the signal boundary event in the target is created")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId);

    engine.signal().withSignalName(targetSignalName).broadcast();

    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .withSignalName(targetSignalName)
                .withCatchEventId("boundary2")
                .getFirst()
                .getValue())
        .describedAs(
            "Expect that the signal boundary event in the target is deleted after signal broadcast")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_signal_end")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process instance is continued in the target process")
        .isNotNull();
  }

  @Test
  public void shouldResubscribeToSignalEventWithTheSameSignalName() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String signalName = helper.getSignalName();

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .signal(signalName)
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary2")
                    .signal(signalName)
                    .endEvent("target_process_signal_end")
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName(signalName)
        .withCatchEventId("boundary1")
        .await();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .withBpmnProcessId(processId)
                .withCatchEventId("boundary1")
                .withSignalName(signalName)
                .getFirst())
        .describedAs("Expect that the signal boundary event in the source is deleted")
        .isNotNull();

    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
                .withSignalName(signalName)
                .withCatchEventId("boundary2")
                .getFirst()
                .getValue())
        .describedAs("Expect that the signal boundary event in the target is created")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId);

    engine.signal().withSignalName(signalName).broadcast();

    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .withSignalName(signalName)
                .withCatchEventId("boundary2")
                .getFirst()
                .getValue())
        .describedAs(
            "Expect that the signal boundary event in the target is deleted after signal broadcast")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_signal_end")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process instance is continued in the target process")
        .isNotNull();
  }
}
