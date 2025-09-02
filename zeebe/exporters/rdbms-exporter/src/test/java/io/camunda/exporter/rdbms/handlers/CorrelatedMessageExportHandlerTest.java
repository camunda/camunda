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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.write.domain.CorrelatedMessageDbModel;
import io.camunda.db.rdbms.write.service.CorrelatedMessageWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
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
class CorrelatedMessageExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private CorrelatedMessageWriter correlatedMessageWriter;

  @Captor private ArgumentCaptor<CorrelatedMessageDbModel> correlatedMessageCaptor;

  private CorrelatedMessageExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new CorrelatedMessageExportHandler(correlatedMessageWriter);
  }

  @Test
  void shouldExportProcessMessageSubscriptionCorrelatedRecord() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CORRELATED));

    // when
    final boolean canExport = handler.canExport((Record<Object>) record);
    handler.export((Record<Object>) record);

    // then
    assertThat(canExport).isTrue();
    verify(correlatedMessageWriter).create(correlatedMessageCaptor.capture());

    final CorrelatedMessageDbModel model = correlatedMessageCaptor.getValue();
    assertThat(model.messageKey()).isEqualTo(record.getValue().getMessageKey());
    assertThat(model.subscriptionKey()).isNotNull(); // Should be generated based on subscription key
    assertThat(model.messageName()).isEqualTo(record.getValue().getMessageName());
    assertThat(model.correlationKey()).isEqualTo(record.getValue().getCorrelationKey());
    assertThat(model.processInstanceKey()).isEqualTo(record.getValue().getProcessInstanceKey());
    assertThat(model.flowNodeInstanceKey()).isEqualTo(record.getValue().getElementInstanceKey());
    assertThat(model.startEventId()).isNull(); // Not applicable for process message subscriptions
    assertThat(model.bpmnProcessId()).isEqualTo(record.getValue().getBpmnProcessId());
    assertThat(model.tenantId()).isEqualTo(record.getValue().getTenantId());
  }

  @Test
  void shouldExportMessageStartEventSubscriptionCorrelatedRecord() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r -> r.withIntent(MessageStartEventSubscriptionIntent.CORRELATED));

    // when
    final boolean canExport = handler.canExport((Record<Object>) record);
    handler.export((Record<Object>) record);

    // then
    assertThat(canExport).isTrue();
    verify(correlatedMessageWriter).create(correlatedMessageCaptor.capture());

    final CorrelatedMessageDbModel model = correlatedMessageCaptor.getValue();
    assertThat(model.messageKey()).isEqualTo(record.getValue().getMessageKey());
    assertThat(model.subscriptionKey()).isNotNull(); // Should be generated based on subscription key
    assertThat(model.messageName()).isEqualTo(record.getValue().getMessageName());
    assertThat(model.correlationKey()).isEqualTo(record.getValue().getCorrelationKey());
    assertThat(model.processInstanceKey()).isEqualTo(record.getValue().getProcessInstanceKey());
    assertThat(model.flowNodeInstanceKey()).isNull(); // Not applicable for message start events
    assertThat(model.startEventId()).isEqualTo(record.getValue().getStartEventId());
    assertThat(model.bpmnProcessId()).isEqualTo(record.getValue().getBpmnProcessId());
    assertThat(model.tenantId()).isEqualTo(record.getValue().getTenantId());
  }

  @Test
  void shouldNotExportNonCorrelatedRecords() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CREATED));

    // when
    final boolean canExport = handler.canExport((Record<Object>) record);

    // then
    assertThat(canExport).isFalse();
  }

  @Test
  void shouldExportCorrelationsForSameMessageToDifferentSubscriptions() {
    // This test simulates the real scenario where the same message correlates to 
    // different subscriptions (e.g., different element instances with same message name)
    // In the engine, each correlation gets the subscription key as the record key,
    // so same subscription = same record key, different subscriptions = different record keys
    
    final long messageKey = 123L;
    final String messageName = "test-message";
    final String tenantId = "tenant1";
    
    // Create two records with different element instance keys (different subscriptions)
    final Record<ProcessMessageSubscriptionRecordValue> record1 =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                  .withValue(v -> v.setMessageKey(messageKey)
                                  .setMessageName(messageName)
                                  .setElementInstanceKey(111L) // Different subscription
                                  .setTenantId(tenantId)));
    final Record<ProcessMessageSubscriptionRecordValue> record2 =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                  .withValue(v -> v.setMessageKey(messageKey)
                                  .setMessageName(messageName)  
                                  .setElementInstanceKey(222L) // Different subscription
                                  .setTenantId(tenantId)));

    // when
    handler.export((Record<Object>) record1);
    handler.export((Record<Object>) record2);

    // then
    verify(correlatedMessageWriter, times(2)).create(correlatedMessageCaptor.capture());

    final var models = correlatedMessageCaptor.getAllValues();
    assertThat(models).hasSize(2);
    
    // Both should have the same message key (same message)
    assertThat(models.get(0).messageKey()).isEqualTo(messageKey);
    assertThat(models.get(1).messageKey()).isEqualTo(messageKey);
    
    // But should have different subscription keys (different subscriptions)
    assertThat(models.get(0).subscriptionKey()).isNotEqualTo(models.get(1).subscriptionKey());
  }

  @Test
  void shouldExportDifferentMessages() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record1 =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r ->
                r.withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                    .withValue(v -> v.setMessageKey(123L)));
    final Record<ProcessMessageSubscriptionRecordValue> record2 =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r ->
                r.withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                    .withValue(v -> v.setMessageKey(456L)));

    // when
    handler.export((Record<Object>) record1);
    handler.export((Record<Object>) record2);

    // then
    verify(correlatedMessageWriter, times(2)).create(any(CorrelatedMessageDbModel.class));
  }

  @Test
  void shouldExportMultipleCorrelationsToSameSubscription() {
    // This simulates the scenario where a non-interrupting boundary event
    // receives multiple messages and each correlation generates a unique record
    final long subscriptionElementInstanceKey = 789L;
    final String messageName = "non-interrupting-message";

    final Record<ProcessMessageSubscriptionRecordValue> correlation1 =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r ->
                r.withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                    .withValue(
                        v ->
                            v.setElementInstanceKey(subscriptionElementInstanceKey)
                                .setMessageName(messageName)
                                .setMessageKey(111L)));
    final Record<ProcessMessageSubscriptionRecordValue> correlation2 =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r ->
                r.withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                    .withValue(
                        v ->
                            v.setElementInstanceKey(subscriptionElementInstanceKey)
                                .setMessageName(messageName)
                                .setMessageKey(222L)));
    final Record<ProcessMessageSubscriptionRecordValue> correlation3 =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r ->
                r.withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                    .withValue(
                        v ->
                            v.setElementInstanceKey(subscriptionElementInstanceKey)
                                .setMessageName(messageName)
                                .setMessageKey(333L)));

    // Verify each correlation uses the same subscription key but different message keys
    // This simulates how the engine would behave with the same subscription getting multiple messages

    // when - export all three correlations
    handler.export((Record<Object>) correlation1);
    handler.export((Record<Object>) correlation2);
    handler.export((Record<Object>) correlation3);

    // then - all three should be stored without overwriting each other
    verify(correlatedMessageWriter, times(3)).create(correlatedMessageCaptor.capture());

    final var models = correlatedMessageCaptor.getAllValues();
    assertThat(models).hasSize(3);

    // All should have the same subscription key (same subscription)
    final Long expectedSubscriptionKey = models.get(0).subscriptionKey();
    assertThat(models.get(1).subscriptionKey()).isEqualTo(expectedSubscriptionKey);
    assertThat(models.get(2).subscriptionKey()).isEqualTo(expectedSubscriptionKey);

    // All should reference the same subscription (same element instance and message name)
    assertThat(models)
        .allSatisfy(
            model -> {
              assertThat(model.flowNodeInstanceKey()).isEqualTo(subscriptionElementInstanceKey);
              assertThat(model.messageName()).isEqualTo(messageName);
            });

    // But each should have different message keys (different messages)
    assertThat(models.get(0).messageKey()).isEqualTo(111L);
    assertThat(models.get(1).messageKey()).isEqualTo(222L);
    assertThat(models.get(2).messageKey()).isEqualTo(333L);
  }

  @Test
  void shouldExportVariablesForProcessMessageSubscription() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                .withValue(v -> v.setVariables(Map.of("var1", "value1", "var2", 42))));

    // when
    handler.export(record);

    // then
    verify(correlatedMessageWriter).create(correlatedMessageCaptor.capture());
    final CorrelatedMessageDbModel model = correlatedMessageCaptor.getValue();

    assertThat(model.variables()).isNotNull();
    assertThat(model.variables()).contains("\"var1\":\"value1\"");
    assertThat(model.variables()).contains("\"var2\":42");
  }

  @Test
  void shouldExportVariablesForMessageStartEventSubscription() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r -> r.withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
                .withValue(v -> v.setVariables(Map.of("startVar", "initValue", "count", 100))));

    // when
    handler.export(record);

    // then
    verify(correlatedMessageWriter).create(correlatedMessageCaptor.capture());
    final CorrelatedMessageDbModel model = correlatedMessageCaptor.getValue();

    assertThat(model.variables()).isNotNull();
    assertThat(model.variables()).contains("\"startVar\":\"initValue\"");
    assertThat(model.variables()).contains("\"count\":100");
  }

  @Test
  void shouldHandleEmptyVariables() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                .withValue(v -> v.setVariables(Map.of())));

    // when
    handler.export(record);

    // then
    verify(correlatedMessageWriter).create(correlatedMessageCaptor.capture());
    final CorrelatedMessageDbModel model = correlatedMessageCaptor.getValue();

    assertThat(model.variables()).isNull();
  }
}
