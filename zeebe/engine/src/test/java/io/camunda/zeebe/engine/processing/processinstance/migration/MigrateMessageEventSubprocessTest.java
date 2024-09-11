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
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateMessageEventSubprocessTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteMigratedEventForActiveMessageEventSubprocess() {
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
                            s.startEvent()
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
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
                            s.startEvent()
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
                                .serviceTask("B", t -> t.zeebeJobType("task2"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key", helper.getCorrelationValue())
            .create();

    ENGINE.message().withName("msg").withCorrelationKey(helper.getCorrelationValue()).publish();

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

    assertThat(
            RecordingExporter.records()
                .limit(
                    r ->
                        r.getKey() == processInstanceKey
                            && r.getIntent() == ProcessInstanceMigrationIntent.MIGRATED)
                .messageSubscriptionRecords()
                .withIntent(MessageSubscriptionIntent.CREATED)
                .withMessageName("msg")
                .withCorrelationKey(helper.getCorrelationValue())
                .skip(1)
                .exists())
        .describedAs(
            "Expect that no message subscription is created after migration because "
                + "there are no element instances subscribed to it")
        .isFalse();
  }

  @Test
  public void shouldWriteMigratedEventForMultipleActiveMessageEventSubprocesses() {
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
                                .message(m -> m.name("msg1").zeebeCorrelationKeyExpression("key"))
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
                                .message(m -> m.name("msg2").zeebeCorrelationKeyExpression("key"))
                                .interrupting(false)
                                .serviceTask("B", t -> t.zeebeJobType("task2"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key", helper.getCorrelationValue())
            .create();

    ENGINE.message().withName("msg1").withCorrelationKey(helper.getCorrelationValue()).publish();
    ENGINE.message().withName("msg1").withCorrelationKey(helper.getCorrelationValue()).publish();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("A")
                .limit(2))
        .describedAs("Expect that the non-interrupting event subprocess is activated twice")
        .hasSize(2);

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
    assertThat(
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
  public void shouldMigrateSubscriptionForMappedStartEvent() {
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
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key1"))
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
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key2"))
                                .userTask("eventSubprocessUserTask")
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key1", helper.getCorrelationValue())
            .create();

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
        .addMappingInstruction("start1", "start2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("start2")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey(helper.getCorrelationValue());

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey(helper.getCorrelationValue());

    ENGINE.message().withName("msg").withCorrelationKey(helper.getCorrelationValue()).publish();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("eventSubprocessUserTask")
                .getFirst())
        .describedAs(
            "Expect that the element inside the event subprocess in the target process is activated")
        .isNotNull();
  }

  @Test
  public void shouldUnsubscribeFromMessageEventSubprocess() {
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
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key1"))
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
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key1", helper.getCorrelationValue())
            .create();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("userTask1")
        .await();

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
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(processId)
        .hasElementId("start1")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey(helper.getCorrelationValue());

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(processId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey(helper.getCorrelationValue());
  }

  @Test
  public void shouldSubscribeToMessageEventSubprocess() {
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
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key1"))
                                .userTask("eventSubprocessUserTask")
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key1", helper.getCorrelationValue())
            .create();

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
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("start1")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey(helper.getCorrelationValue());

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey(helper.getCorrelationValue());

    ENGINE.message().withName("msg").withCorrelationKey(helper.getCorrelationValue()).publish();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("eventSubprocessUserTask")
                .getFirst())
        .describedAs(
            "Expect that the element inside the event subprocess in the target process is activated")
        .isNotNull();
  }

  @Test
  public void shouldResubscribeToMessageEventSubprocess() {
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
                                .message(m -> m.name("msg1").zeebeCorrelationKeyExpression("key1"))
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
                                .message(m -> m.name("msg2").zeebeCorrelationKeyExpression("key2"))
                                .userTask("eventSubprocessUserTask")
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(
                Map.of(
                    "key1",
                    helper.getCorrelationValue() + "1",
                    "key2",
                    helper.getCorrelationValue() + "2"))
            .create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName("msg1")
        .withCorrelationKey(helper.getCorrelationValue() + "1")
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
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(processId)
        .hasElementId("start1")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey(helper.getCorrelationValue() + "1");

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(processId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey(helper.getCorrelationValue() + "1");

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg2")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("start2")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey(helper.getCorrelationValue() + "2");

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg2")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey(helper.getCorrelationValue() + "2");

    ENGINE
        .message()
        .withName("msg2")
        .withCorrelationKey(helper.getCorrelationValue() + "2")
        .publish();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("eventSubprocessUserTask")
                .getFirst())
        .describedAs(
            "Expect that the element inside the event subprocess in the target process is activated")
        .isNotNull();
  }

  @Test
  public void shouldRejectCommandWhenMappedCatchEventIsAttachedToDifferentElement() {
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
                                .message(m -> m.name("msg1").zeebeCorrelationKeyExpression("key1"))
                                .endEvent())
                    .startEvent("start")
                    .subProcess(
                        "embedded1",
                        e ->
                            e.embeddedSubProcess()
                                .startEvent()
                                .userTask("userTask1")
                                .endEvent()
                                .subProcessDone())
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("start")
                    .subProcess(
                        "embedded2",
                        e ->
                            e.embeddedSubProcess()
                                .eventSubProcess(
                                    "sub2",
                                    s ->
                                        s.startEvent("start2")
                                            .message(
                                                m ->
                                                    m.name("msg2")
                                                        .zeebeCorrelationKeyExpression("key2"))
                                            .endEvent())
                                .startEvent()
                                .userTask("userTask2")
                                .endEvent()
                                .subProcessDone())
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(
                Map.of(
                    "key1",
                    helper.getCorrelationValue() + "1",
                    "key2",
                    helper.getCorrelationValue() + "2"))
            .create();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("userTask1")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("embedded1", "embedded2")
            .addMappingInstruction("userTask1", "userTask2")
            .addMappingInstruction("start1", "start2")
            .expectRejection()
            .migrate();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains("Expected to migrate process instance '" + processInstanceKey + "'")
        .contains(
            "active element with id '%s' is mapped to an element with id '%s'"
                .formatted(processId, targetProcessId))
        .contains(
            "and has a catch event with id 'start1' that is mapped to a catch event with id 'start2'")
        .contains("These mappings detach the catch event from the element in the target process")
        .contains("Catch events must stay attached to the same element instance");
  }

  @Test
  public void shouldRejectCommandWhenMappedCatchEventsAreMerged() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "subA1",
                        s ->
                            s.startEvent("startA1")
                                .message(m -> m.name("msgA1").zeebeCorrelationKeyExpression("key1"))
                                .endEvent())
                    .eventSubProcess(
                        "subB1",
                        s ->
                            s.startEvent("startB1")
                                .message(m -> m.name("msgB1").zeebeCorrelationKeyExpression("key1"))
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
                                .message(m -> m.name("msg2").zeebeCorrelationKeyExpression("key2"))
                                .endEvent())
                    .startEvent("start")
                    .userTask("userTask2")
                    .endEvent("end")
                    .done())
            .deploy();
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(
                Map.of(
                    "key1",
                    helper.getCorrelationValue() + "1",
                    "key2",
                    helper.getCorrelationValue() + "2"))
            .create();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("userTask1")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("userTask1", "userTask2")
            .addMappingInstruction("startA1", "start2")
            .addMappingInstruction("startB1", "start2")
            .expectRejection()
            .migrate();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains("Expected to migrate process instance '" + processInstanceKey + "'")
        .contains("active element with id '" + processId + "' has a catch event attached")
        .contains("catch event attached that is mapped to a catch event with id 'start2'")
        .contains(
            "There are multiple mapping instructions that target this catch event: 'startA1', 'startB1'")
        .contains("Catch events cannot be merged by process instance migration")
        .contains("Please ensure the mapping instructions target a catch event only once");
  }

  @Test
  public void shouldRejectCommandWhenMappedCatchEventChangesEventType() {
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
                                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
                                .endEvent())
                    .startEvent()
                    .userTask("userTask1")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .eventSubProcess(
                        "sub2", s -> s.startEvent("start2").timerWithDuration("PT1M").endEvent())
                    .startEvent()
                    .userTask("userTask2")
                    .endEvent()
                    .done())
            .deploy();
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("key", helper.getCorrelationValue() + "1"))
            .create();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("userTask1")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("userTask1", "userTask2")
            .addMappingInstruction("start1", "start2")
            .expectRejection()
            .migrate();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains("Expected to migrate process instance '" + processInstanceKey + "'")
        .contains("active element with id '" + processId + "' has a catch event")
        .contains(
            "has a catch event with id 'start1' that is mapped to a catch event with id 'start2'")
        .contains("These catch events have different event types: 'MESSAGE' and 'TIMER'")
        .contains("The event type of a catch event cannot be changed by process instance migration")
        .contains("Please ensure the event type of the catch event remains the same")
        .contains("or remove the mapping instruction for these catch events");
  }
}
