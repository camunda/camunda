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
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateIntermediateCatchEventTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteMessageSubscriptionMigratedEvent() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .intermediateCatchEvent(
                        "catch1",
                        c -> c.message(m -> m.name("msg1").zeebeCorrelationKeyExpression("key1")))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .intermediateCatchEvent(
                        "catch2",
                        c -> c.message(m -> m.name("msg2").zeebeCorrelationKeyExpression("key2")))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("key1", "key1"))
            .create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("catch1", "catch2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("catch2")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");
  }

  @Test
  public void shouldCorrelateMessageSubscriptionAfterMigration() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .intermediateCatchEvent(
                        "catch1",
                        c -> c.message(m -> m.name("msg1").zeebeCorrelationKeyExpression("key1")))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .intermediateCatchEvent(
                        "catch2",
                        c -> c.message(m -> m.name("msg2").zeebeCorrelationKeyExpression("key2")))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("key1", "key1"))
            .create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("catch1", "catch2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg1")
                .getFirst())
        .isNotNull();
    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg1")
                .getFirst())
        .isNotNull();

    engine.message().withName("msg1").withCorrelationKey("key1").publish();

    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("catch2")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1")
        .hasRootProcessInstanceKey(processInstanceKey);

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");
  }

  @Test
  public void shouldWriteTimerInstanceMigratedEvent() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .intermediateCatchEvent("catch1", c -> c.timerWithDuration("PT5M"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .intermediateCatchEvent("catch2", c -> c.timerWithDuration("PT10M"))
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
        .addMappingInstruction("catch1", "catch2")
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
        .hasTargetElementId("catch2")
        .describedAs("Expect that the due date is not changed")
        .hasDueDate(timerRecord.getDueDate());

    engine.increaseTime(Duration.ofMinutes(6));

    Assertions.assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the target element id is updated")
        .hasTargetElementId("catch2")
        .describedAs("Expect that the due date is not changed")
        .hasDueDate(timerRecord.getDueDate());
  }

  @Test
  public void shouldWriteSignalSubscriptionMigratedEvent() {
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
                    .intermediateCatchEvent("catch1", c -> c.signal(signalName))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .intermediateCatchEvent("catch2", c -> c.signal(signalName))
                    .endEvent("target_process_signal_end")
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName(signalName)
        .withCatchEventId("catch1")
        .await();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("catch1", "catch2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.MIGRATED)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the catch event id is updated")
        .hasCatchEventId("catch2")
        .describedAs("Expect that the signal name is not changed")
        .hasSignalName(signalName);

    engine.signal().withSignalName(signalName).broadcast();

    Assertions.assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the catch event id is updated")
        .hasCatchEventId("catch2")
        .describedAs("Expect that the signal name is not changed")
        .hasSignalName(signalName);

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
