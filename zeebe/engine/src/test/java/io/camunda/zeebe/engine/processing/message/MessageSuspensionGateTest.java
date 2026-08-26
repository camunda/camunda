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
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.sbe.RejectionType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.MessageCorrelationRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class MessageSuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectMessageCorrelateWhileSuspended() {
    // given - an instance waiting on an intermediate message catch event, then suspended
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

    // then - the correlate command is rejected, referencing the suspended instance
    Assertions.assertThat(rejection)
        .hasIntent(MessageCorrelationIntent.CORRELATE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectMessageCorrelateWhileResuming() {
    // given - an instance waiting on a message catch event, with the RESUMING marker seeded
    // directly to isolate the gate from the drain that puts the instance into that state
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    final long processInstanceKey =
        deployAndStartProcessWithMessageCatchEvent(processId, messageName, correlationKey);
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
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

    // then - the correlate command is rejected just like while SUSPENDED
    Assertions.assertThat(rejection)
        .hasIntent(MessageCorrelationIntent.CORRELATE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
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
