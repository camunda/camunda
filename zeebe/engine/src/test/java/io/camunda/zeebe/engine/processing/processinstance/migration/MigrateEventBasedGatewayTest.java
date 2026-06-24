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
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateEventBasedGatewayTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteMigratedEventForElementInstance() {
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "_v2";
    final String correlationKey = helper.getCorrelationValue();

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .eventBasedGateway("gateway")
                    .intermediateCatchEvent("timer", b -> b.timerWithDuration("PT5M"))
                    .endEvent("A")
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "msg",
                        b -> b.message(m -> m.name("msg").zeebeCorrelationKeyExpression("key")))
                    .endEvent("B")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .eventBasedGateway("gateway2")
                    .intermediateCatchEvent("timer2", b -> b.timerWithDuration("PT5M"))
                    .endEvent("A")
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "msg2",
                        b -> b.message(m -> m.name("msg").zeebeCorrelationKeyExpression("key")))
                    .endEvent("B")
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(sourceProcessId)
            .withVariable("key", correlationKey)
            .create();

    final var gateway =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("gateway")
            .getFirst();
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName("msg")
        .withCorrelationKey(correlationKey)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("gateway", "gateway2")
        .addMappingInstruction("timer", "timer2")
        .addMappingInstruction("msg", "msg2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withRecordKey(gateway.getKey())
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasVersion(1)
        .hasElementId("gateway2");
  }

  @Test
  public void shouldHandleSubscriptionsForMessageCatchEvents() {
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "_v2";
    final String correlationKey = helper.getCorrelationValue();

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .eventBasedGateway("gateway")
                    .intermediateCatchEvent(
                        "msg_a",
                        b -> b.message(m -> m.name("msg_a").zeebeCorrelationKeyExpression("key")))
                    .endEvent("A")
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "msg_b",
                        b -> b.message(m -> m.name("msg_b").zeebeCorrelationKeyExpression("key")))
                    .endEvent("B")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .eventBasedGateway("gateway2")
                    .intermediateCatchEvent(
                        "msg_a2",
                        b -> b.message(m -> m.name("msg_a2").zeebeCorrelationKeyExpression("key")))
                    .endEvent("A")
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "msg_b2",
                        b -> b.message(m -> m.name("msg_b2").zeebeCorrelationKeyExpression("key")))
                    .endEvent("B")
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(sourceProcessId)
            .withVariable("key", correlationKey)
            .create();

    final var processMessageSubscriptionA =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withMessageName("msg_a")
            .withCorrelationKey(correlationKey)
            .getFirst();
    final var messageSubscriptionA =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withMessageName("msg_a")
            .withCorrelationKey(correlationKey)
            .getFirst();
    final var processMessageSubscriptionB =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withMessageName("msg_b")
            .withCorrelationKey(correlationKey)
            .getFirst();
    final var messageSubscriptionB =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withMessageName("msg_b")
            .withCorrelationKey(correlationKey)
            .getFirst();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("gateway", "gateway2")
        .addMappingInstruction("msg_a", "msg_a2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.MIGRATED)
                .withRecordKey(processMessageSubscriptionA.getKey())
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("msg_a2")
        .describedAs("Expect that the other data is unchanged")
        .hasMessageName(processMessageSubscriptionA.getValue().getMessageName())
        .hasCorrelationKey(processMessageSubscriptionA.getValue().getCorrelationKey())
        .hasTenantId(processMessageSubscriptionA.getValue().getTenantId())
        .hasProcessInstanceKey(processMessageSubscriptionA.getValue().getProcessInstanceKey())
        .hasRootProcessInstanceKey(
            processMessageSubscriptionA.getValue().getRootProcessInstanceKey())
        .hasElementInstanceKey(processMessageSubscriptionA.getValue().getElementInstanceKey())
        .hasMessageKey(processMessageSubscriptionA.getValue().getMessageKey())
        .hasVariables(processMessageSubscriptionA.getValue().getVariables());
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.MIGRATED)
                .withRecordKey(messageSubscriptionA.getKey())
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the other data is unchanged")
        .hasProcessInstanceKey(messageSubscriptionA.getValue().getProcessInstanceKey())
        .hasElementInstanceKey(messageSubscriptionA.getValue().getElementInstanceKey())
        .hasTenantId(messageSubscriptionA.getValue().getTenantId())
        .hasMessageName(messageSubscriptionA.getValue().getMessageName())
        .hasCorrelationKey(messageSubscriptionA.getValue().getCorrelationKey())
        .hasMessageKey(messageSubscriptionA.getValue().getMessageKey())
        .hasVariables(messageSubscriptionA.getValue().getVariables());

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.DELETED)
                .withRecordKey(processMessageSubscriptionB.getKey())
                .findFirst())
        .describedAs(
            "Expect that the existing process message subscription is deleted for the unmapped catch event")
        .isPresent();
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withRecordKey(messageSubscriptionB.getKey())
                .findFirst())
        .describedAs(
            "Expect that the existing message subscription is deleted for the unmapped catch event")
        .isPresent();

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg_b2")
                .withCorrelationKey(correlationKey)
                .getFirst()
                .getValue())
        .describedAs(
            "Expect that a new process message subscription is created for the unmapped catch event")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("msg_b2");
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg_b2")
                .withCorrelationKey(correlationKey)
                .getFirst()
                .getValue())
        .describedAs(
            "Expect that a new message subscription is created for the unmapped catch event")
        .hasBpmnProcessId(targetProcessId);
  }

  @Test
  public void shouldWriteMigratedEventsForTimerOfMappedTimerCatchEvent() {
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "_v2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .eventBasedGateway("gateway")
                    .intermediateCatchEvent(
                        "timerA", b -> b.timerWithDurationExpression("durationA"))
                    .endEvent("A")
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "timerB", b -> b.timerWithDurationExpression("durationB"))
                    .endEvent("B")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .eventBasedGateway("gateway2")
                    .intermediateCatchEvent(
                        "timerA2", b -> b.timerWithDurationExpression("durationA"))
                    .endEvent("A")
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "timerB2", b -> b.timerWithDurationExpression("durationB"))
                    .endEvent("B")
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(sourceProcessId)
            .withVariables(Map.of("durationA", "PT5M", "durationB", "PT10M"))
            .create();

    final var timerRecordA =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withHandlerNodeId("timerA")
            .getFirst();
    final var timerRecordB =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withHandlerNodeId("timerB")
            .getFirst();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("gateway", "gateway2")
        .addMappingInstruction("timerA", "timerA2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.MIGRATED)
                .withRecordKey(timerRecordA.getKey())
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasTargetElementId("timerA2")
        .describedAs("Expect that the other data is unchanged")
        .hasDueDate(timerRecordA.getValue().getDueDate())
        .hasRepetitions(timerRecordA.getValue().getRepetitions())
        .hasTenantId(timerRecordA.getValue().getTenantId())
        .hasProcessInstanceKey(timerRecordA.getValue().getProcessInstanceKey())
        .hasElementInstanceKey(timerRecordA.getValue().getElementInstanceKey());

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withRecordKey(timerRecordB.getKey())
                .findAny())
        .describedAs("Expect that the existing timer is canceled for the unmapped catch event")
        .isPresent();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withHandlerNodeId("timerB2")
                .findAny())
        .describedAs("Expect that a new timer is created for the unmapped catch event")
        .isPresent();
  }

  @Test
  public void shouldRejectCommandWhenMappedCatchEventIsAttachedToDifferentElement() {
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .eventBasedGateway("gateway")
                    .intermediateCatchEvent(
                        "timerA", b -> b.timerWithDurationExpression("durationA"))
                    .endEvent("A")
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "timerB", b -> b.timerWithDurationExpression("durationB"))
                    .endEvent("B")
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "timerC", b -> b.timerWithDurationExpression("durationC"))
                    .endEvent("C")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .eventBasedGateway("gateway2")
                    .intermediateCatchEvent(
                        "timerA2", b -> b.timerWithDurationExpression("durationA"))
                    .intermediateCatchEvent(
                        "timerC2", b -> b.timerWithDurationExpression("durationC"))
                    .endEvent("AC")
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "timerB2", b -> b.timerWithDurationExpression("durationB"))
                    .endEvent("B")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(
                Map.ofEntries(
                    Map.entry("durationA", "PT5M"),
                    Map.entry("durationB", "PT10M"),
                    Map.entry("durationC", "PT15M")))
            .create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("gateway")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("gateway", "gateway2")
            .addMappingInstruction("timerA", "timerA2")
            .addMappingInstruction("timerB", "timerB2")
            .addMappingInstruction("timerC", "timerC2")
            .expectRejection()
            .migrate();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains("Expected to migrate process instance '" + processInstanceKey + "'")
        .contains("active element with id 'gateway' is mapped to an element with id 'gateway2'")
        .contains(
            "and has a catch event with id 'timerC' that is mapped to a catch event with id 'timerC2'")
        .contains("These mappings detach the catch event from the element in the target process")
        .contains("Catch events must stay attached to the same element instance");
  }

  @Test
  public void shouldRejectCommandWhenMappedCatchEventsAreMerged() {
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .eventBasedGateway("gateway")
                    .intermediateCatchEvent(
                        "timerA", b -> b.timerWithDurationExpression("durationA"))
                    .endEvent("A")
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "timerB", b -> b.timerWithDurationExpression("durationB"))
                    .endEvent("B")
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "timerC", b -> b.timerWithDurationExpression("durationC"))
                    .endEvent("C")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .eventBasedGateway("gateway2")
                    .intermediateCatchEvent(
                        "timerA2", b -> b.timerWithDurationExpression("durationA"))
                    .intermediateCatchEvent(
                        "timerC2", b -> b.timerWithDurationExpression("durationC"))
                    .endEvent("AC")
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "timerB2", b -> b.timerWithDurationExpression("durationB"))
                    .endEvent("B")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(
                Map.ofEntries(
                    Map.entry("durationA", "PT5M"),
                    Map.entry("durationB", "PT10M"),
                    Map.entry("durationC", "PT15M")))
            .create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("gateway")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("gateway", "gateway2")
            .addMappingInstruction("timerA", "timerA2")
            .addMappingInstruction("timerB", "timerB2")
            .addMappingInstruction("timerC", "timerA2")
            .expectRejection()
            .migrate();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains("Expected to migrate process instance '" + processInstanceKey + "'")
        .contains("active element with id 'gateway' has a catch event attached")
        .contains("catch event attached that is mapped to a catch event with id 'timerA2'")
        .contains(
            "There are multiple mapping instructions that target this catch event: 'timerA', 'timerC'")
        .contains("Catch events cannot be merged by process instance migration")
        .contains("Please ensure the mapping instructions target a catch event only once");
  }

  @Test
  public void shouldRejectCommandWhenMappedCatchEventChangesEventType() {
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .eventBasedGateway("gateway")
                    .intermediateCatchEvent(
                        "timerA", b -> b.timerWithDurationExpression("durationA"))
                    .endEvent("A")
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "timerB", b -> b.timerWithDurationExpression("durationB"))
                    .endEvent("B")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .eventBasedGateway("gateway2")
                    .intermediateCatchEvent(
                        "timerA2", b -> b.timerWithDurationExpression("durationA"))
                    .endEvent("A2")
                    .moveToLastGateway()
                    .intermediateCatchEvent(
                        "msgB2",
                        b -> b.message(m -> m.name("msgB").zeebeCorrelationKeyExpression("key")))
                    .endEvent("B2")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(
                Map.ofEntries(Map.entry("durationA", "PT5M"), Map.entry("durationB", "PT10M")))
            .create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("gateway")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("gateway", "gateway2")
            .addMappingInstruction("timerA", "timerA2")
            .addMappingInstruction("timerB", "msgB2")
            .expectRejection()
            .migrate();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .extracting(Record::getRejectionReason)
        .asString()
        .contains("Expected to migrate process instance '" + processInstanceKey + "'")
        .contains("active element with id 'gateway' has a catch event")
        .contains(
            "has a catch event with id 'timerB' that is mapped to a catch event with id 'msgB2'")
        .contains("These catch events have different event types: 'TIMER' and 'MESSAGE'")
        .contains("The event type of a catch event cannot be changed by process instance migration")
        .contains("Please ensure the event type of the catch event remains the same")
        .contains("or remove the mapping instruction for these catch events");
  }
}
