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
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
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
  private static final String CATCH_EVENT_WORKFLOW_PROCESS_ID = "catchEventWorkflow";
  private static final BpmnModelInstance CATCH_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(CATCH_EVENT_WORKFLOW_PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent(ELEMENT_ID)
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_VARIABLE))
          .sequenceFlowId(SEQUENCE_FLOW_ID)
          .endEvent()
          .done();
  private static final String RECEIVE_TASK_WORKFLOW_PROCESS_ID = "receiveTaskWorkflow";
  private static final BpmnModelInstance RECEIVE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess(RECEIVE_TASK_WORKFLOW_PROCESS_ID)
          .startEvent()
          .receiveTask(ELEMENT_ID)
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_VARIABLE))
          .sequenceFlowId(SEQUENCE_FLOW_ID)
          .endEvent()
          .done();
  private static final String BOUNDARY_EVENT_WORKFLOW_PROCESS_ID = "boundaryEventWorkflow";
  private static final BpmnModelInstance BOUNDARY_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(BOUNDARY_EVENT_WORKFLOW_PROCESS_ID)
          .startEvent()
          .serviceTask(ELEMENT_ID, b -> b.zeebeJobType("type"))
          .boundaryEvent()
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_VARIABLE))
          .sequenceFlowId(SEQUENCE_FLOW_ID)
          .endEvent()
          .done();
  private static final String NON_INT_BOUNDARY_EVENT_WORKFLOW_PROCESS_ID =
      "nonIntBoundaryEventWorkflow";
  private static final BpmnModelInstance NON_INT_BOUNDARY_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(NON_INT_BOUNDARY_EVENT_WORKFLOW_PROCESS_ID)
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
  public WorkflowInstanceIntent enteredState;

  @Parameter(3)
  public WorkflowInstanceIntent continueState;

  @Parameter(4)
  public String continuedElementId;

  private String correlationKey;
  private long workflowInstanceKey;

  @Parameters(name = "{0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "intermediate message catch event",
        CATCH_EVENT_WORKFLOW_PROCESS_ID,
        WorkflowInstanceIntent.ELEMENT_ACTIVATED,
        WorkflowInstanceIntent.ELEMENT_COMPLETED,
        ELEMENT_ID
      },
      {
        "receive task",
        RECEIVE_TASK_WORKFLOW_PROCESS_ID,
        WorkflowInstanceIntent.ELEMENT_ACTIVATED,
        WorkflowInstanceIntent.ELEMENT_COMPLETED,
        ELEMENT_ID
      },
      {
        "int boundary event",
        BOUNDARY_EVENT_WORKFLOW_PROCESS_ID,
        WorkflowInstanceIntent.ELEMENT_ACTIVATED,
        WorkflowInstanceIntent.ELEMENT_TERMINATED,
        ELEMENT_ID
      },
      {
        "non int boundary event",
        NON_INT_BOUNDARY_EVENT_WORKFLOW_PROCESS_ID,
        WorkflowInstanceIntent.ELEMENT_ACTIVATED,
        WorkflowInstanceIntent.ELEMENT_COMPLETED,
        "event"
      }
    };
  }

  @BeforeClass
  public static void awaitCluster() {
    deploy(CATCH_EVENT_WORKFLOW);
    deploy(RECEIVE_TASK_WORKFLOW);
    deploy(BOUNDARY_EVENT_WORKFLOW);
    deploy(NON_INT_BOUNDARY_EVENT_WORKFLOW);
  }

  private static void deploy(final BpmnModelInstance modelInstance) {
    ENGINE_RULE.deployment().withXmlResource(modelInstance).deploy();
  }

  @Before
  public void init() {
    correlationKey = UUID.randomUUID().toString();
    workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId(bpmnProcessId)
            .withVariable("orderId", correlationKey)
            .create();
  }

  @Test
  public void shouldOpenMessageSubscription() {
    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        getFirstElementRecord(enteredState);

    final Record<MessageSubscriptionRecordValue> messageSubscription =
        getFirstMessageSubscriptionRecord(MessageSubscriptionIntent.CREATED);

    assertThat(messageSubscription.getValueType()).isEqualTo(ValueType.MESSAGE_SUBSCRIPTION);
    assertThat(messageSubscription.getRecordType()).isEqualTo(RecordType.EVENT);

    Assertions.assertThat(messageSubscription.getValue())
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName("order canceled")
        .hasCorrelationKey(correlationKey);
  }

  @Test
  public void shouldOpenWorkflowInstanceSubscription() {
    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        getFirstElementRecord(enteredState);

    final Record<WorkflowInstanceSubscriptionRecordValue> workflowInstanceSubscription =
        getFirstWorkflowInstanceSubscriptionRecord(WorkflowInstanceSubscriptionIntent.OPENED);

    assertThat(workflowInstanceSubscription.getValueType())
        .isEqualTo(ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION);
    assertThat(workflowInstanceSubscription.getRecordType()).isEqualTo(RecordType.EVENT);

    Assertions.assertThat(workflowInstanceSubscription.getValue())
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName("order canceled");

    assertThat(workflowInstanceSubscription.getValue().getVariables()).isEmpty();
  }

  @Test
  public void shouldCorrelateWorkflowInstanceSubscription() {
    // given
    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        getFirstElementRecord(enteredState);

    // when
    ENGINE_RULE
        .message()
        .withCorrelationKey(correlationKey)
        .withName(MESSAGE_NAME)
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    // then
    final Record<WorkflowInstanceSubscriptionRecordValue> subscription =
        getFirstWorkflowInstanceSubscriptionRecord(WorkflowInstanceSubscriptionIntent.CORRELATED);

    assertThat(subscription.getValueType()).isEqualTo(ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION);
    assertThat(subscription.getRecordType()).isEqualTo(RecordType.EVENT);

    Assertions.assertThat(subscription.getValue())
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName("order canceled");

    assertThat(subscription.getValue().getVariables()).containsExactly(entry("foo", "bar"));
  }

  @Test
  public void shouldCorrelateMessageSubscription() {
    // given
    final Record<WorkflowInstanceRecordValue> catchEventEntered =
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
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName(MESSAGE_NAME)
        .hasCorrelationKey(correlationKey)
        .hasBpmnProcessId(bpmnProcessId)
        .hasMessageKey(messagePublished.getKey());

    final Record<MessageSubscriptionRecordValue> subscriptionCorrelated =
        getFirstMessageSubscriptionRecord(MessageSubscriptionIntent.CORRELATED);

    Assertions.assertThat(subscriptionCorrelated.getValue())
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName(MESSAGE_NAME)
        .hasCorrelationKey(correlationKey);
  }

  @Test
  public void shouldCloseMessageSubscription() {
    // given
    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        getFirstElementRecord(enteredState);

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .await();

    // when
    ENGINE_RULE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    final Record<MessageSubscriptionRecordValue> messageSubscription =
        getFirstMessageSubscriptionRecord(MessageSubscriptionIntent.CLOSED);

    assertThat(messageSubscription.getRecordType()).isEqualTo(RecordType.EVENT);

    Assertions.assertThat(messageSubscription.getValue())
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName(MESSAGE_NAME)
        .hasCorrelationKey("");
  }

  @Test
  public void shouldCloseWorkflowInstanceSubscription() {
    // given
    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        getFirstElementRecord(enteredState);

    // when
    ENGINE_RULE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    final Record<WorkflowInstanceSubscriptionRecordValue> subscription =
        getFirstWorkflowInstanceSubscriptionRecord(WorkflowInstanceSubscriptionIntent.CLOSED);

    assertThat(subscription.getRecordType()).isEqualTo(RecordType.EVENT);

    Assertions.assertThat(subscription.getValue())
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName(MESSAGE_NAME);
  }

  @Test
  public void shouldCorrelateMessageAndContinue() {
    // given
    RecordingExporter.workflowInstanceSubscriptionRecords(WorkflowInstanceSubscriptionIntent.OPENED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withMessageName(MESSAGE_NAME)
        .await();

    // when
    ENGINE_RULE.message().withCorrelationKey(correlationKey).withName(MESSAGE_NAME).publish();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(continueState)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId(continuedElementId)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
                .withWorkflowInstanceKey(workflowInstanceKey)
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
                .withWorkflowInstanceKey(workflowInstanceKey)
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

  private Record<WorkflowInstanceRecordValue> getFirstElementRecord(
      final WorkflowInstanceIntent intent) {
    return RecordingExporter.workflowInstanceRecords(intent)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementId(ELEMENT_ID)
        .getFirst();
  }

  private Record<MessageSubscriptionRecordValue> getFirstMessageSubscriptionRecord(
      final MessageSubscriptionIntent intent) {
    return RecordingExporter.messageSubscriptionRecords(intent)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withMessageName(MESSAGE_NAME)
        .getFirst();
  }

  private Record<WorkflowInstanceSubscriptionRecordValue>
      getFirstWorkflowInstanceSubscriptionRecord(final WorkflowInstanceSubscriptionIntent intent) {
    return RecordingExporter.workflowInstanceSubscriptionRecords(intent)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withMessageName(MESSAGE_NAME)
        .getFirst();
  }
}
