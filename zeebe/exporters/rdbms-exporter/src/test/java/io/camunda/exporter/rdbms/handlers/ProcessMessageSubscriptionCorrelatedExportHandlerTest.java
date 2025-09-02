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
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
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
class ProcessMessageSubscriptionCorrelatedExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private CorrelatedMessageWriter correlatedMessageWriter;
  @Captor private ArgumentCaptor<CorrelatedMessageDbModel> dbModelCaptor;

  private ProcessMessageSubscriptionCorrelatedExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ProcessMessageSubscriptionCorrelatedExportHandler(correlatedMessageWriter);
  }

  @Test
  void shouldAcceptProcessMessageSubscriptionCorrelatedRecord() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            builder ->
                builder
                    .withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                    .withKey(12345L)
                    .withTimestamp(System.currentTimeMillis()),
            builder ->
                builder
                    .withMessageKey(67890L)
                    .withMessageName("testMessage")
                    .withCorrelationKey("correlation123")
                    .withProcessInstanceKey(111L)
                    .withElementInstanceKey(222L)
                    .withElementId("testElement")
                    .withInterrupting(true)
                    .withBpmnProcessId("testProcess")
                    .withTenantId("testTenant")
                    .withVariables(Map.of("var1", "value1", "var2", 42)));

    // when
    final boolean canExport = handler.canExport(record);

    // then
    assertThat(canExport).isTrue();
  }

  @Test
  void shouldRejectNonCorrelatedIntent() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            builder -> builder.withIntent(ProcessMessageSubscriptionIntent.CREATED),
            builder -> builder.withMessageKey(67890L));

    // when
    final boolean canExport = handler.canExport(record);

    // then
    assertThat(canExport).isFalse();
  }

  @Test
  void shouldExportProcessMessageSubscriptionCorrelatedWithAllAttributes() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            builder ->
                builder
                    .withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                    .withKey(12345L)
                    .withTimestamp(1234567890000L)
                    .withPartitionId(1),
            builder ->
                builder
                    .withMessageKey(67890L)
                    .withMessageName("testMessage")
                    .withCorrelationKey("correlation123")
                    .withProcessInstanceKey(111L)
                    .withElementInstanceKey(222L)
                    .withElementId("testElement")
                    .withInterrupting(true)
                    .withBpmnProcessId("testProcess")
                    .withTenantId("testTenant")
                    .withVariables(Map.of("var1", "value1", "var2", 42)));

    // when
    handler.export(record);

    // then
    verify(correlatedMessageWriter).create(dbModelCaptor.capture());
    final CorrelatedMessageDbModel capturedModel = dbModelCaptor.getValue();

    assertThat(capturedModel.messageKey()).isEqualTo(67890L);
    assertThat(capturedModel.subscriptionKey()).isEqualTo(12345L); // record key as subscription key
    assertThat(capturedModel.messageName()).isEqualTo("testMessage");
    assertThat(capturedModel.correlationKey()).isEqualTo("correlation123");
    assertThat(capturedModel.processInstanceKey()).isEqualTo(111L);
    assertThat(capturedModel.flowNodeInstanceKey()).isEqualTo(222L);
    assertThat(capturedModel.flowNodeId()).isEqualTo("testElement");
    assertThat(capturedModel.isInterrupting()).isTrue();
    assertThat(capturedModel.processDefinitionKey()).isNull(); // not available in process subscriptions
    assertThat(capturedModel.bpmnProcessId()).isEqualTo("testProcess");
    assertThat(capturedModel.version()).isNull(); // would need process cache
    assertThat(capturedModel.versionTag()).isNull(); // would need process cache
    assertThat(capturedModel.variables()).isEqualTo("{\"var1\":\"value1\",\"var2\":42}");
    assertThat(capturedModel.tenantId()).isEqualTo("testTenant");
    assertThat(capturedModel.dateTime()).isNotNull();
    assertThat(capturedModel.partitionId()).isEqualTo(1);
    assertThat(capturedModel.historyCleanupDate()).isNull();

    verifyNoMoreInteractions(correlatedMessageWriter);
  }

  @Test
  void shouldExportWithNullVariables() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            builder ->
                builder
                    .withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                    .withKey(12345L),
            builder ->
                builder
                    .withMessageKey(67890L)
                    .withMessageName("testMessage")
                    .withCorrelationKey("correlation123")
                    .withProcessInstanceKey(111L)
                    .withElementInstanceKey(222L)
                    .withElementId("testElement")
                    .withInterrupting(false)
                    .withBpmnProcessId("testProcess")
                    .withTenantId("testTenant")
                    .withVariables(null));

    // when
    handler.export(record);

    // then
    verify(correlatedMessageWriter).create(dbModelCaptor.capture());
    final CorrelatedMessageDbModel capturedModel = dbModelCaptor.getValue();

    assertThat(capturedModel.variables()).isNull();
    assertThat(capturedModel.isInterrupting()).isFalse();
  }

  @Test
  void shouldExportWithEmptyVariables() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            builder ->
                builder
                    .withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                    .withKey(12345L),
            builder ->
                builder
                    .withMessageKey(67890L)
                    .withMessageName("testMessage")
                    .withCorrelationKey("correlation123")
                    .withProcessInstanceKey(111L)
                    .withElementInstanceKey(222L)
                    .withElementId("testElement")
                    .withInterrupting(false)
                    .withBpmnProcessId("testProcess")
                    .withTenantId("testTenant")
                    .withVariables(Map.of()));

    // when
    handler.export(record);

    // then
    verify(correlatedMessageWriter).create(dbModelCaptor.capture());
    final CorrelatedMessageDbModel capturedModel = dbModelCaptor.getValue();

    assertThat(capturedModel.variables()).isNull();
  }

  @Test
  void shouldHandleMultipleCorrelationsToSameSubscription() {
    // given - two different messages correlated to same subscription
    final Record<ProcessMessageSubscriptionRecordValue> record1 =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            builder ->
                builder
                    .withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                    .withKey(12345L), // same subscription key
            builder ->
                builder
                    .withMessageKey(67890L) // different message key
                    .withMessageName("testMessage")
                    .withElementId("testElement")
                    .withBpmnProcessId("testProcess"));

    final Record<ProcessMessageSubscriptionRecordValue> record2 =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            builder ->
                builder
                    .withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                    .withKey(12345L), // same subscription key
            builder ->
                builder
                    .withMessageKey(98765L) // different message key
                    .withMessageName("testMessage")
                    .withElementId("testElement")
                    .withBpmnProcessId("testProcess"));

    // when
    handler.export(record1);
    handler.export(record2);

    // then
    verify(correlatedMessageWriter).create(any(CorrelatedMessageDbModel.class));
    verify(correlatedMessageWriter).create(any(CorrelatedMessageDbModel.class));
    // Both should be stored with different composite keys (messageKey, subscriptionKey)
  }
}