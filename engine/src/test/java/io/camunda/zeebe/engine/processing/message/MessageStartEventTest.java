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
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;

public final class MessageStartEventTest {

  private static final String MESSAGE_NAME_1 = "a";
  private static final String MESSAGE_NAME_EXPRESSION_1 = "=\"a\"";
  private static final String MESSAGE_NAME_2 = "b";

  private static final String CORRELATION_KEY_1 = "key-1";
  private static final String CORRELATION_KEY_2 = "key-2";

  private static final BpmnModelInstance SINGLE_START_EVENT_1 =
      singleStartEvent(startEvent -> {}, MESSAGE_NAME_1);
  private static final BpmnModelInstance SINGLE_START_EVENT_EXPRESSION_1 =
      singleStartEvent(startEvent -> {}, MESSAGE_NAME_EXPRESSION_1);
  private static final BpmnModelInstance MULTIPLE_START_EVENTS = multipleStartEvents();

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  private static BpmnModelInstance singleStartEvent(final Consumer<StartEventBuilder> customizer) {
    return singleStartEvent(customizer, MESSAGE_NAME_1);
  }

  private static BpmnModelInstance singleStartEvent(
      final Consumer<StartEventBuilder> customizer, final String messageName) {
    final var startEventBuilder =
        Bpmn.createExecutableProcess("wf").startEvent("start").message(messageName);

    customizer.accept(startEventBuilder);

    return startEventBuilder.serviceTask("task", t -> t.zeebeJobType("test")).done();
  }

  private static BpmnModelInstance multipleStartEvents() {
    final var process = Bpmn.createExecutableProcess("wf");
    process.startEvent().message(MESSAGE_NAME_1).serviceTask("task", t -> t.zeebeJobType("test"));
    process.startEvent().message(MESSAGE_NAME_2).connectTo("task");

    return process.done();
  }

  @Test
  public void shouldCorrelateMessageToStartEvent() {
    // given
    engine.deployment().withXmlResource(SINGLE_START_EVENT_1).deploy();

    // when
    engine.message().withCorrelationKey(CORRELATION_KEY_1).withName(MESSAGE_NAME_1).publish();

    // then
    final var processInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .filterRootScope()
            .getFirst();

    final var startEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.START_EVENT)
            .getFirst();

    Assertions.assertThat(startEvent.getValue())
        .hasProcessDefinitionKey(processInstance.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(processInstance.getValue().getBpmnProcessId())
        .hasVersion(processInstance.getValue().getVersion())
        .hasProcessInstanceKey(processInstance.getKey())
        .hasFlowScopeKey(processInstance.getKey());
  }

  @Test
  public void shouldCorrelateMessageSubscription() {
    // given
    engine.deployment().withXmlResource(SINGLE_START_EVENT_1).deploy();

    // when
    final var messagePublished =
        engine.message().withCorrelationKey(CORRELATION_KEY_1).withName(MESSAGE_NAME_1).publish();

    // then
    final var startEventActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.START_EVENT)
            .getFirst();

    final var subscriptionCorrelated =
        RecordingExporter.messageStartEventSubscriptionRecords(
                MessageStartEventSubscriptionIntent.CORRELATED)
            .getFirst();

    Assertions.assertThat(subscriptionCorrelated.getValue())
        .hasProcessDefinitionKey(startEventActivated.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(startEventActivated.getValue().getBpmnProcessId())
        .hasProcessInstanceKey(startEventActivated.getValue().getProcessInstanceKey())
        .hasStartEventId(startEventActivated.getValue().getElementId())
        .hasMessageKey(messagePublished.getKey())
        .hasMessageName(MESSAGE_NAME_1)
        .hasCorrelationKey(CORRELATION_KEY_1);
  }

