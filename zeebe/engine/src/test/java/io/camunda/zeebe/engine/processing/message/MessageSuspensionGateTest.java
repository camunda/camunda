/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.SuspensionState.State;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.MessageCorrelationRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class MessageSuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldReturnNotFoundWhileSuspended() {
    // given - an instance waiting on an intermediate message catch event, then suspended.
    // Suspension tears down the message-side subscription so no active subscriber exists.
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    final long processInstanceKey =
        deployAndStartProcessWithMessageCatchEvent(processId, messageName, correlationKey);
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<MessageCorrelationRecordValue> rejection =
        ENGINE
            .messageCorrelation()
            .withName(messageName)
            .withCorrelationKey(correlationKey)
            .expectRejection()
            .correlate();

    // then - NOT_FOUND because the message-side subscription was removed on suspend;
    // subscriptions are re-created only on resume (COMPLETE_RESUMING)
    Assertions.assertThat(rejection)
        .hasIntent(MessageCorrelationIntent.CORRELATE)
        .hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldReturnNotFoundWhileResuming() {
    // given - suspend an instance (tears down the message-side subscription), then artificially
    // seed RESUMING to simulate the mid-resume window before COMPLETE_RESUMING re-opens it.
    // During that window there is no active message-side subscriber.
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    final long processInstanceKey =
        deployAndStartProcessWithMessageCatchEvent(processId, messageName, correlationKey);
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    // Wait for the message-side subscription to be deleted before seeding RESUMING
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ((MutableProcessingState) ENGINE.getProcessingState())
        .getSuspensionState()
        .setSuspensionState(processInstanceKey, State.RESUMING);

    // when
    final Record<MessageCorrelationRecordValue> rejection =
        ENGINE
            .messageCorrelation()
            .withName(messageName)
            .withCorrelationKey(correlationKey)
            .expectRejection()
            .correlate();

    // then - NOT_FOUND: subscriptions are not yet re-created during the RESUMING window
    Assertions.assertThat(rejection)
        .hasIntent(MessageCorrelationIntent.CORRELATE)
        .hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldCorrelateMessageWhileNotSuspended() {
    // given - an instance waiting on an intermediate message catch event, not suspended
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    final long processInstanceKey =
        deployAndStartProcessWithMessageCatchEvent(processId, messageName, correlationKey);
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .messageCorrelation()
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .correlate();

    // then - correlation proceeds normally
    final Record<MessageCorrelationRecordValue> correlated =
        RecordingExporter.messageCorrelationRecords(MessageCorrelationIntent.CORRELATED)
            .withCorrelationKey(correlationKey)
            .getFirst();
    Assertions.assertThat(correlated.getValue()).hasCorrelationKey(correlationKey);
  }

  @Test
  public void shouldCorrelateToActiveTargetWhenAnotherTargetSuspended() {
    // given - two instances of different processes waiting on the same message, one suspended
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    final long suspendedInstanceKey =
        deployAndStartProcessWithMessageCatchEvent(
            Strings.newRandomValidBpmnId(), messageName, correlationKey);
    final long activeInstanceKey =
        deployAndStartProcessWithMessageCatchEvent(
            Strings.newRandomValidBpmnId(), messageName, correlationKey);
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(suspendedInstanceKey)
        .await();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(activeInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(suspendedInstanceKey).suspend();

    // when
    ENGINE
        .messageCorrelation()
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .correlate();

    // then - the active instance receives the message and completes, while the suspended instance
    // is skipped, so only the active instance correlates and the command is not rejected
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(activeInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATED)
                .withMessageName(messageName)
                .limit(1))
        .extracting(r -> r.getValue().getProcessInstanceKey())
        .containsExactly(activeInstanceKey);
  }

  @Test
  public void shouldCorrelateToSingleActiveTargetAmongMultipleSuspendedInstances() {
    // given - three suspended instances and one active, all subscribed to the same message
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    final long suspendedKey1 =
        deployAndStartProcessWithMessageCatchEvent(
            Strings.newRandomValidBpmnId(), messageName, correlationKey);
    final long suspendedKey2 =
        deployAndStartProcessWithMessageCatchEvent(
            Strings.newRandomValidBpmnId(), messageName, correlationKey);
    final long suspendedKey3 =
        deployAndStartProcessWithMessageCatchEvent(
            Strings.newRandomValidBpmnId(), messageName, correlationKey);
    final long activeKey =
        deployAndStartProcessWithMessageCatchEvent(
            Strings.newRandomValidBpmnId(), messageName, correlationKey);
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(suspendedKey1)
        .await();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(suspendedKey2)
        .await();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(suspendedKey3)
        .await();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(activeKey)
        .await();
    ENGINE.processInstance().withInstanceKey(suspendedKey1).suspend();
    ENGINE.processInstance().withInstanceKey(suspendedKey2).suspend();
    ENGINE.processInstance().withInstanceKey(suspendedKey3).suspend();

    // when
    ENGINE
        .messageCorrelation()
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .correlate();

    // then - only the active instance receives the message; the three suspended ones are skipped
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(activeKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATED)
                .withMessageName(messageName)
                .limit(1))
        .extracting(r -> r.getValue().getProcessInstanceKey())
        .containsExactly(activeKey);
  }

  @Test
  public void shouldCorrelateMessageAfterResume() {
    // given - suspend an instance, then resume it; subscriptions are re-created on resume
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    final long processInstanceKey =
        deployAndStartProcessWithMessageCatchEvent(processId, messageName, correlationKey);
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();
    // Wait for the message-side subscription to be re-created by reopenMessageSubscriptions
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when - send a fresh message after resume
    ENGINE
        .messageCorrelation()
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .correlate();

    // then - the resumed instance correlates and completes normally
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  @Test
  public void shouldPickUpMessageWithTTLOnResume() {
    // given - suspend the instance (removing message-side subscription), publish a message while
    // suspended so it buffers, then resume; reopenMessageSubscriptions re-creates the subscription
    // and MessageSubscriptionCreateProcessor calls correlateNextMessage immediately on CREATE.
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    final long processInstanceKey =
        deployAndStartProcessWithMessageCatchEvent(processId, messageName, correlationKey);
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    // Ensure the message-side subscription is fully deleted before publishing
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // publish while suspended: no active subscriber → message buffers with a 1-minute TTL
    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofMinutes(1))
        .publish();

    // when - resume; subscription is re-created and correlateNextMessage picks up the buffer
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - the buffered message correlates automatically and the instance completes
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  private long deployAndStartProcessWithMessageCatchEvent(
      final String processId, final String messageName, final String correlationKey) {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(
                    "msg",
                    e ->
                        e.message(
                            m ->
                                m.name(messageName)
                                    .zeebeCorrelationKey("=\"%s\"".formatted(correlationKey))))
                .endEvent()
                .done())
        .deploy();
    return ENGINE.processInstance().ofBpmnProcessId(processId).create();
  }
}
