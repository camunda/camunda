/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class MessageCorrelationIT {

  private static final String CORRELATION_KEY_VARIABLE = "orderId";
  private static final String CATCH_EVENT_ELEMENT_ID = "catch-event";
  private static final String MESSAGE_NAME = "order canceled";

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true);

  private CamundaClient client;
  private ZeebeResourcesHelper resourcesHelper;

  private void createClient() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(10)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @AfterEach
  void closeClient() {
    client.close();
  }

  private long processDefinitionKey;
  private String correlationValue;

  @BeforeEach
  void init() {
    createClient();
    correlationValue = Strings.newRandomValidBpmnId();

    processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .intermediateCatchEvent(CATCH_EVENT_ELEMENT_ID)
                .message(
                    c ->
                        c.name(MESSAGE_NAME)
                            .zeebeCorrelationKeyExpression(CORRELATION_KEY_VARIABLE))
                .endEvent()
                .done());
  }

  @Test
  void shouldCorrelateMessage() {
    // given
    final long processInstanceKey =
        client
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .variables(Map.of(CORRELATION_KEY_VARIABLE, correlationValue))
            .send()
            .join()
            .getProcessInstanceKey();

    // when
    client
        .newPublishMessageCommand()
        .messageName(MESSAGE_NAME)
        .correlationKey(correlationValue)
        .variables(Map.of("foo", "bar"))
        .send()
        .join();

    // then
    final Record<ProcessInstanceRecordValue> processInstanceEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(CATCH_EVENT_ELEMENT_ID)
            .getFirst();

    final Record<VariableRecordValue> variableEvent =
        RecordingExporter.variableRecords()
            .withName("foo")
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(variableEvent.getValue())
        .hasValue("\"bar\"")
        .hasScopeKey(processInstanceEvent.getValue().getProcessInstanceKey());
  }

  @Test
  void shouldCorrelateMessageWithZeroTTL() {
    // given
    final long processInstanceKey =
        client
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .variables(Map.of(CORRELATION_KEY_VARIABLE, correlationValue))
            .send()
            .join()
            .getProcessInstanceKey();

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(MESSAGE_NAME)
                .exists())
        .isTrue();

    // when
    client
        .newPublishMessageCommand()
        .messageName(MESSAGE_NAME)
        .correlationKey(correlationValue)
        .timeToLive(Duration.ZERO)
        .send()
        .join();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(CATCH_EVENT_ELEMENT_ID)
                .exists())
        .isTrue();
  }

  @Test
  void shouldRejectMessageWithSameId() {
    // given
    client
        .newPublishMessageCommand()
        .messageName(MESSAGE_NAME)
        .correlationKey(correlationValue)
        .messageId("foo")
        .send()
        .join();

    // when
    final CamundaFuture<PublishMessageResponse> future =
        client
            .newPublishMessageCommand()
            .messageName(MESSAGE_NAME)
            .correlationKey(correlationValue)
            .messageId("foo")
            .send();

    // then
    assertThatThrownBy(future::join)
        .isInstanceOf(ClientException.class)
        .hasMessageContaining(
            "Expected to publish a new message with id 'foo', but a message with that id was already published");
  }

  @Test
  void shouldReturnTheMessageKey() {
    // when
    final PublishMessageResponse response =
        client
            .newPublishMessageCommand()
            .messageName(MESSAGE_NAME)
            .correlationKey(correlationValue)
            .send()
            .join();

    // then
    final var messagePublished =
        RecordingExporter.messageRecords(MessageIntent.PUBLISHED).getFirst();
    assertThat(response.getMessageKey()).isEqualTo(messagePublished.getKey());
  }
}