  @Test
  public void shouldCreateNewInstanceWithNameLiteral() {
    // given
    engine.deployment().withXmlResource(SINGLE_START_EVENT_1).deploy();

    // when
    engine.message().withCorrelationKey(CORRELATION_KEY_1).withName(MESSAGE_NAME_1).publish();

    final var job = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();
    engine.job().withKey(job.getKey()).complete();

    // then
    assertThat(RecordingExporter.processInstanceRecords().limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCreateNewInstanceWithNameFeelExpression() {
    // given
    engine.deployment().withXmlResource(SINGLE_START_EVENT_EXPRESSION_1).deploy();

    // when
    engine.message().withCorrelationKey(CORRELATION_KEY_1).withName(MESSAGE_NAME_1).publish();

    final var job = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();
    engine.job().withKey(job.getKey()).complete();

    // then
    assertThat(RecordingExporter.processInstanceRecords().limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCreateNewInstanceWithMessageVariables() {
    // given
    engine.deployment().withXmlResource(SINGLE_START_EVENT_1).deploy();

    // when
    engine
        .message()
        .withCorrelationKey(CORRELATION_KEY_1)
        .withName(MESSAGE_NAME_1)
        .withVariables(Map.of("x", 1, "y", 2))
        .publish();

    // then
    assertThat(RecordingExporter.variableRecords().limit(2))
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getName, VariableRecordValue::getValue)
        .hasSize(2)
        .contains(tuple("x", "1"), tuple("y", "2"));
  }

  @Test
  public void shouldApplyOutputMappings() {
    // given
    engine
        .deployment()
        .withXmlResource(singleStartEvent(startEvent -> startEvent.zeebeOutputExpression("x", "y")))
        .deploy();

    // when
    engine
        .message()
        .withCorrelationKey(CORRELATION_KEY_1)
        .withName(MESSAGE_NAME_1)
        .withVariables(Map.of("x", 1))
        .publish();

    // then
    final var processInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .filterRootScope()
            .getFirst();

    assertThat(RecordingExporter.variableRecords().withScopeKey(processInstance.getKey()).limit(1))
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getName, VariableRecordValue::getValue)
        .contains(tuple("y", "1"));
  }

  @Test
  public void shouldCreateInstanceOfLatestVersion() {
    // given
    engine
        .deployment()
        .withXmlResource(singleStartEvent(startEvent -> startEvent.id("v1")))
        .deploy();

    engine
        .deployment()
        .withXmlResource(singleStartEvent(startEvent -> startEvent.id("v2")))
        .deploy();

    // when
    engine.message().withCorrelationKey(CORRELATION_KEY_1).withName(MESSAGE_NAME_1).publish();

    // then
    final var startEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.START_EVENT)
            .getFirst();

    Assertions.assertThat(startEvent.getValue()).hasElementId("v2");
  }

  @Test
  public void shouldCreateNewInstanceWithMultipleStartEvents() {
    // given
    engine.deployment().withXmlResource(MULTIPLE_START_EVENTS).deploy();

    // when
    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 1))
        .publish();

    engine
        .message()
        .withName(MESSAGE_NAME_2)
        .withCorrelationKey(CORRELATION_KEY_2)
        .withVariables(Map.of("x", 2))
        .publish();

