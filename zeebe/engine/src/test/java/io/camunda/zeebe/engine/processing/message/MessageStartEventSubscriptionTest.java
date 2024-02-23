/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public final class MessageStartEventSubscriptionTest {

  private static final String MESSAGE_NAME1 = "startMessage1";
  private static final String EVENT_ID1 = "startEventId1";

  private static final String MESSAGE_NAME2 = "startMessage2";
  private static final String EVENT_ID2 = "startEventId2";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldOpenMessageSubscriptionOnDeployment() {

    // when
    engine.deployment().withXmlResource(createProcessWithOneMessageStartEvent()).deploy();

    final Record<MessageStartEventSubscriptionRecordValue> subscription =
        RecordingExporter.messageStartEventSubscriptionRecords(
                MessageStartEventSubscriptionIntent.CREATED)
            .getFirst();

    // then
    assertThat(subscription.getValue().getStartEventId()).isEqualTo(EVENT_ID1);
    assertThat(subscription.getValue().getMessageName()).isEqualTo(MESSAGE_NAME1);
  }

  @Test
  public void shouldOpenSubscriptionsForAllMessageStartEvents() {

    // when
    engine.deployment().withXmlResource(createProcessWithTwoMessageStartEvent()).deploy();

    final List<Record<MessageStartEventSubscriptionRecordValue>> subscriptions =
        RecordingExporter.messageStartEventSubscriptionRecords(
                MessageStartEventSubscriptionIntent.CREATED)
            .limit(2)
            .asList();

    // then
    assertThat(subscriptions.size()).isEqualTo(2);

    assertThat(subscriptions)
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(s -> tuple(s.getMessageName(), s.getStartEventId()))
        .containsExactlyInAnyOrder(
            tuple(MESSAGE_NAME1, EVENT_ID1), tuple(MESSAGE_NAME2, EVENT_ID2));
  }

  @Test
  public void shouldDeleteSubscriptionForOldVersions() {
    // given
    engine.deployment().withXmlResource(createProcessWithOneMessageStartEvent()).deploy();

    // when
    engine.deployment().withXmlResource(createProcessWithOneMessageStartEvent()).deploy();
    // then

    final List<Record<MessageStartEventSubscriptionRecordValue>> subscriptions =
        RecordingExporter.messageStartEventSubscriptionRecords().limit(3).asList();

    final List<Intent> intents =
        subscriptions.stream().map(Record::getIntent).collect(Collectors.toList());

    assertThat(intents)
        .containsExactly(
            MessageStartEventSubscriptionIntent.CREATED,
            MessageStartEventSubscriptionIntent.DELETED,
            MessageStartEventSubscriptionIntent.CREATED);

    final long closingProcessDefinitionKey =
        subscriptions.get(1).getValue().getProcessDefinitionKey();
    assertThat(closingProcessDefinitionKey)
        .isEqualTo(subscriptions.get(0).getValue().getProcessDefinitionKey());
  }

  @Test
  public void shouldDeleteSubscriptionsForAllMessageStartEvents() {
    // given
    engine.deployment().withXmlResource(createProcessWithTwoMessageStartEvent()).deploy();

    final var processDefinitionKey = RecordingExporter.processRecords().getFirst().getKey();

    // when
    engine.deployment().withXmlResource(createProcessWithTwoMessageStartEvent()).deploy();

    // then
    assertThat(
            RecordingExporter.messageStartEventSubscriptionRecords(
                    MessageStartEventSubscriptionIntent.DELETED)
                .limit(2))
        .extracting(r -> r.getValue().getProcessDefinitionKey(), r -> r.getValue().getMessageName())
        .contains(
            tuple(processDefinitionKey, MESSAGE_NAME1), tuple(processDefinitionKey, MESSAGE_NAME2));
  }

  @Test
  public void testLifecycle() {
    // given
    engine.deployment().withXmlResource(createProcessWithOneMessageStartEvent()).deploy();

    engine.message().withName(MESSAGE_NAME1).withCorrelationKey("key-1").publish();

    // when
    engine.deployment().withXmlResource(createProcessWithOneMessageStartEvent()).deploy();

    // then
    assertThat(RecordingExporter.messageStartEventSubscriptionRecords().limit(3))
        .extracting(Record::getRecordType, Record::getIntent)
        .containsExactly(
            tuple(RecordType.EVENT, MessageStartEventSubscriptionIntent.CREATED),
            tuple(RecordType.EVENT, MessageStartEventSubscriptionIntent.CORRELATED),
            tuple(RecordType.EVENT, MessageStartEventSubscriptionIntent.DELETED));
  }

  @Test
  public void shouldHaveSameSubscriptionKey() {
    // given
    engine.deployment().withXmlResource(createProcessWithOneMessageStartEvent()).deploy();

    final var subscriptionKey =
        RecordingExporter.messageStartEventSubscriptionRecords(
                MessageStartEventSubscriptionIntent.CREATED)
            .getFirst()
            .getKey();

    engine.message().withName(MESSAGE_NAME1).withCorrelationKey("key-1").publish();

    // when
    engine.deployment().withXmlResource(createProcessWithOneMessageStartEvent()).deploy();

    // then
    assertThat(RecordingExporter.messageStartEventSubscriptionRecords().limit(3))
        .extracting(Record::getIntent, Record::getKey)
        .containsExactly(
            tuple(MessageStartEventSubscriptionIntent.CREATED, subscriptionKey),
            tuple(MessageStartEventSubscriptionIntent.CORRELATED, subscriptionKey),
            tuple(MessageStartEventSubscriptionIntent.DELETED, subscriptionKey));
  }

  @Test // see #4099
  public void
      shouldResolveCorrelationKeyDefinedInMessageWhenOpeningSubscriptionForEventSubprocess() {
    final var process =
        Bpmn.createExecutableProcess("process")
            .eventSubProcess(
                "subprocess",
                s ->
                    s.startEvent()
                        .message(
                            m ->
                                m.name("event_message")
                                    .zeebeCorrelationKeyExpression("correlation_key"))
                        .endEvent())
            .startEvent()
            .message("start_message")
            .endEvent()
            .done();

    engine.deployment().withXmlResource(process).deploy();
    engine
        .message()
        .withName("start_message")
        .withCorrelationKey("")
        .withVariables(Map.of("correlation_key", "key"))
        .publish();

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withMessageName("event_message")
                .withCorrelationKey("key")
                .findAny())
        .isPresent();
  }

  /**
   * This test covers the following scenario. A process has a message start event (A) with output
   * mappings. This process also has an event subprocess with a message start event (B) which
   * references a variable that is only created after the output mapping of (A) has been applied.
   * The expectation is that the output mapping for A will be applied, and only after that the
   * message subscription for B will be opened. At this point the message subscription can be
   * opened. If it were opened earlier, it would produce a "variable not found" incident.
   */
  @Test
  public void
      shouldResolveCorrelationKeyDefinedByOutputMappingInMessageStartEventWhenOpeningSubscriptionForEventSubprocess() {
    final var process =
        Bpmn.createExecutableProcess("process")
            .eventSubProcess(
                "subprocess",
                s ->
                    s.startEvent()
                        .message(
                            m ->
                                m.name("event_message")
                                    .zeebeCorrelationKeyExpression("correlation_key"))
                        .endEvent())
            .startEvent()
            .message("start_message")
            .zeebeOutputExpression("correlation_key_before_mapping", "correlation_key")
            .endEvent()
            .done();

    engine.deployment().withXmlResource(process).deploy();
    engine
        .message()
        .withName("start_message")
        .withCorrelationKey("")
        .withVariables(Map.of("correlation_key_before_mapping", "key"))
        .publish();

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withMessageName("event_message")
                .withCorrelationKey("key")
                .findAny())
        .isPresent();
  }

  /**
   * This test covers the following scenario. A process has a none start event (A) with output
   * mappings. This process also has an event subprocess with a message start event (B) which
   * references a variable that is only created after the output mapping of (A) has been applied.
   * The expectation is that the output mapping for A will be applied, and only after that the
   * message subscription for B will be opened. At this point the message subscription can be
   * opened. If it were opened earlier, it would produce a "variable not found" incident.
   */
  @Test
  public void
      shouldResolveCorrelationKeyDefinedByOutputMappingInNoneStartEventWhenOpeningSubscriptionForEventSubprocess() {
    final var process =
        Bpmn.createExecutableProcess("process")
            .eventSubProcess(
                "subprocess",
                s ->
                    s.startEvent()
                        .message(
                            m ->
                                m.name("event_message")
                                    .zeebeCorrelationKeyExpression("correlation_key"))
                        .endEvent())
            .startEvent()
            .zeebeOutputExpression("correlation_key_before_mapping", "correlation_key")
            .endEvent()
            .done();

    engine.deployment().withXmlResource(process).deploy();
    engine
        .processInstance()
        .ofBpmnProcessId("process")
        .withVariables(Map.of("correlation_key_before_mapping", "key"))
        .create();

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withMessageName("event_message")
                .withCorrelationKey("key")
                .findAny())
        .isPresent();
  }

  @Test
  public void shouldOpenSingleMessageSubscriptionOnMultipleDeployments() {
    // give
    final var process = createProcessWithOneMessageStartEvent();

    engine.deployment().withXmlResource(process).deploy();

    // when
    engine.deployment().withXmlResource(process).deploy();

    // then
    // deploy an empty deployment, so we can use the position to limit the recorded stream
    final var position = engine.deployment().expectRejection().deploy().getPosition();
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= position)
                .filter(r -> r.getValueType() == ValueType.MESSAGE_START_EVENT_SUBSCRIPTION))
        .describedAs("Expect only one message start event subscription for duplicate deployments")
        .hasSize(1);
  }

  private static BpmnModelInstance createProcessWithOneMessageStartEvent() {
    return Bpmn.createExecutableProcess("processId")
        .startEvent(EVENT_ID1)
        .message(m -> m.name(MESSAGE_NAME1).id("startmsgId"))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance createProcessWithTwoMessageStartEvent() {
    final ProcessBuilder process = Bpmn.createExecutableProcess("processId");
    process.startEvent(EVENT_ID1).message(m -> m.name(MESSAGE_NAME1).id("startmsgId1")).endEvent();
    process.startEvent(EVENT_ID2).message(m -> m.name(MESSAGE_NAME2).id("startmsgId2")).endEvent();

    return process.done();
  }
}
