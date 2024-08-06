/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.MessageCorrelationRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public final class CorrelateMessageTest {

  public static final String CORRELATION_KEY = "correlationKey";
  public static final String MESSAGE_NAME = "messageName";
  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldNotCorrelateWhenNoSubscriptions() {
    // when
    engine
        .messageCorrelation()
        .withCorrelationKey(CORRELATION_KEY)
        .withName(MESSAGE_NAME)
        .expectNotCorrelated()
        .correlate();

    // then
    assertThat(RecordingExporter.records().limit(r -> r.getIntent().equals(MessageIntent.EXPIRED)))
        .extracting(Record::getIntent)
        .containsExactly(
            MessageCorrelationIntent.CORRELATE,
            MessageIntent.PUBLISHED,
            MessageCorrelationIntent.NOT_CORRELATED,
            MessageIntent.EXPIRED);
  }

  @Test
  public void shouldHaveCorrectCorrelatedLifecycleForStartEvent() {
    // given
    deployProcessWithMessageStartEvent();

    // when
    engine
        .messageCorrelation()
        .withCorrelationKey(CORRELATION_KEY)
        .withName(MESSAGE_NAME)
        .correlate();

    // then
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent().equals(MessageIntent.EXPIRED))
                .filter(
                    r ->
                        List.of(
                                ValueType.MESSAGE_CORRELATION,
                                ValueType.MESSAGE,
                                ValueType.MESSAGE_START_EVENT_SUBSCRIPTION)
                            .contains(r.getValueType())))
        .extracting(Record::getIntent)
        .containsExactly(
            MessageStartEventSubscriptionIntent.CREATED,
            MessageCorrelationIntent.CORRELATE,
            MessageIntent.PUBLISHED,
            MessageStartEventSubscriptionIntent.CORRELATED,
            MessageCorrelationIntent.CORRELATED,
            MessageIntent.EXPIRED);
  }

  @Test
  public void shouldHaveCorrectCorrelatedLifeCycleForMessageEvent() {
    // given
    deployAndStartProcessWithIntermediaryMessageEvent(MESSAGE_NAME, CORRELATION_KEY);

    // when
    engine
        .messageCorrelation()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .correlate();

    // then
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent().equals(MessageCorrelationIntent.CORRELATED))
                .filter(
                    r ->
                        List.of(
                                ValueType.MESSAGE_CORRELATION,
                                ValueType.MESSAGE_SUBSCRIPTION,
                                ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
                                ValueType.MESSAGE)
                            .contains(r.getValueType())))
        .extracting(Record::getIntent)
        .containsSubsequence(
            MessageCorrelationIntent.CORRELATE,
            MessageIntent.PUBLISHED,
            MessageSubscriptionIntent.CORRELATING,
            ProcessMessageSubscriptionIntent.CORRELATE,
            MessageIntent.EXPIRED,
            ProcessMessageSubscriptionIntent.CORRELATED,
            MessageSubscriptionIntent.CORRELATE,
            MessageSubscriptionIntent.CORRELATED,
            MessageCorrelationIntent.CORRELATED);
  }

  @Test
  public void shouldCorrelateMessageToStartEvent() {
    // given
    deployProcessWithMessageStartEvent();

    // when
    final var record =
        engine
            .messageCorrelation()
            .withCorrelationKey(CORRELATION_KEY)
            .withName(MESSAGE_NAME)
            .correlate();

    // then
    assertMessageIsCorrelated(record);
  }

  @Test
  public void shouldCorrelateToMessageIntermediaryEvent() {
    // given
    deployAndStartProcessWithIntermediaryMessageEvent(MESSAGE_NAME, CORRELATION_KEY);

    // when
    final var record =
        engine
            .messageCorrelation()
            .withName(MESSAGE_NAME)
            .withCorrelationKey(CORRELATION_KEY)
            .correlate();

    // then
    assertMessageIsCorrelated(record);
  }

  @Test
  public void shouldCorrelateToMessageBoundaryEvent() {
    // given
    deployAndStartProcessWithMessageBoundaryEvent(MESSAGE_NAME, CORRELATION_KEY);

    // when
    final var record =
        engine
            .messageCorrelation()
            .withName(MESSAGE_NAME)
            .withCorrelationKey(CORRELATION_KEY)
            .correlate();

    // then
    assertMessageIsCorrelated(record);
  }

  @Test
  public void shouldNotCorrelateMessageIfNoProcess() {
    // when
    final var record =
        engine
            .messageCorrelation()
            .withName(MESSAGE_NAME)
            .withCorrelationKey(CORRELATION_KEY)
            .expectNotCorrelated()
            .correlate();

    // then
    assertMessageIsNotCorrelated(record);
  }

  @Test
  public void shouldRespondFirstProcessInstanceKeyWhenMultipleMessageStartEvent() {
    // given
    deployProcessWithMessageStartEvent("process1");
    deployProcessWithMessageStartEvent("process2");

    // when
    final var record =
        engine
            .messageCorrelation()
            .withName(MESSAGE_NAME)
            .withCorrelationKey(CORRELATION_KEY)
            .correlate();

    // then
    assertMessageIsCorrelated(record);
    final var processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .filter(r -> r.getValue().getBpmnProcessId().equals("process1"))
            .getFirst()
            .getKey();
    assertThat(record.getValue().getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  public void
      shouldResponseWithCreatedProcessInstanceWhenCorrelatedToStartEventAndIntermediateEvent() {
    // given
    deployProcessWithMessageStartEvent("processStartEvent");
    final var intermediaryProcessKey =
        deployAndStartProcessWithIntermediaryMessageEvent(MESSAGE_NAME, CORRELATION_KEY);

    // when
    final var record =
        engine
            .messageCorrelation()
            .withName(MESSAGE_NAME)
            .withCorrelationKey(CORRELATION_KEY)
            .correlate();

    // then
    assertMessageIsCorrelated(record);
    final var processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .filter(r -> r.getValue().getBpmnProcessId().equals("processStartEvent"))
            .getFirst()
            .getKey();
    assertThat(record.getValue().getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(intermediaryProcessKey)
                .limitToProcessInstanceCompleted())
        .describedAs("Has completed intermediary message process")
        .isNotEmpty();
  }

  @Test
  public void shouldCorrelateMessageWithVariablesToStartEvent() {
    // given
    final var processId = "processId";
    deployProcessWithMessageStartEvent(processId);
    final var variables = asMsgPack("foo", "bar");

    // when
    final var record =
        engine
            .messageCorrelation()
            .withCorrelationKey(CORRELATION_KEY)
            .withName(MESSAGE_NAME)
            .withVariables(variables)
            .correlate();

    // then
    assertMessageIsCorrelated(record);
    final var processInstanceRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withBpmnProcessId(processId)
            .filter(r -> r.getValue().getBpmnElementType().equals(BpmnElementType.PROCESS))
            .getFirst();
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withScopeKey(processInstanceRecord.getValue().getProcessInstanceKey())
                .getFirst())
        .extracting(r -> r.getValue().getName(), r -> r.getValue().getValue())
        .containsExactly("foo", "\"bar\"");
  }

  @Test
  public void shouldCorrelateMessageWithVariablesToIntermediaryEvent() {
    // given
    final var variables = asMsgPack("foo", "bar");
    final var processInstanceKey =
        deployAndStartProcessWithIntermediaryMessageEvent(MESSAGE_NAME, CORRELATION_KEY);

    // when
    final var record =
        engine
            .messageCorrelation()
            .withCorrelationKey(CORRELATION_KEY)
            .withName(MESSAGE_NAME)
            .withVariables(variables)
            .correlate();

    // then
    assertMessageIsCorrelated(record);
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withScopeKey(processInstanceKey)
                .getFirst())
        .extracting(r -> r.getValue().getName(), r -> r.getValue().getValue())
        .containsExactly("foo", "\"bar\"");
  }

  @Test
  public void shouldCorrelateMessageWithVariablesToBoundaryEvent() {
    // given
    final var variables = asMsgPack("foo", "bar");
    final var processInstanceKey =
        deployAndStartProcessWithMessageBoundaryEvent(MESSAGE_NAME, CORRELATION_KEY);

    // when
    final var record =
        engine
            .messageCorrelation()
            .withCorrelationKey(CORRELATION_KEY)
            .withName(MESSAGE_NAME)
            .withVariables(variables)
            .correlate();

    // then
    assertMessageIsCorrelated(record);
    final var processInstanceRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .filter(r -> r.getValue().getBpmnElementType().equals(BpmnElementType.PROCESS))
            .getFirst();
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withScopeKey(processInstanceRecord.getValue().getProcessInstanceKey())
                .getFirst())
        .extracting(r -> r.getValue().getName(), r -> r.getValue().getValue())
        .containsExactly("foo", "\"bar\"");
  }

  private static void assertMessageIsCorrelated(
      final Record<MessageCorrelationRecordValue> record) {
    Assertions.assertThat(record)
        .hasIntent(MessageCorrelationIntent.CORRELATED)
        .hasRecordType(RecordType.EVENT)
        .hasValueType(ValueType.MESSAGE_CORRELATION);
    Assertions.assertThat(record.getValue())
        .hasCorrelationKey(CORRELATION_KEY)
        .hasName(MESSAGE_NAME)
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private static void assertMessageIsNotCorrelated(
      final Record<MessageCorrelationRecordValue> record) {
    Assertions.assertThat(record)
        .hasIntent(MessageCorrelationIntent.NOT_CORRELATED)
        .hasRecordType(RecordType.EVENT)
        .hasValueType(ValueType.MESSAGE_CORRELATION);
    Assertions.assertThat(record.getValue())
        .hasCorrelationKey(CORRELATION_KEY)
        .hasName(MESSAGE_NAME)
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private void deployProcessWithMessageStartEvent(final String processId) {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .message(MESSAGE_NAME)
                .endEvent()
                .done())
        .deploy();
  }

  private void deployProcessWithMessageStartEvent() {
    deployProcessWithMessageStartEvent("process");
  }

  private long deployAndStartProcessWithIntermediaryMessageEvent(
      final String messageName, final String correlationKey) {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .intermediateCatchEvent(
                    "msg",
                    i ->
                        i.message(
                            m ->
                                m.name(messageName)
                                    .zeebeCorrelationKey("=\"%s\"".formatted(correlationKey))))
                .endEvent()
                .done())
        .deploy();
    return engine.processInstance().ofBpmnProcessId("process").create();
  }

  private long deployAndStartProcessWithMessageBoundaryEvent(
      final String messageName, final String correlationKey) {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .userTask()
                .boundaryEvent(
                    "msg",
                    i ->
                        i.message(
                            m ->
                                m.name(messageName)
                                    .zeebeCorrelationKey("=\"%s\"".formatted(correlationKey))))
                .endEvent()
                .done())
        .deploy();
    return engine.processInstance().ofBpmnProcessId("process").create();
  }
}
