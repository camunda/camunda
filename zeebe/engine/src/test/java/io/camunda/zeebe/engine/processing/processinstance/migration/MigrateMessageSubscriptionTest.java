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
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateMessageSubscriptionTest {

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
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key1"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary2")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key2"))
                    .endEvent()
                    .moveToActivity("B")
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
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("boundary2")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
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
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key1"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary2")
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key2"))
                    .endEvent()
                    .moveToActivity("B")
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
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst())
        .isNotNull();
    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst())
        .isNotNull();

    engine.message().withName("message").withCorrelationKey("key1").publish();

    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("boundary2")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1")
        .hasRootProcessInstanceKey(processInstanceKey);

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");
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
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key1"))
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
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key2"))
                    .endEvent()
                    .moveToActivity("B")
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

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .isNotInterrupting();

    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .isNotInterrupting();

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
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("boundary2")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1")
        .describedAs("Expected that the interrupting status is updated")
        .isInterrupting();

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1")
        .describedAs("Expected that the interrupting status is updated")
        .isInterrupting();

    engine.message().withName("message").withCorrelationKey("key1").publish();

    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("boundary2")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1")
        .describedAs("Expected that the interrupting status is updated")
        .isInterrupting()
        .describedAs("Expect the root process instance key is set")
        .hasRootProcessInstanceKey(processInstanceKey);

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1")
        .describedAs("Expected that the interrupting status is updated")
        .isInterrupting();

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
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key1"))
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
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key2"))
                    .endEvent()
                    .moveToActivity("B")
                    .userTask("C")
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

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .isInterrupting();

    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .isInterrupting();

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
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("boundary2")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1")
        .describedAs("Expected that the interrupting status is updated")
        .isNotInterrupting();

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1")
        .describedAs("Expected that the interrupting status is updated")
        .isNotInterrupting();

    engine.message().withName("message").withCorrelationKey("key1").publish();

    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("boundary2")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1")
        .describedAs("Expected that the interrupting status is updated")
        .isNotInterrupting()
        .describedAs("Expect the root process instance key is set")
        .hasRootProcessInstanceKey(processInstanceKey);

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1")
        .describedAs("Expected that the interrupting status is updated")
        .isNotInterrupting();

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

  @Test
  public void shouldMigrateMultipleMessageBoundaryEvents() {
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
                    .message(m -> m.name("message1").zeebeCorrelationKeyExpression("key1"))
                    .endEvent()
                    .moveToActivity("A")
                    .boundaryEvent("boundary2")
                    .message(m -> m.name("message2").zeebeCorrelationKeyExpression("key2"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary3")
                    .cancelActivity(false) // to test the second message correlation
                    .message(m -> m.name("message3").zeebeCorrelationKeyExpression("key3"))
                    .endEvent()
                    .moveToActivity("B")
                    .boundaryEvent("boundary4")
                    .message(m -> m.name("message4").zeebeCorrelationKeyExpression("key4"))
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("key1", "key1", "key2", "key2"))
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
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary3")
        .addMappingInstruction("boundary2", "boundary4")
        .migrate();

    // then
    // assert that the first boundary event is migrated
    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message1")
                .withCorrelationKey("key1")
                .getFirst())
        .isNotNull();
    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message1")
                .withCorrelationKey("key1")
                .getFirst())
        .isNotNull();

    // assert that the second boundary event is migrated
    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message2")
                .withCorrelationKey("key2")
                .getFirst())
        .isNotNull();
    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message2")
                .withCorrelationKey("key2")
                .getFirst())
        .isNotNull();

    // publish a message for the first boundary event
    engine.message().withName("message1").withCorrelationKey("key1").publish();

    // assert that the first boundary event is correlated
    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("boundary3")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1")
        .describedAs("Expect the root process instance key is set")
        .hasRootProcessInstanceKey(processInstanceKey);

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");

    // publish a message for the second boundary event
    engine.message().withName("message2").withCorrelationKey("key2").publish();

    // assert that the second boundary event is correlated
    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message2")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("boundary4")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key2")
        .describedAs("Expect the root process instance key is set")
        .hasRootProcessInstanceKey(processInstanceKey);

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message2")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key2");
  }

  @Test
  public void shouldMigrateOneOfMultipleMessageBoundaryEventsAndUnsubscribe() {
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
                    .message(m -> m.name("message1").zeebeCorrelationKeyExpression("key1"))
                    .endEvent()
                    .moveToActivity("A")
                    .boundaryEvent("boundary2")
                    .message(m -> m.name("message2").zeebeCorrelationKeyExpression("key2"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary3")
                    .message(m -> m.name("message3").zeebeCorrelationKeyExpression("key3"))
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("key1", "key1", "key2", "key2"))
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
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary3")
        .migrate();

    // then
    // assert that the first boundary event is migrated
    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message1")
                .withCorrelationKey("key1")
                .getFirst())
        .isNotNull();
    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message1")
                .withCorrelationKey("key1")
                .getFirst())
        .isNotNull();

    // assert that the second boundary event is unsubscribed
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message2")
                .withCorrelationKey("key2")
                .exists())
        .describedAs("Expect that the second message boundary event is unsubscribed")
        .isTrue();
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message2")
                .withCorrelationKey("key2")
                .exists())
        .describedAs("Expect that the second message boundary event is unsubscribed")
        .isTrue();

    // publish a message for the first boundary event
    engine.message().withName("message1").withCorrelationKey("key1").publish();

    // assert that the first boundary event is correlated
    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("boundary3")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1")
        .describedAs("Expect the root process instance key is set")
        .hasRootProcessInstanceKey(processInstanceKey);

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");
  }

  @Test
  public void shouldMigrateOneOfMultipleMessageBoundaryEventsAndSubscribe() {
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
                    .message(m -> m.name("message1").zeebeCorrelationKeyExpression("key1"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary2")
                    .message(m -> m.name("message2").zeebeCorrelationKeyExpression("key2"))
                    .endEvent()
                    .moveToActivity("B")
                    .boundaryEvent("boundary3")
                    .message(m -> m.name("message3").zeebeCorrelationKeyExpression("key3"))
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("key1", "key1", "key3", "key3"))
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
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary2")
        .migrate();

    // then
    // assert that the first boundary event is migrated
    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message1")
                .withCorrelationKey("key1")
                .getFirst())
        .isNotNull();
    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message1")
                .withCorrelationKey("key1")
                .getFirst())
        .isNotNull();

    // assert that the second boundary event is subscribed
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message3")
                .withCorrelationKey("key3")
                .exists())
        .describedAs("Expect that the second message boundary event is subscribed")
        .isTrue();
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message3")
                .withCorrelationKey("key3")
                .exists())
        .describedAs("Expect that the second message boundary event is subscribed")
        .isTrue();

    // publish a message for the migrated boundary event
    engine.message().withName("message1").withCorrelationKey("key1").publish();

    // assert that the migrated boundary event is correlated
    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("boundary2")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1")
        .describedAs("Expect the root process instance key is set")
        .hasRootProcessInstanceKey(processInstanceKey);

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message1")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");
  }
}
