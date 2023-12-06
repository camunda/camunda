/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateProcessInstanceEventSubscriptionsTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldUnsubscribeMessageBoundaryEventForProcessInstance() {
    // given
    final String processId1 = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .boundaryEvent(
                        "boundaryEvent",
                        e ->
                            e.message(
                                m ->
                                    m.name("message")
                                        .zeebeCorrelationKeyExpression("\"correlationKey\"")))
                    .userTask()
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(processId2)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();

    final long otherProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId2);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId1).create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName("message")
        .withCorrelationKey("correlationKey")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(otherProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .withCorrelationKey("correlationKey")
                .findAny())
        .isPresent();

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst()
                .getValue())
        .describedAs("Expected to delete process message subscription for process instance")
        .hasBpmnProcessId(processId1)
        .hasElementId("boundaryEvent")
        .hasCorrelationKey("correlationKey");
  }

  @Test
  public void shouldUnsubscribeTimerBoundaryEventForProcessInstance() {
    // given
    final String processId1 = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .boundaryEvent("boundaryEvent", e -> e.timerWithDuration("PT1M"))
                    .userTask()
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(processId2)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();

    final long otherProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId2);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId1).create();

    final long processDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId1);
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .getFirst()
                .getValue())
        .hasTargetElementId("boundaryEvent");

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(otherProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .getFirst()
                .getValue())
        .describedAs("Expected to cancel timer for process instance")
        .hasTargetElementId("boundaryEvent");
  }

  @Test
  public void shouldUnsubscribeSignalBoundaryEventForProcessInstance() {
    // given
    final String processId1 = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .boundaryEvent("boundaryEvent", e -> e.signal("signal"))
                    .userTask()
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(processId2)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();

    final long otherProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId2);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId1).create();

    final long processDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId1);
    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withBpmnProcessId(processId1)
        .withProcessDefinitionKey(processDefinitionKey)
        .withSignalName("signal")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(otherProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .withSignalName("signal")
                .getFirst()
                .getValue())
        .describedAs("Expected to delete signal subscription for process instance")
        .hasBpmnProcessId(processId1)
        .hasProcessDefinitionKey(processDefinitionKey);
  }

  @Test
  public void shouldUnsubscribeMessageEventSubProcessForProcessInstance() {
    // given
    final String processId1 = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .eventSubProcess(
                        "eventSubProcess",
                        sub ->
                            sub.startEvent(
                                    "eventSubProcessStart",
                                    s ->
                                        s.message(
                                            m ->
                                                m.name("message")
                                                    .zeebeCorrelationKeyExpression(
                                                        "\"correlationKey\"")))
                                .endEvent())
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(processId2)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();

    final long otherProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId2);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId1).create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName("message")
        .withCorrelationKey("correlationKey")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(otherProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .withCorrelationKey("correlationKey")
                .findAny())
        .isPresent();

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst()
                .getValue())
        .describedAs(
            "Expected to delete event subprocess message subscription for process instance")
        .hasBpmnProcessId(processId1)
        .hasElementId("eventSubProcessStart")
        .hasCorrelationKey("correlationKey");
  }

  @Test
  public void shouldUnsubscribeTimerEventSubProcessForProcessInstance() {
    // given
    final String processId1 = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .eventSubProcess(
                        "eventSubProcess",
                        sub ->
                            sub.startEvent(
                                "eventSubProcessStart", s -> s.timerWithDuration("PT1M")))
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(processId2)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();

    final long otherProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId2);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId1).create();

    final long processDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId1);
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .getFirst()
                .getValue())
        .hasTargetElementId("eventSubProcessStart");

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(otherProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .getFirst()
                .getValue())
        .describedAs("Expected to cancel event subprocess timer start event for process instance")
        .hasTargetElementId("eventSubProcessStart");
  }

  @Test
  public void shouldUnsubscribeSignalEventSubProcessForProcessInstance() {
    // given
    final String processId1 = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .eventSubProcess(
                        "eventSubProcess",
                        sub -> sub.startEvent("eventSubProcessStart", s -> s.signal("signal")))
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(processId2)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();

    final long otherProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId2);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId1).create();

    final long processDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId1);
    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withBpmnProcessId(processId1)
        .withProcessDefinitionKey(processDefinitionKey)
        .withSignalName("signal")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(otherProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .withSignalName("signal")
                .getFirst()
                .getValue())
        .describedAs("Expected to delete event subprocess signal subscription for process instance")
        .hasBpmnProcessId(processId1)
        .hasProcessDefinitionKey(processDefinitionKey);
  }

  @Test
  public void shouldSubscribeMessageBoundaryEventForTargetProcessDefinition() {
    // given
    final String processId1 = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(processId2)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .boundaryEvent(
                        "boundaryEvent",
                        e ->
                            e.message(
                                m ->
                                    m.name("message")
                                        .zeebeCorrelationKeyExpression("\"correlationKey\"")))
                    .userTask()
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();

    final long otherProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId2);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId1).create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(otherProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .withCorrelationKey("correlationKey")
                .findAny())
        .isPresent();

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst()
                .getValue())
        .describedAs(
            "Expected to create process message subscription for target process definition")
        .hasBpmnProcessId(processId2)
        .hasElementId("boundaryEvent")
        .hasCorrelationKey("correlationKey");
  }

  @Test
  public void shouldSubscribeTimerBoundaryEventForTargetProcessDefinition() {
    // given
    final String processId1 = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(processId2)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .boundaryEvent("boundaryEvent", e -> e.timerWithDuration("PT1M"))
                    .userTask()
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();

    final long otherProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId2);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId1).create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(otherProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    final long processDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId2);
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .getFirst()
                .getValue())
        .describedAs("Expected to create timer for target process definition")
        .hasTargetElementId("boundaryEvent");
  }

  @Test
  public void shouldSubscribeSignalBoundaryEventForTargetProcessDefinition() {
    // given
    final String processId1 = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(processId2)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .boundaryEvent("boundaryEvent", e -> e.signal("signal"))
                    .userTask()
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();

    final long otherProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId2);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId1).create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(otherProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
                .withSignalName("signal")
                .getFirst()
                .getValue())
        .describedAs("Expected to create signal subscription for target process definition")
        .hasBpmnProcessId(processId2)
        .hasProcessDefinitionKey(otherProcessDefinitionKey);
  }

  @Test
  public void shouldSubscribeMessageEventSubProcessForTargetProcessDefinition() {
    // given
    final String processId1 = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(processId2)
                    .eventSubProcess(
                        "eventSubProcess",
                        sub ->
                            sub.startEvent(
                                    "eventSubProcessStart",
                                    s ->
                                        s.message(
                                            m ->
                                                m.name("message")
                                                    .zeebeCorrelationKeyExpression(
                                                        "\"correlationKey\"")))
                                .endEvent())
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();

    final long otherProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId2);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId1).create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(otherProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .withCorrelationKey("correlationKey")
                .findAny())
        .isPresent();

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst()
                .getValue())
        .describedAs(
            "Expected to create event subprocess process message subscription for target process definition")
        .hasBpmnProcessId(processId2)
        .hasElementId("eventSubProcessStart")
        .hasCorrelationKey("correlationKey");
  }

  @Test
  public void shouldSubscribeTimerEventSubProcessForTargetProcessDefinition() {
    // given
    final String processId1 = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(processId2)
                    .eventSubProcess(
                        "eventSubProcess",
                        sub ->
                            sub.startEvent(
                                "eventSubProcessStart", s -> s.timerWithDuration("PT1M")))
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKey2 =
        extractProcessDefinitionKeyByProcessId(deployment, processId2);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId1).create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(processDefinitionKey2)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey2)
                .getFirst()
                .getValue())
        .describedAs("Expected to create event subprocess timer for target process definition")
        .hasTargetElementId("eventSubProcessStart");
  }

  @Test
  public void shouldSubscribeSignalEventSubProcessForTargetProcessDefinition() {
    // given
    final String processId1 = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(processId2)
                    .eventSubProcess(
                        "eventSubProcess",
                        sub -> sub.startEvent("eventSubProcessStart", s -> s.signal("signal")))
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKey2 =
        extractProcessDefinitionKeyByProcessId(deployment, processId2);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId1).create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(processDefinitionKey2)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
                .withSignalName("signal")
                .getFirst()
                .getValue())
        .describedAs(
            "Expected to create event subprocess signal subscription for target process definition")
        .hasBpmnProcessId(processId2)
        .hasProcessDefinitionKey(processDefinitionKey2);
  }

  @Test
  public void shouldRecreateMessageSubscription() {
    // given
    final String processId1 = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .boundaryEvent(
                        "boundaryEvent",
                        e ->
                            e.message(
                                m ->
                                    m.name("message")
                                        .zeebeCorrelationKeyExpression("\"correlationKey\"")))
                    .userTask()
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(processId2)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .boundaryEvent(
                        "boundaryEvent2",
                        e ->
                            e.message(
                                m ->
                                    m.name("message2")
                                        .zeebeCorrelationKeyExpression("\"correlationKey2\"")))
                    .userTask()
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();

    final long otherProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId2);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId1).create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName("message")
        .withCorrelationKey("correlationKey")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(otherProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .withCorrelationKey("correlationKey")
                .findAny())
        .isPresent();

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .getFirst()
                .getValue())
        .describedAs("Expected to delete process message subscription for process instance")
        .hasBpmnProcessId(processId1)
        .hasElementId("boundaryEvent")
        .hasCorrelationKey("correlationKey");

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message2")
                .withCorrelationKey("correlationKey2")
                .findAny())
        .isPresent();

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message2")
                .getFirst()
                .getValue())
        .describedAs(
            "Expected to create process message subscription for target process definition")
        .hasBpmnProcessId(processId2)
        .hasElementId("boundaryEvent2")
        .hasCorrelationKey("correlationKey2");
  }

  private static long extractProcessDefinitionKeyByProcessId(
      final Record<DeploymentRecordValue> deployment, final String processId) {
    return deployment.getValue().getProcessesMetadata().stream()
        .filter(p -> p.getBpmnProcessId().equals(processId))
        .findAny()
        .orElseThrow()
        .getProcessDefinitionKey();
  }
}
