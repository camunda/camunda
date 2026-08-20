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
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateSignalEventSubprocessTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteMigratedEventForActiveSignalEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String sourceSignalName = helper.getSignalName();
    final String targetSignalName = helper.getSignalName() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .signal(sourceSignalName)
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
                                .signal(targetSignalName)
                                .serviceTask("B", t -> t.zeebeJobType("task2"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    ENGINE.signal().withSignalName(sourceSignalName).broadcast();

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
                .signalSubscriptionRecords()
                .withIntent(SignalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withBpmnProcessId(targetProcessId)
                .withCatchEventId("start2")
                .skip(1)
                .exists())
        .describedAs(
            "Expect that no signal subscription is created after migration because "
                + "there are no element instances subscribed to it")
        .isFalse();
  }

  @Test
  public void shouldWriteMigratedEventForMultipleActiveSignalEventSubprocesses() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String sourceSignalName = helper.getSignalName();
    final String targetSignalName = helper.getSignalName() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .signal(sourceSignalName)
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
                                .signal(targetSignalName)
                                .interrupting(false)
                                .serviceTask("B", t -> t.zeebeJobType("task2"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    ENGINE.signal().withSignalName(sourceSignalName).broadcast();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    ENGINE.signal().withSignalName(sourceSignalName).broadcast();
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
  public void shouldMigrateSignalForMappedStartEvent() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String sourceSignalName = helper.getSignalName();
    final String targetSignalName = helper.getSignalName() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .signal(sourceSignalName)
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
                                .signal(targetSignalName)
                                .userTask("eventSubprocessUserTask")
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName(sourceSignalName)
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
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.MIGRATED)
                .withSignalName(sourceSignalName)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the catch event id is updated")
        .hasCatchEventId("start2");

    ENGINE.signal().withSignalName(sourceSignalName).broadcast();

    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the catch event id is updated")
        .hasCatchEventId("start2")
        .describedAs("Expect that the signal name is not changed")
        .hasSignalName(sourceSignalName);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .withElementId("eventSubprocessUserTask")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process instance is continued in the target process")
        .isNotNull();
  }

  @Test
  public void shouldUnsubscribeFromSignalEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String sourceSignalName = helper.getSignalName();

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub",
                        s ->
                            s.startEvent("start1")
                                .signal(sourceSignalName)
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

    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName(sourceSignalName)
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
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .withBpmnProcessId(processId)
                .withCatchEventId("start1")
                .withSignalName(sourceSignalName)
                .getFirst()
                .getValue())
        .describedAs("Expect that the signal subscription is deleted")
        .isNotNull();
  }

  @Test
  public void shouldSubscribeToSignalEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String targetSignalName = helper.getSignalName();

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
                                .signal(targetSignalName)
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
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the catch event id is updated")
        .hasCatchEventId("start1")
        .describedAs("Expect that the signal name is not changed")
        .hasSignalName(targetSignalName);

    ENGINE.signal().withSignalName(targetSignalName).broadcast();

    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the catch event id is updated")
        .hasCatchEventId("start1")
        .describedAs("Expect that the signal name is not changed")
        .hasSignalName(targetSignalName);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .withElementId("eventSubprocessUserTask")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process instance is continued in the target process")
        .isNotNull();
  }

  @Test
  public void shouldResubscribeToSignalEventSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String sourceSignalName = helper.getSignalName();
    final String targetSignalName = helper.getSignalName() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "sub1",
                        s ->
                            s.startEvent("start1")
                                .signal(sourceSignalName)
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
                                .signal(targetSignalName)
                                .userTask("eventSubprocessUserTask")
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName(sourceSignalName)
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
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .withBpmnProcessId(processId)
                .withCatchEventId("start1")
                .withSignalName(sourceSignalName)
                .getFirst()
                .getValue())
        .describedAs("Expect that the signal subscription is deleted")
        .isNotNull();

    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withBpmnProcessId(targetProcessId)
                .withCatchEventId("start2")
                .withSignalName(targetSignalName)
                .getFirst()
                .getValue())
        .describedAs("Expect that the signal subscription is created in the target process")
        .isNotNull();

    ENGINE.signal().withSignalName(targetSignalName).broadcast();

    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withBpmnProcessId(targetProcessId)
                .withCatchEventId("start2")
                .withSignalName(targetSignalName)
                .getFirst()
                .getValue())
        .describedAs("Expect that the signal subscription is deleted after broadcast")
        .isNotNull();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .withElementId("eventSubprocessUserTask")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process instance is continued in the target process")
        .isNotNull();
  }
}
