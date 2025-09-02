/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.CorrelatedMessageEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageStartEventSubscriptionCorrelatedHandlerTest {

  private static final String INDEX_NAME = "correlated-message-test";
  private final ProtocolFactory factory = new ProtocolFactory();

  @Captor private ArgumentCaptor<CorrelatedMessageEntity> entityCaptor;

  private MessageStartEventSubscriptionCorrelatedHandler handler;

  @BeforeEach
  void setUp() {
    handler = new MessageStartEventSubscriptionCorrelatedHandler(INDEX_NAME);
  }

  @Test
  void shouldHandleMessageStartEventSubscriptionCorrelatedRecord() {
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
                    .withStartEventId("startEvent_1")
                    .withProcessDefinitionKey(999L)
                    .withBpmnProcessId("startProcess"));

    // when
    final boolean handlesRecord = handler.handlesRecord(record);

    // then
    assertThat(handlesRecord).isTrue();
  }

  @Test
  void shouldNotHandleNonCorrelatedIntent() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            builder -> builder.withIntent(MessageStartEventSubscriptionIntent.CREATED),
            builder -> builder.withMessageKey(12345L));

    // when
    final boolean handlesRecord = handler.handlesRecord(record);

    // then
    assertThat(handlesRecord).isFalse();
  }

  @Test
  void shouldNotHandleWrongValueType() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE, // wrong value type
            builder -> builder.withIntent(MessageStartEventSubscriptionIntent.CORRELATED),
            builder -> builder.withMessageKey(12345L));

    // when
    final boolean handlesRecord = handler.handlesRecord(record);

    // then
    assertThat(handlesRecord).isFalse();
  }

  @Test
  void shouldGenerateCompositeId() {
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
                    .withMessageName("startMessage"));

    // when
    final List<String> ids = handler.generateIds(record);

    // then
    assertThat(ids).hasSize(1);
    assertThat(ids.get(0)).isEqualTo("12345-54321"); // messageKey-subscriptionKey
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final CorrelatedMessageEntity entity = handler.createNewEntity("test-id");

    // then
    assertThat(entity).isNotNull();
    assertThat(entity.getId()).isEqualTo("test-id");
  }

  @Test
  void shouldUpdateEntityWithAllAttributes() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            builder ->
                builder
                    .withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
                    .withKey(54321L)
                    .withTimestamp(9876543210000L),
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

    final CorrelatedMessageEntity entity = new CorrelatedMessageEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo("12345-54321");
    assertThat(entity.getMessageKey()).isEqualTo(12345L);
    assertThat(entity.getSubscriptionKey()).isEqualTo(54321L);
    assertThat(entity.getMessageName()).isEqualTo("startMessage");
    assertThat(entity.getCorrelationKey()).isEqualTo("startCorrelation");
    assertThat(entity.getProcessInstanceKey()).isEqualTo(777L);
    assertThat(entity.getFlowNodeInstanceKey()).isNull(); // not available for start events
    assertThat(entity.getFlowNodeId()).isEqualTo("startEvent_1"); // merged from startEventId
    assertThat(entity.getIsInterrupting()).isNull(); // not applicable for start events
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(999L);
    assertThat(entity.getBpmnProcessId()).isEqualTo("startProcess");
    assertThat(entity.getVersion()).isNull(); // would need process cache
    assertThat(entity.getVersionTag()).isNull(); // would need process cache
    assertThat(entity.getVariables()).isEqualTo("{\"startVar\":\"startValue\",\"numVar\":123}");
    assertThat(entity.getTenantId()).isEqualTo("startTenant");
    assertThat(entity.getDateTime()).isNotNull();
  }

  @Test
  void shouldUpdateEntityWithDefaultTenant() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            builder -> builder.withIntent(MessageStartEventSubscriptionIntent.CORRELATED),
            builder ->
                builder
                    .withMessageKey(12345L)
                    .withMessageName("startMessage")
                    .withStartEventId("startEvent_1")
                    .withProcessDefinitionKey(999L)
                    .withBpmnProcessId("startProcess")
                    .withTenantId("") // empty tenant
                    .withVariables(null));

    final CorrelatedMessageEntity entity = new CorrelatedMessageEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getTenantId()).isEqualTo("<default>"); // default tenant
    assertThat(entity.getVariables()).isNull();
  }

  @Test
  void shouldFlushEntityToBatchRequest() {
    // given
    final BatchRequest batchRequest = mock(BatchRequest.class);
    final CorrelatedMessageEntity entity = new CorrelatedMessageEntity().setId("test-id");

    // when
    handler.flush(entity, batchRequest);

    // then
    verify(batchRequest, times(1)).add(eq(INDEX_NAME), eq(entity));
  }

  @Test
  void shouldReturnCorrectIndexName() {
    // when
    final String indexName = handler.getIndexName();

    // then
    assertThat(indexName).isEqualTo(INDEX_NAME);
  }

  @Test
  void shouldReturnCorrectValueType() {
    // when
    final ValueType valueType = handler.getHandledValueType();

    // then
    assertThat(valueType).isEqualTo(ValueType.MESSAGE_START_EVENT_SUBSCRIPTION);
  }

  @Test
  void shouldReturnCorrectEntityType() {
    // when
    final Class<CorrelatedMessageEntity> entityType = handler.getEntityType();

    // then
    assertThat(entityType).isEqualTo(CorrelatedMessageEntity.class);
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
            builder -> builder.withIntent(MessageStartEventSubscriptionIntent.CORRELATED),
            builder ->
                builder
                    .withMessageKey(12345L)
                    .withMessageName("startMessage")
                    .withStartEventId("startEvent_1")
                    .withProcessDefinitionKey(999L)
                    .withBpmnProcessId("startProcess")
                    .withVariables(complexVariables));

    final CorrelatedMessageEntity entity = new CorrelatedMessageEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getVariables()).isNotNull();
    assertThat(entity.getVariables()).contains("simpleString");
    assertThat(entity.getVariables()).contains("number");
    assertThat(entity.getVariables()).contains("boolean");
    assertThat(entity.getVariables()).contains("nested");
  }

  @Test
  void shouldHandleNullAndEmptyVariables() {
    // given - null variables
    final Record<MessageStartEventSubscriptionRecordValue> recordWithNull =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            builder -> builder.withIntent(MessageStartEventSubscriptionIntent.CORRELATED),
            builder ->
                builder
                    .withMessageKey(12345L)
                    .withStartEventId("startEvent_1")
                    .withProcessDefinitionKey(999L)
                    .withBpmnProcessId("startProcess")
                    .withVariables(null));

    // given - empty variables
    final Record<MessageStartEventSubscriptionRecordValue> recordWithEmpty =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            builder -> builder.withIntent(MessageStartEventSubscriptionIntent.CORRELATED),
            builder ->
                builder
                    .withMessageKey(12345L)
                    .withStartEventId("startEvent_1")
                    .withProcessDefinitionKey(999L)
                    .withBpmnProcessId("startProcess")
                    .withVariables(Map.of()));

    final CorrelatedMessageEntity entityNull = new CorrelatedMessageEntity();
    final CorrelatedMessageEntity entityEmpty = new CorrelatedMessageEntity();

    // when
    handler.updateEntity(recordWithNull, entityNull);
    handler.updateEntity(recordWithEmpty, entityEmpty);

    // then
    assertThat(entityNull.getVariables()).isNull();
    assertThat(entityEmpty.getVariables()).isNull();
  }
}