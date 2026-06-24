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
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;

public final class MessageCorrelationRejectionTest {
  @Rule public final EngineRule engine = EngineRule.multiplePartition(2);

  @Test
  public void shouldWriteNotCorrelatedEvent() {
    // given - a process instance on partition 1 that waits for a message published on 2
    final var correlationKey = "correlationKey";

    final var messageName = "message-" + UUID.randomUUID();
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("receive-message")
            .message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key"))
            .endEvent("end")
            .done();
    engine.deployment().withXmlResource(process).deploy();

    // MESSAGE_SUBSCRIPTION.CORRELATE messages from partition 2 to partition 1 is dropped
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) ->
            !(receiverPartitionId == 2 && intent == MessageSubscriptionIntent.CORRELATE));

    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("process")
            .withVariable("key", correlationKey)
            .create();

    // Wait for the process message subscription to be created
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when - A message correlate command is send and some time passes
    engine
        .messageCorrelation()
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .expectNothing()
        .correlate();
    engine.increaseTime(Duration.ofMinutes(10));
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.REJECT)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // then - Message is NOT_CORRELATED
    assertThat(
            RecordingExporter.messageCorrelationRecords(MessageCorrelationIntent.NOT_CORRELATED)
                .withName(messageName)
                .withCorrelationKey(correlationKey)
                .exists())
        .isTrue();
  }
}
