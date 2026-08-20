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
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Rule;
import org.junit.Test;

/**
 * Pins that the {@code businessId} carried by a message is recorded on the {@code
 * MessageStartEventSubscription:CORRELATED} event when the message correlates to a start event
 * locally (single partition, so {@code P_B == P_K} and the start is triggered through {@link
 * io.camunda.zeebe.engine.processing.common.EventHandle#triggerMessageStartEvent}). The recorded
 * value is what the secondary-storage read path later exposes for start-event correlations.
 */
public final class MessageStartEventCorrelatedBusinessIdTest {

  private static final String PROCESS_ID = "wf";
  private static final String MESSAGE_NAME = "start-msg";

  private static final BpmnModelInstance MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .message(MESSAGE_NAME)
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldRecordBusinessIdOnStartEventCorrelation() {
    // given
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();

    // when
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("")
        .withBusinessId("biz-1")
        .publish();

    // then
    final var correlated =
        RecordingExporter.messageStartEventSubscriptionRecords(
                MessageStartEventSubscriptionIntent.CORRELATED)
            .withMessageName(MESSAGE_NAME)
            .getFirst();
    assertThat(correlated.getValue().getBusinessId()).isEqualTo("biz-1");
  }

  @Test
  public void shouldRecordEmptyBusinessIdWhenMessageHasNone() {
    // given
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();

    // when
    engine.message().withName(MESSAGE_NAME).withCorrelationKey("").publish();

    // then
    final var correlated =
        RecordingExporter.messageStartEventSubscriptionRecords(
                MessageStartEventSubscriptionIntent.CORRELATED)
            .withMessageName(MESSAGE_NAME)
            .getFirst();
    assertThat(correlated.getValue().getBusinessId()).isEmpty();
  }
}
