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
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Rule;
import org.junit.Test;

/**
 * Pins that {@code elementId}, {@code rootProcessInstanceKey}, and {@code elementType} are captured
 * at subscription-open time and shipped to the message partition together with the OPEN command so
 * the wait-state transformer can populate those fields without a cross-partition lookup.
 */
public final class MessageSubscriptionWaitStateFieldsPlumbingTest {

  private static final String PROCESS_ID = "process";
  private static final String CORRELATION_VAR = "key";

  private static final BpmnModelInstance RECEIVE_TASK_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask("receive-task")
          .message(m -> m.name("order").zeebeCorrelationKeyExpression(CORRELATION_VAR))
          .endEvent()
          .done();

  private static final BpmnModelInstance INTERMEDIATE_CATCH_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("catch-event")
          .message(m -> m.name("order").zeebeCorrelationKeyExpression(CORRELATION_VAR))
          .endEvent()
          .done();

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldPropagateWaitStateFieldsForReceiveTask() {
    // given
    engine.deployment().withXmlResource(RECEIVE_TASK_PROCESS).deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(CORRELATION_VAR, "k1")
            .create();

    // then
    final var subscriptionCreated =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(subscriptionCreated.getValue().getElementId()).isEqualTo("receive-task");
    assertThat(subscriptionCreated.getValue().getRootProcessInstanceKey())
        .isEqualTo(processInstanceKey);
    assertThat(subscriptionCreated.getValue().getElementType())
        .isEqualTo(BpmnElementType.RECEIVE_TASK);
  }

  @Test
  public void shouldPropagateWaitStateFieldsForIntermediateCatchEvent() {
    // given
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(CORRELATION_VAR, "k2")
            .create();

    // then
    final var subscriptionCreated =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(subscriptionCreated.getValue().getElementId()).isEqualTo("catch-event");
    assertThat(subscriptionCreated.getValue().getRootProcessInstanceKey())
        .isEqualTo(processInstanceKey);
    assertThat(subscriptionCreated.getValue().getElementType())
        .isEqualTo(BpmnElementType.INTERMEDIATE_CATCH_EVENT);
  }
}
