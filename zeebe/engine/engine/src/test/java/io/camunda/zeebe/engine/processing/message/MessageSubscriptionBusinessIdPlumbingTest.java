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
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Rule;
import org.junit.Test;

/**
 * Pins that the process instance's {@code businessId} is captured at message-subscription open time
 * and shipped to the message partition together with the OPEN command, so the message partition
 * holds it locally on the {@code MessageSubscription} record. This is a precondition for the
 * post-routing local filter implemented in the next increment: reading the PI's {@code businessId}
 * from a different partition at correlation time would reintroduce the cross-partition chatter the
 * design explicitly avoids.
 *
 * <p>This test does not exercise any correlation-time filtering — it only validates the plumbing of
 * the field from the PI partition through to {@code P_K}.
 */
public final class MessageSubscriptionBusinessIdPlumbingTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance INTERMEDIATE_CATCH_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
          .endEvent()
          .done();

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldPropagateBusinessIdFromProcessInstanceToMessageSubscription() {
    // given
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withBusinessId("biz-42")
            .withVariable("key", "order-1")
            .create();

    // then
    final var processSubscriptionCreated =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(processSubscriptionCreated.getValue().getBusinessId()).isEqualTo("biz-42");

    final var subscriptionCreated =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(subscriptionCreated.getValue().getBusinessId()).isEqualTo("biz-42");
  }

  @Test
  public void shouldStoreEmptyBusinessIdWhenProcessInstanceHasNone() {
    // given
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-2")
            .create();

    // then
    final var subscriptionCreated =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(subscriptionCreated.getValue().getBusinessId()).isEmpty();
  }
}
