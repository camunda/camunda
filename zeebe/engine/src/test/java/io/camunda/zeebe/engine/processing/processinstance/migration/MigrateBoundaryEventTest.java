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
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateBoundaryEventTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldUnsubscribeUnmappedMessageBoundaryEvent() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("key", "key").create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
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
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .withCorrelationKey("key")
                .exists())
        .describedAs("Expect that the message boundary event is unsubscribed")
        .isTrue();
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .withCorrelationKey("key")
                .exists())
        .describedAs("Expect that the message boundary event is unsubscribed")
        .isTrue();
  }

  @Test
  public void shouldSubscribeUnmappedMessageBoundaryEvent() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

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
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("key", "key").create();

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
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .withCorrelationKey("key")
                .exists())
        .describedAs("Expect that the message boundary event is subscribed")
        .isTrue();
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .withCorrelationKey("key")
                .exists())
        .describedAs("Expect that the message boundary event is subscribed")
        .isTrue();
  }

  @Test
  public void shouldUnsubscribeAndSubscribeUnmappedMessageBoundaryEvents() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    // note that the message name must differ in the target process for this test case. If both
    // message names are the same, a mapping instruction must be provided for these boundary events
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary")
                    .message(m -> m.name("message2").zeebeCorrelationKeyExpression("key"))
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("key", "key").create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
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
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == ProcessInstanceMigrationIntent.MIGRATE,
                    r -> r.getIntent() == ProcessMessageSubscriptionIntent.DELETED))
        .extracting(Record::getIntent)
        .describedAs("Expect that the message boundary event is unsubscribed after the migration")
        .contains(MessageSubscriptionIntent.DELETE, ProcessMessageSubscriptionIntent.DELETED);
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == ProcessInstanceMigrationIntent.MIGRATE,
                    r -> r.getIntent() == ProcessMessageSubscriptionIntent.CREATED))
        .extracting(Record::getIntent)
        .describedAs("Expect that the message boundary event is subscribed to after the migration")
        .contains(MessageSubscriptionIntent.CREATED, ProcessMessageSubscriptionIntent.CREATED);
  }

  @Test
  public void shouldMigrateSubprocessWithBoundaryEvent() {
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
                        "sub1",
                        s ->
                            s.embeddedSubProcess()
                                .startEvent()
                                .serviceTask("A", t -> t.zeebeJobType("task"))
                                .endEvent())
                    .boundaryEvent("boundary")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
                    .endEvent()
                    .moveToActivity("sub1")
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
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("key", "key").create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("sub1", "sub2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .withCorrelationKey("key")
                .exists())
        .describedAs("Expect that the message boundary event is unsubscribed")
        .isTrue();
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .withCorrelationKey("key")
                .exists())
        .describedAs("Expect that the process message subscription is unsubscribed")
        .isTrue();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SUB_PROCESS)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("sub2")
        .describedAs("Expect that version number did not change")
        .hasVersion(1);
  }

  @Test
  public void shouldMigrateSubprocessWithBoundaryEventInTarget() {
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
                    .boundaryEvent("boundary")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
                    .endEvent()
                    .moveToActivity("sub2")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("key", "key").create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementId("A")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("sub1", "sub2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .withCorrelationKey("key")
                .exists())
        .describedAs("Expect that the message boundary event is subscribed")
        .isTrue();
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .withCorrelationKey("key")
                .exists())
        .describedAs("Expect that the process message subscription is subscribed")
        .isTrue();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SUB_PROCESS)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("sub2")
        .describedAs("Expect that version number did not change")
        .hasVersion(1);
  }

  @Test
  public void shouldMigrateMappedBoundaryEventAttachedToTheSameMappedElement() {
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .boundaryEvent("boundary")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("start")
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .serviceTask("B", t -> t.zeebeJobType("B"))
                    .boundaryEvent("boundary")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent("end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("key", "key").create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementId("A")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary", "boundary")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasBpmnProcessId(targetProcessId);
  }

  @Test
  public void shouldRejectCommandWhenMappedBoundaryEventIsAttachedToDifferentElement() {
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .boundaryEvent("boundary")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("start")
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .serviceTask("B", t -> t.zeebeJobType("B"))
                    .boundaryEvent("boundary")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent("end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("key", "key").create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementId("A")
        .await();

    // when
    final var rejection =
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
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains("Expected to migrate process instance '" + processInstanceKey + "'")
        .contains("active element with id 'A' is mapped to an element with id 'A'")
        .contains(
            "and has a catch event with id 'boundary' that is mapped to a catch event with id 'boundary'")
        .contains("These mappings detach the catch event from the element in the target process")
        .contains("Catch events must stay attached to the same element instance");
  }

  @Test
  public void shouldRejectCommandWhenMappedBoundaryEventsAreMerged() {
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .boundaryEvent(
                        "boundary1",
                        b ->
                            b.message(m -> m.name("msg1").zeebeCorrelationKeyExpression("key"))
                                .endEvent())
                    .moveToActivity("A")
                    .boundaryEvent(
                        "boundary2",
                        b ->
                            b.message(m -> m.name("msg2").zeebeCorrelationKeyExpression("key"))
                                .endEvent())
                    .moveToActivity("A")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("start")
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .boundaryEvent(
                        "boundary3",
                        b ->
                            b.message(m -> m.name("msg3").zeebeCorrelationKeyExpression("key"))
                                .endEvent())
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("key", "key").create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("A", "A")
            .addMappingInstruction("boundary1", "boundary3")
            .addMappingInstruction("boundary2", "boundary3")
            .expectRejection()
            .migrate();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains("Expected to migrate process instance '" + processInstanceKey + "'")
        .contains("active element with id 'A' has a catch event attached")
        .contains("catch event attached that is mapped to a catch event with id 'boundary3'")
        .contains(
            "There are multiple mapping instructions that target this catch event: 'boundary1', 'boundary2'")
        .contains("Catch events cannot be merged by process instance migration")
        .contains("Please ensure the mapping instructions target a catch event only once");
  }

  @Test
  public void shouldRejectCommandWhenMappedBoundaryEventChangesEventType() {
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .boundaryEvent(
                        "boundary",
                        b ->
                            b.message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
                                .endEvent())
                    .moveToActivity("A")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("start")
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .boundaryEvent("boundary", b -> b.timerWithDuration("PT1M").endEvent())
                    .moveToActivity("A")
                    .endEvent("end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(sourceProcessId)
            .withVariable("key", "key")
            .create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementId("A")
        .await();

    // when
    final var rejection =
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
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains("Expected to migrate process instance '" + processInstanceKey + "'")
        .contains("active element with id 'A' has a catch event")
        .contains(
            "has a catch event with id 'boundary' that is mapped to a catch event with id 'boundary'")
        .contains("These catch events have different event types: 'MESSAGE' and 'TIMER'")
        .contains("The event type of a catch event cannot be changed by process instance migration")
        .contains("Please ensure the event type of the catch event remains the same")
        .contains("or remove the mapping instruction for these catch events");
  }
}
