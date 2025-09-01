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
import static org.mockito.Mockito.never;
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
  void shouldOnlyExportFirstCorrelationForSameMessage() {
    // given
    final long messageKey = 123L;
    final Record<ProcessMessageSubscriptionRecordValue> record1 =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                   .withValue(v -> v.setMessageKey(messageKey)));
    final Record<ProcessMessageSubscriptionRecordValue> record2 =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,  
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                   .withValue(v -> v.setMessageKey(messageKey)));

    // when
    handler.export((Record<Object>) record1);
    handler.export((Record<Object>) record2);

    // then
    // Only the first correlation should be exported, second should be ignored
    verify(correlatedMessageWriter, times(1)).create(any(CorrelatedMessageDbModel.class));
  }

  @Test
  void shouldExportDifferentMessages() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record1 =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                   .withValue(v -> v.setMessageKey(123L)));
    final Record<ProcessMessageSubscriptionRecordValue> record2 =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                   .withValue(v -> v.setMessageKey(456L)));

    // when
    handler.export((Record<Object>) record1);
    handler.export((Record<Object>) record2);

    // then
    verify(correlatedMessageWriter, times(2)).create(any(CorrelatedMessageDbModel.class));
  }
}