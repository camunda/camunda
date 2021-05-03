/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class MessageCatchElementTest {

  private static final int PARTITION_COUNT = 3;

  @ClassRule
  public static final EngineRule ENGINE_RULE = EngineRule.multiplePartition(PARTITION_COUNT);

  private static final String ELEMENT_ID = "receive-message";
  private static final String CORRELATION_VARIABLE = "orderId";
  private static final String MESSAGE_NAME = "order canceled";
  private static final String SEQUENCE_FLOW_ID = "to-end";
  private static final String CATCH_EVENT_PROCESS_PROCESS_ID = "catchEventProcess";
  private static final BpmnModelInstance CATCH_EVENT_PROCESS =
      Bpmn.createExecutableProcess(CATCH_EVENT_PROCESS_PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent(ELEMENT_ID)
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_VARIABLE))
          .sequenceFlowId(SEQUENCE_FLOW_ID)
          .endEvent()
          .done();
  private static final String RECEIVE_TASK_PROCESS_PROCESS_ID = "receiveTaskProcess";
  private static final BpmnModelInstance RECEIVE_TASK_PROCESS =
      Bpmn.createExecutableProcess(RECEIVE_TASK_PROCESS_PROCESS_ID)
          .startEvent()
          .receiveTask(ELEMENT_ID)
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_VARIABLE))
          .sequenceFlowId(SEQUENCE_FLOW_ID)
          .endEvent()
          .done();
  private static final String BOUNDARY_EVENT_PROCESS_PROCESS_ID = "boundaryEventProcess";
  private static final BpmnModelInstance BOUNDARY_EVENT_PROCESS =
      Bpmn.createExecutableProcess(BOUNDARY_EVENT_PROCESS_PROCESS_ID)
          .startEvent()
          .serviceTask(ELEMENT_ID, b -> b.zeebeJobType("type"))
          .boundaryEvent()
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_VARIABLE))
          .sequenceFlowId(SEQUENCE_FLOW_ID)
          .endEvent()
          .done();
  private static final String NON_INT_BOUNDARY_EVENT_PROCESS_PROCESS_ID =
      "nonIntBoundaryEventProcess";
  private static final BpmnModelInstance NON_INT_BOUNDARY_EVENT_PROCESS =
      Bpmn.createExecutableProcess(NON_INT_BOUNDARY_EVENT_PROCESS_PROCESS_ID)
          .startEvent()
          .serviceTask(ELEMENT_ID, b -> b.zeebeJobType("type"))
          .boundaryEvent("event")
          .cancelActivity(false)
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_VARIABLE))
          .sequenceFlowId(SEQUENCE_FLOW_ID)
          .endEvent()
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter public String elementType;

  @Parameter(1)
  public String bpmnProcessId;

  @Parameter(2)
  public ProcessInstanceIntent enteredState;

  @Parameter(3)
  public ProcessInstanceIntent continueState;

  @Parameter(4)
  public String continuedElementId;

  private String correlationKey;
  private long processInstanceKey;

  @Parameters(name = "{0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "intermediate message catch event",
        CATCH_EVENT_PROCESS_PROCESS_ID,
        ProcessInstanceIntent.ELEMENT_ACTIVATED,
        ProcessInstanceIntent.ELEMENT_COMPLETED,
        ELEMENT_ID
      },
      {
        "receive task",
        RECEIVE_TASK_PROCESS_PROCESS_ID,
        ProcessInstanceIntent.ELEMENT_ACTIVATED,
        ProcessInstanceIntent.ELEMENT_COMPLETED,
        ELEMENT_ID
      },
      {
        "int boundary event",
        BOUNDARY_EVENT_PROCESS_PROCESS_ID,
        ProcessInstanceIntent.ELEMENT_ACTIVATED,
        ProcessInstanceIntent.ELEMENT_TERMINATED,
        ELEMENT_ID
      },
      {
        "non int boundary event",
        NON_INT_BOUNDARY_EVENT_PROCESS_PROCESS_ID,
        ProcessInstanceIntent.ELEMENT_ACTIVATED,
        ProcessInstanceIntent.ELEMENT_COMPLETED,
        "event"
      }
    };
  }

  @BeforeClass
  public static void awaitCluster() {
    deploy(CATCH_EVENT_PROCESS);
    deploy(RECEIVE_TASK_PROCESS);
    deploy(BOUNDARY_EVENT_PROCESS);
    deploy(NON_INT_BOUNDARY_EVENT_PROCESS);
  }

  private static void deploy(final BpmnModelInstance modelInstance) {
    ENGINE_RULE.deployment().withXmlResource(modelInstance).deploy();
  }

  @Before
  public void init() {
    correlationKey = UUID.randomUUID().toString();
    processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId(bpmnProcessId)
            .withVariable("orderId", correlationKey)
            .create();
  }

  @Test
  public void shouldOpenMessageSubscription() {
    final Record<ProcessInstanceRecordValue> catchEventEntered =
        getFirstElementRecord(enteredState);

    final Record<MessageSubscriptionRecordValue> messageSubscription =
        getFirstMessageSubscriptionRecord(MessageSubscriptionIntent.CREATED);

    assertThat(messageSubscription.getValueType()).isEqualTo(ValueType.MESSAGE_SUBSCRIPTION);
    assertThat(messageSubscription.getRecordType()).isEqualTo(RecordType.EVENT);

    Assertions.assertThat(messageSubscription.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName("order canceled")
        .hasCorrelationKey(correlationKey);
  }

  @Test
  public void shouldOpenProcessMessageSubscription() {
    final var subscriptionCreating =
        getFirstProcessMessageSubscriptionRecord(ProcessMessageSubscriptionIntent.CREATING);

    final Record<ProcessInstanceRecordValue> catchEventEntered =
        getFirstElementRecord(enteredState);

    final Record<ProcessMessageSubscriptionRecordValue> processMessageSubscription =
        getFirstProcessMessageSubscriptionRecord(ProcessMessageSubscriptionIntent.CREATED);

    Assertions.assertThat(processMessageSubscription)
        .hasValueType(ValueType.PROCESS_MESSAGE_SUBSCRIPTION)
        .hasRecordType(RecordType.EVENT)
        .hasKey(subscriptionCreating.getKey());

    Assertions.assertThat(processMessageSubscription.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName("order canceled");

    assertThat(processMessageSubscription.getValue().getVariables()).isEmpty();
  }

  @Test
  public void shouldCorrelateProcessMessageSubscription() {
    // given
    final Record<ProcessInstanceRecordValue> catchEventEntered =
        getFirstElementRecord(enteredState);

    // when
    ENGINE_RULE
        .message()
        .withCorrelationKey(correlationKey)
        .withName(MESSAGE_NAME)
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    // then
    final Record<ProcessMessageSubscriptionRecordValue> subscription =
        getFirstProcessMessageSubscriptionRecord(ProcessMessageSubscriptionIntent.CORRELATED);

    assertThat(subscription.getValueType()).isEqualTo(ValueType.PROCESS_MESSAGE_SUBSCRIPTION);
    assertThat(subscription.getRecordType()).isEqualTo(RecordType.EVENT);

    Assertions.assertThat(subscription.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName("order canceled");

    assertThat(subscription.getValue().getVariables()).containsExactly(entry("foo", "bar"));
  }

  @Test
  public void shouldCorrelateMessageSubscription() {
    // given
    final Record<ProcessInstanceRecordValue> catchEventEntered =
        getFirstElementRecord(enteredState);

    getFirstMessageSubscriptionRecord(MessageSubscriptionIntent.CREATED);

    // when
    final var messagePublished =
        ENGINE_RULE
            .message()
            .withCorrelationKey(correlationKey)
            .withName(MESSAGE_NAME)
            .withVariables(asMsgPack("foo", "bar"))
            .publish();

    // then
    final var subscriptionCorrelating =
        getFirstMessageSubscriptionRecord(MessageSubscriptionIntent.CORRELATING);

    Assertions.assertThat(subscriptionCorrelating.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName(MESSAGE_NAME)
        .hasCorrelationKey(correlationKey)
        .hasBpmnProcessId(bpmnProcessId)
        .hasMessageKey(messagePublished.getKey());

    final Record<MessageSubscriptionRecordValue> subscriptionCorrelated =
        getFirstMessageSubscriptionRecord(MessageSubscriptionIntent.CORRELATED);

    Assertions.assertThat(subscriptionCorrelated.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName(MESSAGE_NAME)
        .hasCorrelationKey(correlationKey);
  }

  @Test
  public void shouldCloseMessageSubscription() {
    // given
    final var subscriptionCreated =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE_RULE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    final Record<MessageSubscriptionRecordValue> messageSubscription =
        getFirstMessageSubscriptionRecord(MessageSubscriptionIntent.DELETED);

    Assertions.assertThat(messageSubscription)
        .hasRecordType(RecordType.EVENT)
        .hasKey(subscriptionCreated.getKey());

    Assertions.assertThat(messageSubscription.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementInstanceKey(subscriptionCreated.getValue().getElementInstanceKey())
        .hasMessageName(MESSAGE_NAME)
        .hasCorrelationKey(subscriptionCreated.getValue().getCorrelationKey());
  }

  @Test
  public void shouldCloseProcessMessageSubscription() {
    // given
    final var subscriptionCreated =
        getFirstProcessMessageSubscriptionRecord(ProcessMessageSubscriptionIntent.CREATED);

    final Record<ProcessInstanceRecordValue> catchEventEntered =
        getFirstElementRecord(enteredState);

    // when
    ENGINE_RULE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords()
                .onlyEvents()
                .limit(r -> r.getIntent() == ProcessMessageSubscriptionIntent.DELETED)
                .withMessageName(MESSAGE_NAME)
                .withProcessInstanceKey(processInstanceKey)
                .withElementInstanceKey(catchEventEntered.getKey())
                .map(Record::getIntent))
        .as("the lifecycle of the subscription should end with DELETING and DELETED on close")
        .containsSubsequence(
            ProcessMessageSubscriptionIntent.DELETING, ProcessMessageSubscriptionIntent.DELETED);

    final var subscriptionDeleted =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.DELETED)
            .getFirst();
    Assertions.assertThat(subscriptionDeleted).hasKey(subscriptionCreated.getKey());
  }

  @Test
  public void shouldCorrelateMessageAndContinue() {
    // given
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName(MESSAGE_NAME)
        .await();

    // when
    ENGINE_RULE.message().withCorrelationKey(correlationKey).withName(MESSAGE_NAME).publish();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(continueState)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(continuedElementId)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(SEQUENCE_FLOW_ID)
                .exists())
        .isTrue();
  }

  @Test
  public void testMessageSubscriptionLifecycle() {
    // given
    getFirstMessageSubscriptionRecord(MessageSubscriptionIntent.CREATED);

    // when
    ENGINE_RULE
        .message()
        .withCorrelationKey(correlationKey)
        .withName(MESSAGE_NAME)
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(MESSAGE_NAME)
                .limit(5))
        .extracting(Record::getRecordType, Record::getIntent)
        .containsExactly(
            tuple(RecordType.COMMAND, MessageSubscriptionIntent.CREATE),
            tuple(RecordType.EVENT, MessageSubscriptionIntent.CREATED),
            tuple(RecordType.EVENT, MessageSubscriptionIntent.CORRELATING),
            tuple(RecordType.COMMAND, MessageSubscriptionIntent.CORRELATE),
            tuple(RecordType.EVENT, MessageSubscriptionIntent.CORRELATED));
  }

  @Test
  public void testProcessMessageSubscriptionLifecycle() {
    // given
    getFirstProcessMessageSubscriptionRecord(ProcessMessageSubscriptionIntent.CREATED);

    // when
    ENGINE_RULE.message().withCorrelationKey(correlationKey).withName(MESSAGE_NAME).publish();

    // then
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(MESSAGE_NAME)
                .limit(5))
        .extracting(Record::getRecordType, Record::getIntent)
        .containsExactly(
            tuple(RecordType.EVENT, ProcessMessageSubscriptionIntent.CREATING),
            tuple(RecordType.COMMAND, ProcessMessageSubscriptionIntent.CREATE),
            tuple(RecordType.EVENT, ProcessMessageSubscriptionIntent.CREATED),
            tuple(RecordType.COMMAND, ProcessMessageSubscriptionIntent.CORRELATE),
            tuple(RecordType.EVENT, ProcessMessageSubscriptionIntent.CORRELATED));
  }

  @Test
  public void shouldHaveSameMessageSubscriptionKey() {
    // given
    final var messageSubscriptionKey =
        getFirstMessageSubscriptionRecord(MessageSubscriptionIntent.CREATED).getKey();

    // when
    ENGINE_RULE.message().withCorrelationKey(correlationKey).withName(MESSAGE_NAME).publish();

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(MESSAGE_NAME)
                .limit(5))
        .extracting(Record::getIntent, Record::getKey)
        .containsExactly(
            tuple(MessageSubscriptionIntent.CREATE, -1L),
            tuple(MessageSubscriptionIntent.CREATED, messageSubscriptionKey),
            tuple(MessageSubscriptionIntent.CORRELATING, messageSubscriptionKey),
            tuple(MessageSubscriptionIntent.CORRELATE, -1L),
            tuple(MessageSubscriptionIntent.CORRELATED, messageSubscriptionKey));
  }

  @Test
  public void shouldHaveSameProcessMessageSubscriptionKey() {
    // given
    final var subscriptionKey =
        getFirstProcessMessageSubscriptionRecord(ProcessMessageSubscriptionIntent.CREATING)
            .getKey();

    // when
    ENGINE_RULE.message().withCorrelationKey(correlationKey).withName(MESSAGE_NAME).publish();

    // then
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(MESSAGE_NAME)
                .filter(
                    r ->
                        r.getIntent() != ProcessMessageSubscriptionIntent.CREATE
                            && r.getIntent() != ProcessMessageSubscriptionIntent.CREATED)
                .limit(3))
        .extracting(Record::getIntent, Record::getKey)
        .containsExactly(
            tuple(ProcessMessageSubscriptionIntent.CREATING, subscriptionKey),
            tuple(ProcessMessageSubscriptionIntent.CORRELATE, -1L),
            tuple(ProcessMessageSubscriptionIntent.CORRELATED, subscriptionKey));
  }

  private Record<ProcessInstanceRecordValue> getFirstElementRecord(
      final ProcessInstanceIntent intent) {
    return RecordingExporter.processInstanceRecords(intent)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(ELEMENT_ID)
        .getFirst();
  }

  private Record<MessageSubscriptionRecordValue> getFirstMessageSubscriptionRecord(
      final MessageSubscriptionIntent intent) {
    return RecordingExporter.messageSubscriptionRecords(intent)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName(MESSAGE_NAME)
        .getFirst();
  }

  private Record<ProcessMessageSubscriptionRecordValue> getFirstProcessMessageSubscriptionRecord(
      final ProcessMessageSubscriptionIntent intent) {
    return RecordingExporter.processMessageSubscriptionRecords(intent)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName(MESSAGE_NAME)
        .getFirst();
  }
}
