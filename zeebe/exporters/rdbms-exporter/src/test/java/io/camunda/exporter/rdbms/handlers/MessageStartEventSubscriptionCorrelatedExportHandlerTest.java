/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.db.rdbms.write.domain.CorrelatedMessageDbModel;
import io.camunda.db.rdbms.write.service.CorrelatedMessageWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageStartEventSubscriptionCorrelatedExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private CorrelatedMessageWriter correlatedMessageWriter;
  @Captor private ArgumentCaptor<CorrelatedMessageDbModel> dbModelCaptor;

  private MessageStartEventSubscriptionCorrelatedExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new MessageStartEventSubscriptionCorrelatedExportHandler(correlatedMessageWriter);
  }

  @Test
  void shouldAcceptMessageStartEventSubscriptionCorrelatedRecord() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            builder ->
                builder
                    .withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
                    .withKey(54321L)
                    .withTimestamp(System.currentTimeMillis()),
            builder ->
                builder
                    .withMessageKey(12345L)
                    .withMessageName("startMessage")
                    .withCorrelationKey("startCorrelation")
                    .withProcessInstanceKey(777L)
                    .withStartEventId("startEvent_1")
                    .withProcessDefinitionKey(999L)
                    .withBpmnProcessId("startProcess")
                    .withTenantId("startTenant")
                    .withVariables(Map.of("startVar", "startValue")));

    // when
    final boolean canExport = handler.canExport(record);

    // then
    assertThat(canExport).isTrue();
  }

  @Test
  void shouldRejectNonCorrelatedIntent() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            builder -> builder.withIntent(MessageStartEventSubscriptionIntent.CREATED),
            builder -> builder.withMessageKey(12345L));

    // when
    final boolean canExport = handler.canExport(record);

    // then
    assertThat(canExport).isFalse();
  }

  @Test
  void shouldExportMessageStartEventSubscriptionCorrelatedWithAllAttributes() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            builder ->
                builder
                    .withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
                    .withKey(54321L)
                    .withTimestamp(9876543210000L)
                    .withPartitionId(2),
            builder ->
                builder
                    .withMessageKey(12345L)
                    .withMessageName("startMessage")
                    .withCorrelationKey("startCorrelation")
                    .withProcessInstanceKey(777L)
                    .withStartEventId("startEvent_1")
                    .withProcessDefinitionKey(999L)
                    .withBpmnProcessId("startProcess")
                    .withTenantId("startTenant")
                    .withVariables(Map.of("startVar", "startValue", "numVar", 123)));

    // when
    handler.export(record);

    // then
    verify(correlatedMessageWriter).create(dbModelCaptor.capture());
    final CorrelatedMessageDbModel capturedModel = dbModelCaptor.getValue();

    assertThat(capturedModel.messageKey()).isEqualTo(12345L);
    assertThat(capturedModel.subscriptionKey()).isEqualTo(54321L); // record key as subscription key
    assertThat(capturedModel.messageName()).isEqualTo("startMessage");
    assertThat(capturedModel.correlationKey()).isEqualTo("startCorrelation");
    assertThat(capturedModel.processInstanceKey()).isEqualTo(777L);
    assertThat(capturedModel.flowNodeInstanceKey()).isNull(); // not available for start events
    assertThat(capturedModel.flowNodeId()).isEqualTo("startEvent_1"); // merged from startEventId
    assertThat(capturedModel.isInterrupting()).isNull(); // not applicable for start events
    assertThat(capturedModel.processDefinitionKey()).isEqualTo(999L);
    assertThat(capturedModel.bpmnProcessId()).isEqualTo("startProcess");
    assertThat(capturedModel.version()).isNull(); // would need process cache
    assertThat(capturedModel.versionTag()).isNull(); // would need process cache
    assertThat(capturedModel.variables()).isEqualTo("{\"startVar\":\"startValue\",\"numVar\":123}");
    assertThat(capturedModel.tenantId()).isEqualTo("startTenant");
    assertThat(capturedModel.dateTime()).isNotNull();
    assertThat(capturedModel.partitionId()).isEqualTo(2);
    assertThat(capturedModel.historyCleanupDate()).isNull();

    verifyNoMoreInteractions(correlatedMessageWriter);
  }

  @Test
  void shouldExportWithNullVariables() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            builder ->
                builder
                    .withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
                    .withKey(54321L),
            builder ->
                builder
                    .withMessageKey(12345L)
                    .withMessageName("startMessage")
                    .withCorrelationKey("startCorrelation")
                    .withProcessInstanceKey(777L)
                    .withStartEventId("startEvent_1")
                    .withProcessDefinitionKey(999L)
                    .withBpmnProcessId("startProcess")
                    .withTenantId("startTenant")
                    .withVariables(null));

    // when
    handler.export(record);

    // then
    verify(correlatedMessageWriter).create(dbModelCaptor.capture());
    final CorrelatedMessageDbModel capturedModel = dbModelCaptor.getValue();

    assertThat(capturedModel.variables()).isNull();
  }

  @Test
  void shouldExportWithEmptyVariables() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            builder ->
                builder
                    .withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
                    .withKey(54321L),
            builder ->
                builder
                    .withMessageKey(12345L)
                    .withMessageName("startMessage")
                    .withCorrelationKey("startCorrelation")
                    .withProcessInstanceKey(777L)
                    .withStartEventId("startEvent_1")
                    .withProcessDefinitionKey(999L)
                    .withBpmnProcessId("startProcess")
                    .withTenantId("startTenant")
                    .withVariables(Map.of()));

    // when
    handler.export(record);

    // then
    verify(correlatedMessageWriter).create(dbModelCaptor.capture());
    final CorrelatedMessageDbModel capturedModel = dbModelCaptor.getValue();

    assertThat(capturedModel.variables()).isNull();
  }

  @Test
  void shouldHandleComplexVariablesJson() {
    // given
    final Map<String, Object> complexVariables = Map.of(
        "simpleString", "value",
        "number", 42,
        "boolean", true,
        "nested", Map.of("inner", "value", "count", 5)
    );

    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            builder ->
                builder
                    .withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
                    .withKey(54321L),
            builder ->
                builder
                    .withMessageKey(12345L)
                    .withMessageName("startMessage")
                    .withStartEventId("startEvent_1")
                    .withProcessDefinitionKey(999L)
                    .withBpmnProcessId("startProcess")
                    .withVariables(complexVariables));

    // when
    handler.export(record);

    // then
    verify(correlatedMessageWriter).create(dbModelCaptor.capture());
    final CorrelatedMessageDbModel capturedModel = dbModelCaptor.getValue();

    assertThat(capturedModel.variables()).isNotNull();
    assertThat(capturedModel.variables()).contains("simpleString");
    assertThat(capturedModel.variables()).contains("number");
    assertThat(capturedModel.variables()).contains("boolean");
    assertThat(capturedModel.variables()).contains("nested");
  }

  @Test
  void shouldHandleMultipleCorrelationsToSameSubscription() {
    // given - two different messages correlated to same subscription
    final Record<MessageStartEventSubscriptionRecordValue> record1 =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            builder ->
                builder
                    .withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
                    .withKey(54321L), // same subscription key
            builder ->
                builder
                    .withMessageKey(12345L) // different message key
                    .withMessageName("startMessage")
                    .withStartEventId("startEvent_1")
                    .withProcessDefinitionKey(999L)
                    .withBpmnProcessId("startProcess"));

    final Record<MessageStartEventSubscriptionRecordValue> record2 =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            builder ->
                builder
                    .withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
                    .withKey(54321L), // same subscription key
            builder ->
                builder
                    .withMessageKey(98765L) // different message key
                    .withMessageName("startMessage")
                    .withStartEventId("startEvent_1")
                    .withProcessDefinitionKey(999L)
                    .withBpmnProcessId("startProcess"));

    // when
    handler.export(record1);
    handler.export(record2);

    // then
    verify(correlatedMessageWriter).create(any(CorrelatedMessageDbModel.class));
    verify(correlatedMessageWriter).create(any(CorrelatedMessageDbModel.class));
    // Both should be stored with different composite keys (messageKey, subscriptionKey)
  }
}