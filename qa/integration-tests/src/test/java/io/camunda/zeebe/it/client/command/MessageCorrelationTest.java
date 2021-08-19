/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.it.util.BrokerClassRuleHelper;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class MessageCorrelationTest {

  private static final String CORRELATION_KEY_VARIABLE = "orderId";
  private static final String CATCH_EVENT_ELEMENT_ID = "catch-event";
  private static final String MESSAGE_NAME = "order canceled";

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private long processDefinitionKey;
  private String correlationValue;

  @Before
  public void init() {
    correlationValue = helper.getCorrelationValue();

    processDefinitionKey =
        CLIENT_RULE.deployProcess(
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
  public void shouldCorrelateMessage() {
    // given
    final long processInstanceKey =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .variables(Map.of(CORRELATION_KEY_VARIABLE, correlationValue))
            .send()
            .join()
            .getProcessInstanceKey();

    // when
    CLIENT_RULE
        .getClient()
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
  public void shouldCorrelateMessageWithZeroTTL() {
    // given
    final long processInstanceKey =
        CLIENT_RULE
            .getClient()
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
    CLIENT_RULE
        .getClient()
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
  public void shouldRejectMessageWithSameId() {
    // given
    CLIENT_RULE
        .getClient()
        .newPublishMessageCommand()
        .messageName(MESSAGE_NAME)
        .correlationKey(correlationValue)
        .messageId("foo")
        .send()
        .join();

    // when
    final ZeebeFuture<PublishMessageResponse> future =
        CLIENT_RULE
            .getClient()
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
  public void shouldReturnTheMessageKey() {
    // when
    final PublishMessageResponse response =
        CLIENT_RULE
            .getClient()
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
