/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.engine.processing.processinstance.migration.MigrationTestUtil.extractProcessDefinitionKeyByProcessId;
import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateTimerEventSubprocessTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteMigratedEventForActiveTimerEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .timerWithDuration("PT5M")
                                .serviceTask("A", t -> t.zeebeJobType("task1"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask1")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .eventSubProcess(
                        "sub2",
                        s ->
                            s.startEvent("start2")
                                .timerWithDuration("PT10M")
                                .serviceTask("B", t -> t.zeebeJobType("task2"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    ENGINE.increaseTime(Duration.ofMinutes(6));

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("sub1", "sub2")
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key is changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("sub2")
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    Assertions.assertThat(
            RecordingExporter.records()
                .limit(
                    r ->
                        r.getKey() == processInstanceKey
                            && r.getIntent() == ProcessInstanceMigrationIntent.MIGRATED)
                .timerRecords()
                .withIntent(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withHandlerNodeId("start2")
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .skip(1)
                .exists())
        .describedAs(
            "Expect that no timer is created after migration because "
                + "there are no element instances subscribed to it")
        .isFalse();
  }

  @Test
  public void shouldWriteMigratedEventForMultipleActiveTimerEventSubprocesses() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .timerWithCycle("R/PT5M")
                                .interrupting(false)
                                .serviceTask("A", t -> t.zeebeJobType("task1"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask1")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .eventSubProcess(
                        "sub2",
                        s ->
                            s.startEvent("start2")
                                .timerWithCycle("R/PT10M")
                                .interrupting(false)
                                .serviceTask("B", t -> t.zeebeJobType("task2"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    ENGINE.increaseTime(Duration.ofMinutes(6));
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    ENGINE.increaseTime(Duration.ofMinutes(6));
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .skip(1)
        .await();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .addMappingInstruction("sub1", "sub2")
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
                .limit(2))
        .hasSize(2)
        .extracting(Record::getValue)
        .describedAs("Expect that process definition key is changed")
        .allMatch(v -> v.getProcessDefinitionKey() == targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .allMatch(v -> v.getBpmnProcessId().equals(targetProcessId))
        .allMatch(v -> v.getElementId().equals("sub2"))
        .describedAs("Expect that version number did not change")
        .allMatch(v -> v.getVersion() == 1);
  }

  @Test
  public void shouldMigrateTimerForMappedStartEvent() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .timerWithDuration("PT5M")
                                .serviceTask("A", t -> t.zeebeJobType("task1"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask1")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .eventSubProcess(
                        "sub2",
                        s ->
                            s.startEvent("start2")
                                .timerWithDuration("PT10M")
                                .userTask("eventSubprocessUserTask")
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .addMappingInstruction("start1", "start2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the target element id is updated")
        .hasTargetElementId("start2")
        .describedAs("Expect that the due date is not changed")
        .hasDueDate(timerRecord.getDueDate());

    ENGINE.increaseTime(Duration.ofMinutes(6));

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the target element id is updated")
        .hasTargetElementId("start2")
        .describedAs("Expect that the due date is not changed")
        .hasDueDate(timerRecord.getDueDate());
  }

  @Test
  public void shouldUnsubscribeFromTimerEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub",
                        s ->
                            s.startEvent("start1")
                                .timerWithDuration("PT5M")
                                .serviceTask("A", t -> t.zeebeJobType("task"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask1")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId(targetProcessId)
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withHandlerNodeId("start1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the timer boundary event is canceled")
        .hasDueDate(timerRecord.getDueDate());
  }

  @Test
  public void shouldSubscribeToTimerEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .userTask("userTask1")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .timerWithDuration("PT5M")
                                .userTask("eventSubprocessUserTask")
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("userTask1")
        .await();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId(targetProcessId)
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasTargetElementId("start1");

    ENGINE.increaseTime(Duration.ofMinutes(6));

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasTargetElementId("start1");
  }

  @Test
  public void shouldResubscribeToTimerEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .timerWithDuration("PT10M")
                                .serviceTask("A", t -> t.zeebeJobType("task1"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask1")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .eventSubProcess(
                        "sub2",
                        s ->
                            s.startEvent("start2")
                                .timerWithDuration("PT5M")
                                .userTask("eventSubprocessUserTask")
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("userTask1", "userTask2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId(targetProcessId)
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withHandlerNodeId("start1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the timer boundary event is canceled")
        .hasDueDate(timerRecord.getDueDate());

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withHandlerNodeId("start2")
                .getFirst()
                .getValue())
        .isNotNull();

    ENGINE.increaseTime(Duration.ofMinutes(6));

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withHandlerNodeId("start2")
                .getFirst()
                .getValue())
        .isNotNull();
  }
}
