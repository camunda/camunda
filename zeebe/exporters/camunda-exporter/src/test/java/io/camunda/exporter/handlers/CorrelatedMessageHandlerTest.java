/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.CorrelatedMessageEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CorrelatedMessageHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private static final String INDEX_NAME = "test-correlated-message";

  @Mock private BatchRequest batchRequest;

  @Captor private ArgumentCaptor<CorrelatedMessageEntity> entityCaptor;

  private CorrelatedMessageHandler handler;

  @BeforeEach
  void setUp() {
    handler = new CorrelatedMessageHandler(INDEX_NAME);
  }

  @Test
  void shouldHandleProcessMessageSubscriptionCorrelatedRecord() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CORRELATED));

    // when
    final boolean handlesRecord = handler.handlesRecord((Record<Object>) record);
    final List<String> ids = handler.generateIds((Record<Object>) record);
    final CorrelatedMessageEntity entity = handler.createNewEntity(ids.get(0));
    handler.updateEntity((Record<Object>) record, entity);
    handler.flush(entity, batchRequest);

    // then
    assertThat(handlesRecord).isTrue();
    assertThat(ids).hasSize(1);
    assertThat(ids.get(0)).isEqualTo(String.valueOf(record.getKey()));
    
    assertThat(entity.getId()).isEqualTo(String.valueOf(record.getKey()));
    assertThat(entity.getKey()).isEqualTo(record.getKey());
    assertThat(entity.getMessageKey()).isEqualTo(record.getValue().getMessageKey());
    assertThat(entity.getMessageName()).isEqualTo(record.getValue().getMessageName());
    assertThat(entity.getCorrelationKey()).isEqualTo(record.getValue().getCorrelationKey());
    assertThat(entity.getProcessInstanceKey()).isEqualTo(record.getValue().getProcessInstanceKey());
    assertThat(entity.getFlowNodeInstanceKey()).isEqualTo(record.getValue().getElementInstanceKey());
    assertThat(entity.getStartEventId()).isNull(); // Not applicable for process message subscriptions
    assertThat(entity.getBpmnProcessId()).isEqualTo(record.getValue().getBpmnProcessId());
    assertThat(entity.getTenantId()).isEqualTo(record.getValue().getTenantId());

    verify(batchRequest).add(INDEX_NAME, entity);
  }

  @Test
  void shouldHandleMessageStartEventSubscriptionCorrelatedRecord() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r -> r.withIntent(MessageStartEventSubscriptionIntent.CORRELATED));

    // when
    final boolean handlesRecord = handler.handlesRecord((Record<Object>) record);
    final List<String> ids = handler.generateIds((Record<Object>) record);
    final CorrelatedMessageEntity entity = handler.createNewEntity(ids.get(0));
    handler.updateEntity((Record<Object>) record, entity);
    handler.flush(entity, batchRequest);

    // then
    assertThat(handlesRecord).isTrue();
    assertThat(ids).hasSize(1);
    assertThat(ids.get(0)).isEqualTo(String.valueOf(record.getKey()));
    
    assertThat(entity.getId()).isEqualTo(String.valueOf(record.getKey()));
    assertThat(entity.getKey()).isEqualTo(record.getKey());
    assertThat(entity.getMessageKey()).isEqualTo(record.getValue().getMessageKey());
    assertThat(entity.getMessageName()).isEqualTo(record.getValue().getMessageName());
    assertThat(entity.getCorrelationKey()).isEqualTo(record.getValue().getCorrelationKey());
    assertThat(entity.getProcessInstanceKey()).isEqualTo(record.getValue().getProcessInstanceKey());
    assertThat(entity.getFlowNodeInstanceKey()).isNull(); // Not applicable for message start events
    assertThat(entity.getStartEventId()).isEqualTo(record.getValue().getStartEventId());
    assertThat(entity.getBpmnProcessId()).isEqualTo(record.getValue().getBpmnProcessId());
    assertThat(entity.getTenantId()).isEqualTo(record.getValue().getTenantId());

    verify(batchRequest).add(INDEX_NAME, entity);
  }

  @Test
  void shouldNotHandleNonCorrelatedRecords() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CREATED));

    // when
    final boolean handlesRecord = handler.handlesRecord((Record<Object>) record);

    // then
    assertThat(handlesRecord).isFalse();
  }

  @Test
  void shouldReturnCorrectEntityType() {
    assertThat(handler.getEntityType()).isEqualTo(CorrelatedMessageEntity.class);
  }

  @Test
  void shouldReturnCorrectIndexName() {
    assertThat(handler.getIndexName()).isEqualTo(INDEX_NAME);
  }
}