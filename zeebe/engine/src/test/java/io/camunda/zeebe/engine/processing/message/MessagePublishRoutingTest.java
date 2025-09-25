/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.scaling.InMemoryRoutingState;
import io.camunda.zeebe.engine.common.state.immutable.RoutingState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MessagePublishRoutingTest {

  public static final String INTERMEDIATE_MSG_NAME = "intermediateMsg";
  public static final String CORRELATION_KEY_VARIABLE = "correlationKey";
  private static final String PROCESS_ID = "processId";

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(2)
          // only partition one is included in MessageCorrelation
          .withInitialRoutingState(
              new InMemoryRoutingState(
                  Map.of(1, 0L, 2, 0L),
                  Set.of(1, 2),
                  // partition 2 is not included in message correlation
                  new RoutingState.MessageCorrelation.HashMod(1)));

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Before
  public void before() {
    engine
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .intermediateCatchEvent()
                .message(
                    m ->
                        m.name(INTERMEDIATE_MSG_NAME)
                            .zeebeCorrelationKeyExpression(CORRELATION_KEY_VARIABLE))
                .endEvent()
                .done())
        .deploy();
  }

  @Test
  public void shouldPublishMessageToCorrectPartition() {

    // given
    final var correlationKey = UUID.randomUUID().toString();
    createProcessInstance(correlationKey);

    // when
    engine.message().withName(INTERMEDIATE_MSG_NAME).withCorrelationKey(correlationKey).publish(1);

    // then
    assertThat(
            RecordingExporter.messageRecords(MessageIntent.PUBLISH)
                .withName(INTERMEDIATE_MSG_NAME)
                .withCorrelationKey(correlationKey)
                .findFirst())
        .isPresent();
  }

  @Test
  public void shouldRejectMessagePublishWithInvalidPartition() {
    // given
    final var correlationKey = UUID.randomUUID().toString();
    createProcessInstance(correlationKey);

    // when
    engine
        .message()
        .withName(INTERMEDIATE_MSG_NAME)
        .withCorrelationKey(correlationKey)
        .expectRejection()
        .publish(2);

    // then
    assertThat(
            RecordingExporter.messageRecords(MessageIntent.PUBLISH)
                .onlyCommandRejections()
                .withName(INTERMEDIATE_MSG_NAME)
                .withCorrelationKey(correlationKey)
                .findFirst())
        .isPresent()
        .hasValueSatisfying(
            r -> {
              assertThat(r.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
              assertThat(r.getRejectionReason())
                  .isEqualTo(
                      "The message has not been routed to the right partition."
                          + " This is probably a temporary issue, please retry in a few seconds");
            });
  }

  private void createProcessInstance(final String correlationKey) {
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withVariable(CORRELATION_KEY_VARIABLE, correlationKey);
  }
}