    // then
    assertThat(
            RecordingExporter.variableRecords().withName("x").filterProcessInstanceScope().limit(2))
        .extracting(r -> r.getValue().getValue())
        .describedAs("Expected messages [1,2] to be correlated")
        .containsExactly("1", "2");
  }

  @Test
  public void shouldTriggerOnlyMessageStartEvent() {
    // given
    final var process = Bpmn.createExecutableProcess("process");
    process.startEvent("none-start").endEvent();
    process.startEvent("message-start").message(MESSAGE_NAME_1).endEvent();
    process.startEvent("timer-start").timerWithCycle("R/PT1H").endEvent();

    engine.deployment().withXmlResource(process.done()).deploy();

    // when
    engine.message().withName(MESSAGE_NAME_1).withCorrelationKey(CORRELATION_KEY_1).publish();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.START_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsOnly("message-start");
  }

  @Test
  public void shouldNotCorrelateSameMessageToCreatedInstance() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("wf")
                .startEvent()
                .message(MESSAGE_NAME_1)
                .intermediateCatchEvent(
                    "catch",
                    e ->
                        e.message(m -> m.name(MESSAGE_NAME_1).zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .done())
        .deploy();

    // when
    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("key", CORRELATION_KEY_1, "x", 1))
        .publish();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED).await();

    final var message2 =
        engine
            .message()
            .withName(MESSAGE_NAME_1)
            .withCorrelationKey(CORRELATION_KEY_1)
            .withVariables(Map.of("x", 2))
            .publish();

    // then
    assertThat(
            RecordingExporter.variableRecords().withName("x").filterProcessInstanceScope().limit(2))
        .extracting(r -> r.getValue().getValue())
        .describedAs("Expected messages [1,2] to be correlated")
        .containsExactly("1", "2");

    final var subscription =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CORRELATED)
            .getFirst();

    Assertions.assertThat(subscription.getValue()).hasMessageKey(message2.getKey());
  }

  @Test
  public void shouldCreateMultipleInstancesIfCorrelationKeyIsEmpty() {
    // given
    engine.deployment().withXmlResource(SINGLE_START_EVENT_1).deploy();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey("")
        .withVariables(Map.of("x", 1))
        .publish();

    RecordingExporter.jobRecords(JobIntent.CREATED).await();

    // when
    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey("")
        .withVariables(Map.of("x", 2))
        .publish();

    // then
    assertThat(
            RecordingExporter.variableRecords().withName("x").filterProcessInstanceScope().limit(2))
        .extracting(r -> r.getValue().getValue())
        .describedAs("Expected messages [1,2] to be correlated")
        .containsExactly("1", "2");
  }

  @Test
  public void shouldCreateOnlyOneInstancePerCorrelationKey() {
    // given
    engine.deployment().withXmlResource(SINGLE_START_EVENT_1).deploy();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 1))
        .publish();

    RecordingExporter.jobRecords(JobIntent.CREATED).await();

    // when
    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 2))
        .publish();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_2)
        .withVariables(Map.of("x", 3))
        .publish();

    // then
    assertThat(
            RecordingExporter.variableRecords().withName("x").filterProcessInstanceScope().limit(2))
        .extracting(r -> r.getValue().getValue())
        .describedAs("Expected messages [1,3] to be correlated")
        .containsExactly("1", "3");
  }

  @Test
  public void shouldNotCreateInstanceForDifferentVersion() {
    // given
    engine
        .deployment()
        .withXmlResource(singleStartEvent(startEvent -> startEvent.id("v1")))
        .deploy();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 1))
        .publish();

    RecordingExporter.jobRecords(JobIntent.CREATED).await();

    engine
        .deployment()
        .withXmlResource(singleStartEvent(startEvent -> startEvent.id("v2")))
        .deploy();

    // when
    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 2))
        .publish();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_2)
        .withVariables(Map.of("x", 3))
        .publish();

    // then
    assertThat(
            RecordingExporter.variableRecords().withName("x").filterProcessInstanceScope().limit(2))
        .extracting(r -> r.getValue().getValue())
        .describedAs("Expected messages [1,3] to be correlated")
        .containsExactly("1", "3");
  }

  @Test
  public void shouldCreateNewInstanceAfterCompletion() {
    // given
    engine.deployment().withXmlResource(SINGLE_START_EVENT_1).deploy();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 1))
        .publish();

    final var job = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();
    engine.job().withKey(job.getKey()).complete();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(job.getValue().getProcessInstanceKey())
        .filterRootScope()
        .await();

    // when
    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 2))
        .publish();

    // then
    assertThat(
            RecordingExporter.variableRecords().withName("x").filterProcessInstanceScope().limit(2))
        .extracting(r -> r.getValue().getValue())
        .describedAs("Expected messages [1,2] to be correlated")
        .containsExactly("1", "2");
  }

  @Test
  public void shouldCreateNewInstanceAfterTermination() {
    // given
    engine.deployment().withXmlResource(SINGLE_START_EVENT_1).deploy();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 1))
        .publish();

    final var job = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    engine.processInstance().withInstanceKey(job.getValue().getProcessInstanceKey()).cancel();

    // when
    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 2))
        .publish();

    // then
    assertThat(
            RecordingExporter.variableRecords().withName("x").filterProcessInstanceScope().limit(2))
        .extracting(r -> r.getValue().getValue())
        .describedAs("Expected messages [1,2] to be correlated")
        .containsExactly("1", "2");
  }

  @Test
  public void shouldCreateNewInstanceForBufferedMessageAfterCompletion() {
    // given
    engine.deployment().withXmlResource(SINGLE_START_EVENT_1).deploy();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 1))
        .publish();

    final var job1 = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    // when
    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 2))
        .publish();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 3))
        .publish();

    engine.job().withKey(job1.getKey()).complete();

    final var job2 = RecordingExporter.jobRecords(JobIntent.CREATED).skip(1).getFirst();
    engine.job().withKey(job2.getKey()).complete();

    // then
    assertThat(
            RecordingExporter.variableRecords().withName("x").filterProcessInstanceScope().limit(3))
        .extracting(r -> r.getValue().getValue())
        .describedAs("Expected messages [1,2,3] to be correlated")
        .containsExactly("1", "2", "3");
  }

  @Test
  public void shouldCreateNewInstanceForBufferedMessageAfterTermination() {
    // given
    engine.deployment().withXmlResource(SINGLE_START_EVENT_1).deploy();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 1))
        .publish();

    final var job1 = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    // when
    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 2))
        .publish();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 3))
        .publish();

    engine.processInstance().withInstanceKey(job1.getValue().getProcessInstanceKey()).cancel();

    final var job2 = RecordingExporter.jobRecords(JobIntent.CREATED).skip(1).getFirst();
    engine.processInstance().withInstanceKey(job2.getValue().getProcessInstanceKey()).cancel();

    // then
    assertThat(
            RecordingExporter.variableRecords().withName("x").filterProcessInstanceScope().limit(3))
        .extracting(r -> r.getValue().getValue())
        .describedAs("Expected messages [1,2,3] to be correlated")
        .containsExactly("1", "2", "3");
  }

  @Test
  public void shouldWriteCorrelatedEventsForBufferedMessages() {
    // given
    final var instanceCount = 3;
    engine.deployment().withXmlResource(SINGLE_START_EVENT_1).deploy();

    // when
    final var expectedSubscriptionTuples =
        IntStream.range(0, instanceCount)
            .mapToObj(
                i -> {
                  final Map<String, Object> variables = Map.of("x", i);

                  final var messagePublished =
                      engine
                          .message()
                          .withName(MESSAGE_NAME_1)
                          .withCorrelationKey(CORRELATION_KEY_1)
                          .withVariables(variables)
                          .publish();

                  final var jobCreated =
                      RecordingExporter.jobRecords(JobIntent.CREATED).skip(i).getFirst();
                  engine.job().withKey(jobCreated.getKey()).complete();

                  final var processInstanceKey = jobCreated.getValue().getProcessInstanceKey();
                  return tuple(messagePublished.getKey(), processInstanceKey, variables);
                })
            .collect(Collectors.toList());

    // then
    final var process = RecordingExporter.processRecords().getFirst().getValue();

    final var subscriptionRecords =
        RecordingExporter.messageStartEventSubscriptionRecords(
                MessageStartEventSubscriptionIntent.CORRELATED)
            .limit(instanceCount)
            .map(Record::getValue)
            .collect(Collectors.toList());

    assertThat(subscriptionRecords)
        .allSatisfy(
            value -> {
              assertThat(value.getMessageName()).isEqualTo(MESSAGE_NAME_1);
              assertThat(value.getCorrelationKey()).isEqualTo(CORRELATION_KEY_1);
              assertThat(value.getProcessDefinitionKey())
                  .isEqualTo(process.getProcessDefinitionKey());
              assertThat(value.getBpmnProcessId()).isEqualTo(process.getBpmnProcessId());
              assertThat(value.getStartEventId()).isEqualTo("start");
            });

    assertThat(subscriptionRecords)
        .extracting(
            MessageStartEventSubscriptionRecordValue::getMessageKey,
            MessageStartEventSubscriptionRecordValue::getProcessInstanceKey,
            MessageStartEventSubscriptionRecordValue::getVariables)
        .containsSequence(expectedSubscriptionTuples);
  }

  @Test
  public void shouldCreateNewInstanceOfLatestProcessVersionForBufferedMessage() {
    // given
    engine
        .deployment()
        .withXmlResource(singleStartEvent(startEvent -> startEvent.id("v1")))
        .deploy();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 1))
        .publish();

    final var job = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    engine
        .deployment()
        .withXmlResource(singleStartEvent(startEvent -> startEvent.id("v2")))
        .deploy();

    // when
    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 2))
        .publish();

    engine.job().withKey(job.getKey()).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.START_EVENT)
                .limit(2))
        .extracting(r -> r.getValue().getElementId())
        .containsExactly("v1", "v2");
  }

  @Test
  public void shouldNotCreateNewInstanceForBufferedMessageAfterTTL() {
    // given
    engine.deployment().withXmlResource(SINGLE_START_EVENT_1).deploy();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 1))
        .publish();

    final var messageTTL = Duration.ofSeconds(10);

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 2))
        .withTimeToLive(messageTTL)
        .publish();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 3))
        .withTimeToLive(messageTTL.multipliedBy(2))
        .publish();

    // when
    final var job = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    engine.getClock().addTime(messageTTL);

    engine.job().withKey(job.getKey()).complete();

    // then
    assertThat(
            RecordingExporter.variableRecords().withName("x").filterProcessInstanceScope().limit(2))
        .extracting(r -> r.getValue().getValue())
        .describedAs("Expected messages [1,3] to be correlated")
        .containsExactly("1", "3");
  }

  @Test
  public void shouldCreateOnlyOneInstancePerCorrelationKeyWithMultipleStartEvents() {
    // given
    engine.deployment().withXmlResource(MULTIPLE_START_EVENTS).deploy();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 1))
        .publish();

    RecordingExporter.jobRecords(JobIntent.CREATED).await();

    // when
    engine
        .message()
        .withName(MESSAGE_NAME_2)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 2))
        .publish();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_2)
        .withVariables(Map.of("x", 3))
        .publish();

    // then
    assertThat(
            RecordingExporter.variableRecords().withName("x").filterProcessInstanceScope().limit(2))
        .extracting(r -> r.getValue().getValue())
        .describedAs("Expected messages [1,3] to be correlated")
        .containsExactly("1", "3");
  }

  @Test
  public void shouldCreateNewInstanceForBufferedMessageWithMultipleStartEvents() {
    // given
    engine.deployment().withXmlResource(MULTIPLE_START_EVENTS).deploy();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 1))
        .publish();

    // when
    engine
        .message()
        .withName(MESSAGE_NAME_2)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 2))
        .publish();

    engine
        .message()
        .withName(MESSAGE_NAME_1)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 3))
        .publish();

    engine
        .message()
        .withName(MESSAGE_NAME_2)
        .withCorrelationKey(CORRELATION_KEY_1)
        .withVariables(Map.of("x", 4))
        .publish();

    IntStream.range(0, 4)
        .forEach(
            j -> {
              final var job = RecordingExporter.jobRecords(JobIntent.CREATED).skip(j).getFirst();
              engine.job().withKey(job.getKey()).complete();
            });

    // then
    assertThat(
            RecordingExporter.variableRecords().withName("x").filterProcessInstanceScope().limit(4))
        .extracting(r -> r.getValue().getValue())
        .describedAs("Expected messages [1,2,3,4] to be correlated")
        .containsExactly("1", "2", "3", "4");
  }

  // https://github.com/camunda-cloud/zeebe/issues/8068
  @Test
  public void shouldCreateProcessInstancesAndPassVariables() {
    // given
    engine.deployment().withXmlResource(SINGLE_START_EVENT_1).deploy();

    // when - publish two messages concurrently
    engine.writeRecords(
        RecordToWrite.command()
            .message(
                MessageIntent.PUBLISH,
                new MessageRecord()
                    .setName(MESSAGE_NAME_1)
                    .setTimeToLive(0L)
                    .setCorrelationKey(CORRELATION_KEY_1)
                    .setVariables(MsgPackUtil.asMsgPack("x", 1))),
        RecordToWrite.command()
            .message(
                MessageIntent.PUBLISH,
                new MessageRecord()
                    .setName(MESSAGE_NAME_1)
                    .setTimeToLive(0L)
                    .setCorrelationKey(CORRELATION_KEY_2)
                    .setVariables(MsgPackUtil.asMsgPack("x", 2))));

    // then
    final var processInstanceKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.PROCESS)
            .limit(2)
            .map(r -> r.getValue().getProcessInstanceKey())
            .collect(Collectors.toList());

    assertThat(processInstanceKeys)
        .describedAs("Expected two process instances to be created")
        .hasSize(2);

    assertThat(RecordingExporter.variableRecords().filterProcessInstanceScope().limit(2))
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getProcessInstanceKey, VariableRecordValue::getValue)
        .hasSize(2)
        .describedAs("Expected two process instances with different variables")
        .contains(tuple(processInstanceKeys.get(0), "1"), tuple(processInstanceKeys.get(1), "2"));
  }
}
