/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.message;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.engine.util.client.PublishMessageClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.WorkflowInstances;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public final class MessageCorrelationTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance RECEIVE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask("receive-message")
          .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
          .endEvent()
          .done();

  private static final BpmnModelInstance SINGLE_MESSAGE_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
          .endEvent()
          .done();

  private static final BpmnModelInstance SINGLE_MESSAGE_WORKFLOW_WITH_FEEL_EXPRESSION_MESSAGE_NAME =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.nameExpression("\"message\"").zeebeCorrelationKeyExpression("key"))
          .endEvent()
          .done();

  private static final BpmnModelInstance TWO_MESSAGES_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("message1")
          .message(m -> m.name("ping").zeebeCorrelationKeyExpression("key"))
          .intermediateCatchEvent("message2")
          .message(m -> m.name("ping").zeebeCorrelationKeyExpression("key"))
          .done();

  private static final BpmnModelInstance BOUNDARY_EVENTS_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask("task")
          .message(m -> m.name("taskMsg").zeebeCorrelationKeyExpression("key"))
          .boundaryEvent("msg1")
          .message(m -> m.name("msg1").zeebeCorrelationKeyExpression("key"))
          .endEvent("msg1End")
          .moveToActivity("task")
          .boundaryEvent("msg2")
          .message(m -> m.name("msg2").zeebeCorrelationKeyExpression("key"))
          .endEvent("msg2End")
          .moveToActivity("task")
          .endEvent("taskEnd")
          .done();

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldCorrelateMessageIfEnteredBefore() {
    // given
    final String messageId = UUID.randomUUID().toString();
    engine.deployment().withXmlResource(SINGLE_MESSAGE_WORKFLOW).deploy();
    final long workflowInstanceKey =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .exists())
        .isTrue();

    // when
    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-123")
        .withTimeToLive(1000L)
        .withVariables(asMsgPack("foo", "bar"))
        .withId(messageId)
        .publish();

    // then
    final Record<WorkflowInstanceRecordValue> event =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("receive-message")
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, event.getPosition());
    assertThat(variables).containsOnly(entry("key", "\"order-123\""), entry("foo", "\"bar\""));
  }

  @Test
  public void shouldCorrelateMessageIfPublishedBefore() {
    // given
    engine.deployment().withXmlResource(SINGLE_MESSAGE_WORKFLOW).deploy();

    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-123")
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    // when
    final long workflowInstanceKey =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();

    // then
    final Record<WorkflowInstanceRecordValue> event =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("receive-message")
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, event.getPosition());
    assertThat(variables).containsOnly(entry("key", "\"order-123\""), entry("foo", "\"bar\""));
  }

  @Test
  public void shouldCorrelateMessageToMessageWithFeelExpressionNameIfPublishedBefore() {
    // given
    engine
        .deployment()
        .withXmlResource(SINGLE_MESSAGE_WORKFLOW_WITH_FEEL_EXPRESSION_MESSAGE_NAME)
        .deploy();

    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-123")
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    // when
    final long workflowInstanceKey =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();

    // then
    final Record<WorkflowInstanceRecordValue> event =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("receive-message")
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, event.getPosition());
    assertThat(variables).containsOnly(entry("key", "\"order-123\""), entry("foo", "\"bar\""));
  }

  @Test
  public void shouldCorrelateMessageIfCorrelationKeyIsANumber() {
    // given
    engine.deployment().withXmlResource(SINGLE_MESSAGE_WORKFLOW).deploy();

    engine
        .message()
        .withName("message")
        .withCorrelationKey("123")
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    // when
    final long workflowInstanceKey =
        engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", 123).create();

    // then
    final Record<WorkflowInstanceRecordValue> event =
        RecordingExporter.workflowInstanceRecords()
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, event.getPosition());
    assertThat(variables).containsOnly(entry("key", "123"), entry("foo", "\"bar\""));
  }

  @Test
  public void shouldCorrelateFirstPublishedMessage() {
    // given
    engine.deployment().withXmlResource(SINGLE_MESSAGE_WORKFLOW).deploy();

    final PublishMessageClient messageClient =
        engine.message().withName("message").withCorrelationKey("order-123");

    messageClient.withVariables(asMsgPack("nr", 1)).publish();
    messageClient.withVariables(asMsgPack("nr", 2)).publish();

    // when
    final long workflowInstanceKey =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();

    // then
    final Record<WorkflowInstanceRecordValue> event =
        RecordingExporter.workflowInstanceRecords()
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, event.getPosition());
    assertThat(variables).containsOnly(entry("key", "\"order-123\""), entry("nr", "1"));
  }

  @Test
  public void shouldCorrelateMessageWithZeroTTL() {
    // given
    engine.deployment().withXmlResource(SINGLE_MESSAGE_WORKFLOW).deploy();

    final long workflowInstanceKey =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .exists())
        .isTrue();

    // when
    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-123")
        .withVariables(asMsgPack("foo", "bar"))
        .withTimeToLive(0L)
        .publish();

    // then
    final Record<WorkflowInstanceRecordValue> event =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("receive-message")
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();

    assertThat(event.getValue().getWorkflowInstanceKey()).isEqualTo(workflowInstanceKey);
  }

  @Test
  public void shouldCorrelateMessageByCorrelationKey() {
    // given
    engine.deployment().withXmlResource(SINGLE_MESSAGE_WORKFLOW).deploy();

    final long workflowInstanceKey1 =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();
    final long workflowInstanceKey2 =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-456")
            .create();

    // when
    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-123")
        .withVariables(asMsgPack("nr", 1))
        .publish();

    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-456")
        .withVariables(asMsgPack("nr", 2))
        .publish();

    // then
    final Record<WorkflowInstanceRecordValue> catchEventOccurred1 =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey1)
            .withElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT)
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    final Map<String, String> variables1 =
        WorkflowInstances.getCurrentVariables(
            workflowInstanceKey1, catchEventOccurred1.getPosition());
    assertThat(variables1).containsOnly(entry("key", "\"order-123\""), entry("nr", "1"));

    final Record<WorkflowInstanceRecordValue> catchEventOccurred2 =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey2)
            .withElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT)
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    final Map<String, String> variables2 =
        WorkflowInstances.getCurrentVariables(
            workflowInstanceKey2, catchEventOccurred2.getPosition());
    assertThat(variables2).containsOnly(entry("key", "\"order-456\""), entry("nr", "2"));
  }

  @Test
  public void shouldCorrelateMessageToDifferentWorkflows() {
    // given
    engine
        .deployment()
        .withXmlResource("wf-1.bpmn", SINGLE_MESSAGE_WORKFLOW)
        .withXmlResource(
            "wf-2.bpmn",
            Bpmn.createExecutableProcess("process-2")
                .startEvent()
                .intermediateCatchEvent(
                    "catch",
                    c -> c.message(m -> m.name("message").zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .done())
        .deploy();

    final long workflowInstanceKey1 =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();
    final long workflowInstanceKey2 =
        engine
            .workflowInstance()
            .ofBpmnProcessId("process-2")
            .withVariable("key", "order-123")
            .create();

    // when
    final var message =
        engine.message().withName("message").withCorrelationKey("order-123").publish();

    // then
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.CORRELATED)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getMessageKey(), v.getWorkflowInstanceKey()))
        .contains(
            tuple(message.getKey(), workflowInstanceKey1),
            tuple(message.getKey(), workflowInstanceKey2));
  }

  @Test
  public void shouldCorrelateMessageOnlyOncePerWorkflow() {
    // given
    engine.deployment().withXmlResource(SINGLE_MESSAGE_WORKFLOW).deploy();

    final long workflowInstanceKey1 =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();
    final long workflowInstanceKey2 =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();

    // when
    final var message1 =
        engine.message().withName("message").withCorrelationKey("order-123").publish();

    final var message2 =
        engine.message().withName("message").withCorrelationKey("order-123").publish();

    // then
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.CORRELATED)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getMessageKey(), v.getWorkflowInstanceKey()))
        .contains(
            tuple(message1.getKey(), workflowInstanceKey1),
            tuple(message2.getKey(), workflowInstanceKey2));
  }

  @Test
  public void shouldCorrelateMessageOnlyOncePerWorkflowAcrossVersions() {
    // given
    engine.deployment().withXmlResource(SINGLE_MESSAGE_WORKFLOW).deploy();

    final long workflowInstanceKey1 =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();

    engine
        .deployment()
        .withXmlResource(
            "wf_v2.bpmn",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .intermediateCatchEvent(
                    "catch",
                    c -> c.message(m -> m.name("message").zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .done())
        .deploy();

    final long workflowInstanceKey2 =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();

    // when
    final var message1 =
        engine.message().withName("message").withCorrelationKey("order-123").publish();

    final var message2 =
        engine.message().withName("message").withCorrelationKey("order-123").publish();

    // then
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.CORRELATED)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getMessageKey(), v.getWorkflowInstanceKey()))
        .contains(
            tuple(message1.getKey(), workflowInstanceKey1),
            tuple(message2.getKey(), workflowInstanceKey2));
  }

  @Test
  public void shouldCorrelateMessageOnlyOnceIfPublishedBefore() {
    // given
    engine.deployment().withXmlResource(TWO_MESSAGES_WORKFLOW).deploy();

    final PublishMessageClient messageClient =
        engine.message().withName("ping").withCorrelationKey("123");

    messageClient.withVariables(asMsgPack("nr", 1)).publish();
    messageClient.withVariables(asMsgPack("nr", 2)).publish();

    // when
    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    // then
    final List<Object> correlatedValues =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .filter(r -> r.getValue().getElementId().startsWith("message"))
            .limit(2)
            .map(
                event ->
                    tuple(
                        event.getValue().getElementId(),
                        WorkflowInstances.getCurrentVariables(
                                event.getValue().getWorkflowInstanceKey(), event.getPosition())
                            .get("nr")))
            .collect(Collectors.toList());
    assertThat(correlatedValues).contains(tuple("message1", "1"), tuple("message2", "2"));
  }

  @Test
  public void shouldCorrelateMessageOnlyOnceIfEnteredBefore() {
    // given
    engine.deployment().withXmlResource(TWO_MESSAGES_WORKFLOW).deploy();
    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    // when
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    final PublishMessageClient messageClient =
        engine.message().withName("ping").withCorrelationKey("123");

    messageClient.withVariables(asMsgPack("nr", 1)).publish();

    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .limit(2)
                .count())
        .isEqualTo(2);

    messageClient.withVariables(asMsgPack("nr", 2)).publish();

    // then
    final List<Object> correlatedValues =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .filter(r -> r.getValue().getElementId().startsWith("message"))
            .limit(2)
            .map(
                event ->
                    tuple(
                        event.getValue().getElementId(),
                        WorkflowInstances.getCurrentVariables(
                                event.getValue().getWorkflowInstanceKey(), event.getPosition())
                            .get("nr")))
            .collect(Collectors.toList());
    assertThat(correlatedValues).contains(tuple("message1", "1"), tuple("message2", "2"));
  }

  @Test
  public void shouldCorrelateMessageOnlyOnceToInstance() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .parallelGateway()
                .intermediateCatchEvent("message1")
                .message(m -> m.name("ping").zeebeCorrelationKeyExpression("key"))
                .moveToLastGateway()
                .intermediateCatchEvent("message2")
                .message(m -> m.name("ping").zeebeCorrelationKeyExpression("key"))
                .done())
        .deploy();

    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    // when
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .limit(2)
                .count())
        .isEqualTo(2);

    final PublishMessageClient client = engine.message().withName("ping").withCorrelationKey("123");

    client.withVariables(asMsgPack("nr", 1)).publish();
    client.withVariables(asMsgPack("nr", 2)).publish();

    // then
    final List<Object> correlatedValues =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .filter(r -> r.getValue().getElementId().startsWith("message"))
            .limit(2)
            .map(
                event ->
                    WorkflowInstances.getCurrentVariables(
                            event.getValue().getWorkflowInstanceKey(), event.getPosition())
                        .get("nr"))
            .collect(Collectors.toList());
    assertThat(correlatedValues).contains("1", "2");
  }

  @Test
  public void shouldCorrelateOnlyOneMessagePerCatchElement() {
    // given
    engine.deployment().withXmlResource(TWO_MESSAGES_WORKFLOW).deploy();

    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    final PublishMessageClient messageClient =
        engine.message().withName("ping").withCorrelationKey("123");

    messageClient.withVariables(asMsgPack("nr", 1)).publish();
    messageClient.withVariables(asMsgPack("nr", 2)).publish();

    // then
    final List<Object> correlatedValues =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .filter(r -> r.getValue().getElementId().startsWith("message"))
            .limit(2)
            .map(
                event ->
                    tuple(
                        event.getValue().getElementId(),
                        WorkflowInstances.getCurrentVariables(
                                event.getValue().getWorkflowInstanceKey(), event.getPosition())
                            .get("nr")))
            .collect(Collectors.toList());
    assertThat(correlatedValues).contains(tuple("message1", "1"), tuple("message2", "2"));
  }

  @Test
  public void shouldCorrelateCorrectBoundaryEvent() {
    // given
    engine.deployment().withXmlResource(BOUNDARY_EVENTS_WORKFLOW).deploy();
    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    // when
    awaitSubscriptionsOpened(3);

    engine
        .message()
        .withName("msg1")
        .withCorrelationKey("123")
        .withVariables(asMsgPack("foo", 1))
        .publish();

    // then
    assertThat(RecordingExporter.workflowInstanceRecords().limitToWorkflowInstanceCompleted())
        .filteredOn(r -> r.getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATED)
        .extracting(Record::getValue)
        .extracting(WorkflowInstanceRecordValue::getElementId)
        .contains("msg1End")
        .doesNotContain("taskEnd", "msg2End");
  }

  @Test
  public void shouldNotTriggerBoundaryEventIfReceiveTaskTriggeredFirst() {
    // given
    engine.deployment().withXmlResource(BOUNDARY_EVENTS_WORKFLOW).deploy();
    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    // when
    awaitSubscriptionsOpened(3);

    engine
        .message()
        .withName("taskMsg")
        .withCorrelationKey("123")
        .withVariables(asMsgPack("foo", 1))
        .publish();

    // then
    assertThat(RecordingExporter.workflowInstanceRecords().limitToWorkflowInstanceCompleted())
        .filteredOn(r -> r.getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATED)
        .extracting(Record::getValue)
        .extracting(WorkflowInstanceRecordValue::getElementId)
        .contains("taskEnd")
        .doesNotContain("msg1End", "msg2End");
  }

  @Test
  public void shouldNotTriggerReceiveTaskIfBoundaryEventTriggeredFirst() {
    // given
    engine.deployment().withXmlResource(BOUNDARY_EVENTS_WORKFLOW).deploy();
    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    // when
    awaitSubscriptionsOpened(3); // await both subscriptions opened

    engine
        .message()
        .withName("msg2")
        .withCorrelationKey("123")
        .withVariables(asMsgPack("foo", 1))
        .publish();

    // then
    assertThat(RecordingExporter.workflowInstanceRecords().limitToWorkflowInstanceCompleted())
        .filteredOn(r -> r.getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATED)
        .extracting(Record::getValue)
        .extracting(WorkflowInstanceRecordValue::getElementId)
        .contains("msg2End")
        .doesNotContain("taskEnd", "msg1End");
  }

  @Test
  public void testIntermediateMessageEventLifeCycle() {
    // given
    engine.deployment().withXmlResource(SINGLE_MESSAGE_WORKFLOW).deploy();

    engine.message().withName("message").withCorrelationKey("order-123").publish();

    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "order-123").create();

    final List<Record<WorkflowInstanceRecordValue>> events =
        RecordingExporter.workflowInstanceRecords()
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(events)
        .filteredOn(r -> r.getValue().getElementId().equals("receive-message"))
        .extracting(Record::getIntent)
        .containsExactly(
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.EVENT_OCCURRED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void testReceiveTaskLifeCycle() {
    // given
    engine.deployment().withXmlResource(RECEIVE_TASK_WORKFLOW).deploy();
    engine.message().withName("message").withCorrelationKey("order-123").publish();
    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "order-123").create();

    final List<Record<WorkflowInstanceRecordValue>> events =
        RecordingExporter.workflowInstanceRecords()
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(events)
        .filteredOn(r -> r.getValue().getElementId().equals("receive-message"))
        .extracting(Record::getIntent)
        .containsExactly(
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.EVENT_OCCURRED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void testBoundaryMessageEventLifecycle() {
    // given
    engine.deployment().withXmlResource(BOUNDARY_EVENTS_WORKFLOW).deploy();
    engine.message().withName("msg1").withCorrelationKey("order-123").publish();

    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "order-123").create();

    final List<Record<WorkflowInstanceRecordValue>> events =
        RecordingExporter.workflowInstanceRecords()
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(events)
        .extracting(r -> tuple(r.getValue().getElementId(), r.getIntent()))
        .containsSequence(
            tuple("task", WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple("task", WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task", WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple("msg1", WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple("msg1", WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple("msg1", WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple("msg1", WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCorrelateToNonInterruptingBoundaryEvent() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType("type"))
            .boundaryEvent("msg1")
            .cancelActivity(false)
            .message(m -> m.name("msg1").zeebeCorrelationKeyExpression("key"))
            .endEvent("msg1End")
            .moveToActivity("task")
            .endEvent("taskEnd")
            .done();
    engine.deployment().withXmlResource(workflow).deploy();
    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    // when
    final PublishMessageClient messageClient =
        engine.message().withName("msg1").withCorrelationKey("123");

    messageClient.withVariables(asMsgPack("foo", 0)).publish();
    messageClient.withVariables(asMsgPack("foo", 1)).publish();
    messageClient.withVariables(asMsgPack("foo", 2)).publish();

    assertThat(awaitMessagesCorrelated(3)).hasSize(3);

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withElementType(BpmnElementType.BOUNDARY_EVENT)
                .limit(3)
                .count())
        .isEqualTo(3);

    assertThat(RecordingExporter.variableRecords().withName("foo").limit(3))
        .extracting(r -> r.getValue().getValue())
        .containsExactly("0", "1", "2");
  }

  @Test
  public void shouldCorrelateMessageAgainAfterRejection() {
    // given
    engine.message().withName("a").withCorrelationKey("123").publish();
    engine.message().withName("b").withCorrelationKey("123").publish();

    final BpmnModelInstance twoMessages =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .eventBasedGateway("split")
            .intermediateCatchEvent(
                "element-a", c -> c.message(m -> m.name("a").zeebeCorrelationKeyExpression("key")))
            .intermediateCatchEvent(
                "element-ab", c -> c.message(m -> m.name("b").zeebeCorrelationKeyExpression("key")))
            .exclusiveGateway("merge")
            .endEvent()
            .moveToNode("split")
            .intermediateCatchEvent(
                "element-b", c -> c.message(m -> m.name("b").zeebeCorrelationKeyExpression("key")))
            .intermediateCatchEvent(
                "element-ba", c -> c.message(m -> m.name("a").zeebeCorrelationKeyExpression("key")))
            .connectTo("merge")
            .done();

    engine.deployment().withXmlResource(twoMessages).deploy();

    // when
    final var workflowInstanceKey =
        engine.workflowInstance().ofBpmnProcessId("process").withVariable("key", "123").create();

    // then
    assertThat(RecordingExporter.records().limitToWorkflowInstance(workflowInstanceKey))
        .extracting(Record::getRecordType, Record::getIntent)
        .containsSubsequence(
            tuple(RecordType.COMMAND_REJECTION, WorkflowInstanceSubscriptionIntent.CORRELATE),
            tuple(RecordType.COMMAND, MessageSubscriptionIntent.REJECT),
            tuple(RecordType.EVENT, MessageSubscriptionIntent.REJECTED),
            tuple(RecordType.COMMAND, WorkflowInstanceSubscriptionIntent.CORRELATE));

    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.CORRELATED)
                .limit(2))
        .extracting(r -> r.getValue().getMessageName())
        .containsExactlyInAnyOrder("a", "b");

    RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  @Test
  public void shouldNotCorrelateMessageAfterTTL() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("wf")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .intermediateCatchEvent(
                    "catch", c -> c.message(m -> m.name("a").zeebeCorrelationKeyExpression("key")))
                .done())
        .deploy();

    engine.workflowInstance().ofBpmnProcessId("wf").withVariable("key", "key-1").create();

    // - zero TTL
    engine
        .message()
        .withName("a")
        .withCorrelationKey("key-1")
        .withVariables(Map.of("x", 1))
        .withTimeToLive(Duration.ZERO)
        .publish();

    // - short TTL
    final var messageTtl = Duration.ofSeconds(10);

    engine
        .message()
        .withName("a")
        .withCorrelationKey("key-1")
        .withVariables(Map.of("x", 2))
        .withTimeToLive(messageTtl)
        .publish();

    // - long TTL
    engine
        .message()
        .withName("a")
        .withCorrelationKey("key-1")
        .withVariables(Map.of("x", 3))
        .withTimeToLive(messageTtl.multipliedBy(2))
        .publish();

    // when
    final var job = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    engine.getClock().addTime(messageTtl);

    engine.job().withKey(job.getKey()).complete();

    // then
    final var variable = RecordingExporter.variableRecords().withName("x").getFirst();
    Assertions.assertThat(variable.getValue()).hasValue("3");
  }

  private List<Record<WorkflowInstanceSubscriptionRecordValue>> awaitMessagesCorrelated(
      final int messagesCount) {
    return RecordingExporter.workflowInstanceSubscriptionRecords(
            WorkflowInstanceSubscriptionIntent.CORRELATED)
        .limit(messagesCount)
        .asList();
  }

  private List<Record<WorkflowInstanceSubscriptionRecordValue>> awaitSubscriptionsOpened(
      final int subscriptionsCount) {
    return RecordingExporter.workflowInstanceSubscriptionRecords()
        .withIntent(WorkflowInstanceSubscriptionIntent.OPENED)
        .limit(subscriptionsCount)
        .asList();
  }
}
