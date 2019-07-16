/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message;

import static io.zeebe.protocol.record.Assertions.assertThat;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.zeebe.test.util.record.RecordingExporter.messageStartEventSubscriptionRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.engine.util.client.PublishMessageClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.DeploymentRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public class MessageStartEventTest {

  private static final String MESSAGE_NAME1 = "startMessage1";
  private static final String EVENT_ID1 = "startEventId1";

  private static final String MESSAGE_NAME2 = "startMessage2";
  private static final String EVENT_ID2 = "startEventId2";

  @Rule public EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldCorrelateMessageToStartEvent() {
    // given
    final Record<DeploymentRecordValue> deploymentRecord =
        engine.deployment().withXmlResource(createWorkflowWithOneMessageStartEvent()).deploy();
    final long workflowKey = getFirstDeployedWorkflowKey(deploymentRecord);

    // wait until subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    engine
        .message()
        .withCorrelationKey("order-123")
        .withName(MESSAGE_NAME1)
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    // then
    final Record<WorkflowInstanceRecordValue> record =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_OCCURRED).getFirst();

    assertThat(record.getValue()).hasWorkflowKey(workflowKey).hasElementId(EVENT_ID1);
  }

  @Test
  public void shouldCreateInstanceOnMessage() {
    // given
    final Record<DeploymentRecordValue> deploymentRecord =
        engine.deployment().withXmlResource(createWorkflowWithOneMessageStartEvent()).deploy();
    final long workflowKey = getFirstDeployedWorkflowKey(deploymentRecord);

    // wait until subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    engine
        .message()
        .withCorrelationKey("order-123")
        .withName(MESSAGE_NAME1)
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    // then
    final List<Record<WorkflowInstanceRecordValue>> records =
        RecordingExporter.workflowInstanceRecords().limit(5).asList();

    assertThat(records)
        .extracting(Record::getIntent)
        .containsExactly(
            WorkflowInstanceIntent.EVENT_OCCURRED, // message
            WorkflowInstanceIntent.ELEMENT_ACTIVATING, // workflow instance
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING, // start event
            WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    assertThat(records).allMatch(r -> r.getValue().getWorkflowKey() == workflowKey);

    assertThat(records.get(3).getValue()).hasElementId(EVENT_ID1);
  }

  @Test
  public void shouldMergeMessageVariables() {
    // given
    engine.deployment().withXmlResource(createWorkflowWithOneMessageStartEvent()).deploy();

    // wait until subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    engine
        .message()
        .withCorrelationKey("order-123")
        .withName(MESSAGE_NAME1)
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    // then
    assertThat(RecordingExporter.variableRecords().withName("foo").withValue("\"bar\"").exists())
        .isTrue();
  }

  @Test
  public void shouldApplyOutputMappingsOfMessageStartEvent() {
    // given
    engine
        .deployment()
        .withXmlResource(createWorkflowWithMessageStartEventOutputMapping())
        .deploy();

    // wait until subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    engine
        .message()
        .withCorrelationKey("order-123")
        .withName(MESSAGE_NAME1)
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    // then
    assertThat(
            RecordingExporter.variableRecords().withName("mappedfoo").withValue("\"bar\"").exists())
        .isTrue();
  }

  @Test
  public void shouldCreateInstancesForMultipleMessagesOfSameName() {
    // given
    final Record<DeploymentRecordValue> record =
        engine.deployment().withXmlResource(createWorkflowWithOneMessageStartEvent()).deploy();

    final long workflowKey = getFirstDeployedWorkflowKey(record);

    // wait until subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    final PublishMessageClient messageClient =
        engine.message().withName(MESSAGE_NAME1).withVariables(asMsgPack("foo", "bar"));

    messageClient.withCorrelationKey("order-123").publish();
    messageClient.withCorrelationKey("order-124").publish();

    // then

    // check if two instances are created
    final List<Record<WorkflowInstanceRecordValue>> records =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .limit(2)
            .asList();

    assertThat(records).allMatch(r -> r.getValue().getWorkflowKey() == workflowKey);

    final WorkflowInstanceRecordValue recordValue1 = records.get(0).getValue();
    final WorkflowInstanceRecordValue recordValue2 = records.get(1).getValue();

    assertThat(recordValue1.getWorkflowInstanceKey())
        .isNotEqualTo(recordValue2.getWorkflowInstanceKey());
  }

  @Test
  public void shouldCreateInstancesForDifferentMessages() {
    // given
    final Record<DeploymentRecordValue> record =
        engine.deployment().withXmlResource(createWorkflowWithTwoMessageStartEvent()).deploy();

    final long workflowKey = getFirstDeployedWorkflowKey(record);

    // check if two subscriptions are opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .limit(2)
                .count())
        .isEqualTo(2);

    // when
    final PublishMessageClient messageClient =
        engine.message().withVariables(asMsgPack("foo", "bar"));

    messageClient.withName(MESSAGE_NAME1).withCorrelationKey("order-123").publish();
    messageClient.withName(MESSAGE_NAME2).withCorrelationKey("order-124").publish();

    // then

    // check if two instances are created
    final List<Record<WorkflowInstanceRecordValue>> records =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETING)
            .withElementType(BpmnElementType.START_EVENT)
            .limit(2)
            .asList();

    assertThat(records.size()).isEqualTo(2);
    assertThat(records).allMatch(r -> r.getValue().getWorkflowKey() == workflowKey);

    assertThat(records.get(0).getValue())
        .hasElementId(EVENT_ID1); // Message 1 triggers start event 1
    assertThat(records.get(1).getValue())
        .hasElementId(EVENT_ID2); // Message 2 triggers start event 2
  }

  @Test
  public void shouldNotCreateInstanceOfOldVersion() {
    // given
    engine.deployment().withXmlResource(createWorkflowWithOneMessageStartEvent()).deploy();

    // new version
    final Record<DeploymentRecordValue> record =
        engine.deployment().withXmlResource(createWorkflowWithOneMessageStartEvent()).deploy();
    final long workflowKey2 = getFirstDeployedWorkflowKey(record);

    // wait until second subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .limit(2)
                .count())
        .isEqualTo(2);

    // when
    engine
        .message()
        .withCorrelationKey("order-123")
        .withName(MESSAGE_NAME1)
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    // then
    final List<Record<WorkflowInstanceRecordValue>> records =
        RecordingExporter.workflowInstanceRecords().limit(5).asList();

    assertThat(records.stream().map(Record::getIntent))
        .containsExactly(
            WorkflowInstanceIntent.EVENT_OCCURRED, // message
            WorkflowInstanceIntent.ELEMENT_ACTIVATING, // workflow instance
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING, // start event
            WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    assertThat(records).allMatch(r -> r.getValue().getWorkflowKey() == workflowKey2);
  }

  private static BpmnModelInstance createWorkflowWithOneMessageStartEvent() {
    return Bpmn.createExecutableProcess("processId")
        .startEvent(EVENT_ID1)
        .message(m -> m.name(MESSAGE_NAME1).id("startmsgId"))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance createWorkflowWithTwoMessageStartEvent() {
    final ProcessBuilder process = Bpmn.createExecutableProcess("processId");
    process.startEvent(EVENT_ID1).message(m -> m.name(MESSAGE_NAME1).id("startmsgId1")).endEvent();
    process.startEvent(EVENT_ID2).message(m -> m.name(MESSAGE_NAME2).id("startmsgId2")).endEvent();

    return process.done();
  }

  private static BpmnModelInstance createWorkflowWithMessageStartEventOutputMapping() {
    return Bpmn.createExecutableProcess("processId")
        .startEvent(EVENT_ID1)
        .zeebeOutput("foo", "mappedfoo")
        .message(m -> m.name(MESSAGE_NAME1).id("startmsgId"))
        .endEvent()
        .done();
  }

  private long getFirstDeployedWorkflowKey(final Record<DeploymentRecordValue> deploymentRecord) {
    return deploymentRecord.getValue().getDeployedWorkflows().get(0).getWorkflowKey();
  }
}
