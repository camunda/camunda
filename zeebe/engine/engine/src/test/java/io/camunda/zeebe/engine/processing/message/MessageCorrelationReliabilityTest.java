/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;

public final class MessageCorrelationReliabilityTest {
  @Rule public final EngineRule engine = EngineRule.multiplePartition(2);

  @Test
  public void shouldCleanupSubscriptionAfterRejection() {
    // given - a process instance on partition 1 that waits for a message published on 2
    final var processInstancePartitionId = 1;
    final var messageSubscriptionPartitionId = 2;
    final var correlationKey = "test-2";
    assertThat(
            SubscriptionUtil.getSubscriptionPartitionId(
                new UnsafeBuffer(correlationKey.getBytes(StandardCharsets.UTF_8)),
                engine.getPartitionIds().size()))
        .isEqualTo(messageSubscriptionPartitionId);

    final var messageName = "message-" + UUID.randomUUID();
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("receive-message")
            .message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key"))
            .endEvent("end")
            .done();
    engine.deployment().withXmlResource(process).deploy();

    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("process")
            .withVariable("key", correlationKey)
            .create();

    assertThat(Protocol.decodePartitionId(processInstanceKey))
        .isEqualTo(processInstancePartitionId);

    engine.deployment().withXmlResource(process).deploy();

    // given - MESSAGE_SUBSCRIPTION/CORRELATE messages from partition 2 to partition 1 are dropped
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) ->
            !(receiverPartitionId == messageSubscriptionPartitionId
                && intent == MessageSubscriptionIntent.CORRELATE));

    // when - A message is published and some time passes
    engine.message().withName(messageName).withCorrelationKey(correlationKey).publish();
    engine.increaseTime(Duration.ofMinutes(10));
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.REJECT)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // then - the message correlation is not acknowledged and at least once rejected
    final long lastPosition = getLastPosition();
    assertThat(
            RecordingExporter.records()
                .between(0, lastPosition)
                .messageSubscriptionRecords()
                .withIntent(MessageSubscriptionIntent.CORRELATE)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(messageName)
                .withPartitionId(messageSubscriptionPartitionId)
                .count())
        .isZero();
    // then - the message is correlated once to the process instance on partition 1
    assertThat(
            RecordingExporter.records()
                .between(0, lastPosition)
                .processMessageSubscriptionRecords()
                .withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                .withPartitionId(processInstancePartitionId)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(messageName)
                .count())
        .isOne();

    // then - the correlation is not acknowledged on partition 2
    assertThat(
            RecordingExporter.records()
                .between(0, lastPosition)
                .messageSubscriptionRecords()
                .withIntent(MessageSubscriptionIntent.CORRELATE)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(messageName)
                .withPartitionId(messageSubscriptionPartitionId)
                .count())
        .isZero();

    // then - Partition 1 gets another correlation after partition 2 retries
    engine.increaseTime(Duration.ofMinutes(10));

    assertThat(
            RecordingExporter.records()
                .between(0, lastPosition)
                .processMessageSubscriptionRecords()
                .withIntent(ProcessMessageSubscriptionIntent.CORRELATE)
                .withRecordType(RecordType.COMMAND)
                .withProcessInstanceKey(processInstanceKey)
                .withPartitionId(processInstancePartitionId)
                .withMessageName(messageName)
                .count())
        .isEqualTo(messageSubscriptionPartitionId);

    // then - The correlation is rejected once on partition 1
    assertThat(
            RecordingExporter.records()
                .between(0, lastPosition)
                .messageSubscriptionRecords()
                .withIntent(MessageSubscriptionIntent.REJECT)
                .withProcessInstanceKey(processInstanceKey)
                .withPartitionId(messageSubscriptionPartitionId)
                .withMessageName(messageName)
                .count())
        .isOne();
  }

  private long getLastPosition() {
    return engine.decision().ofDecisionId("noop").expectRejection().evaluate().getPosition();
  }
}
