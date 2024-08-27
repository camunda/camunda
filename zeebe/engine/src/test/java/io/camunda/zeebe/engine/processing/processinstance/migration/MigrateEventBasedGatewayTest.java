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
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
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
}
