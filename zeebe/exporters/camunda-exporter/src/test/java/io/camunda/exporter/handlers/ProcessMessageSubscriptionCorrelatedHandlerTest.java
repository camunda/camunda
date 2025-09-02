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
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
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
class ProcessMessageSubscriptionCorrelatedHandlerTest {

  private static final String INDEX_NAME = "correlated-message-test";
  private final ProtocolFactory factory = new ProtocolFactory();

  @Captor private ArgumentCaptor<CorrelatedMessageEntity> entityCaptor;

  private ProcessMessageSubscriptionCorrelatedHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ProcessMessageSubscriptionCorrelatedHandler(INDEX_NAME);
  }

  @Test
  void shouldHandleProcessMessageSubscriptionCorrelatedRecord() {
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
                    .withElementId("testElement")
                    .withBpmnProcessId("testProcess"));

    // when
    final boolean handlesRecord = handler.handlesRecord(record);

    // then
    assertThat(handlesRecord).isTrue();
  }

  @Test
  void shouldNotHandleNonCorrelatedIntent() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            builder -> builder.withIntent(ProcessMessageSubscriptionIntent.CREATED),
            builder -> builder.withMessageKey(67890L));

    // when
    final boolean handlesRecord = handler.handlesRecord(record);

    // then
    assertThat(handlesRecord).isFalse();
  }

  @Test
  void shouldNotHandleWrongValueType() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE, // wrong value type
            builder -> builder.withIntent(ProcessMessageSubscriptionIntent.CORRELATED),
            builder -> builder.withMessageKey(67890L));

    // when
    final boolean handlesRecord = handler.handlesRecord(record);

    // then
    assertThat(handlesRecord).isFalse();
  }

  @Test
  void shouldGenerateCompositeId() {
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
                    .withMessageName("testMessage"));

    // when
    final List<String> ids = handler.generateIds(record);

    // then
    assertThat(ids).hasSize(1);
    assertThat(ids.get(0)).isEqualTo("67890-12345"); // messageKey-subscriptionKey
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
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            builder ->
                builder
                    .withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                    .withKey(12345L)
                    .withTimestamp(1234567890000L),
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

    final CorrelatedMessageEntity entity = new CorrelatedMessageEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo("67890-12345");
    assertThat(entity.getMessageKey()).isEqualTo(67890L);
    assertThat(entity.getSubscriptionKey()).isEqualTo(12345L);
    assertThat(entity.getMessageName()).isEqualTo("testMessage");
    assertThat(entity.getCorrelationKey()).isEqualTo("correlation123");
    assertThat(entity.getProcessInstanceKey()).isEqualTo(111L);
    assertThat(entity.getFlowNodeInstanceKey()).isEqualTo(222L);
    assertThat(entity.getFlowNodeId()).isEqualTo("testElement");
    assertThat(entity.getIsInterrupting()).isTrue();
    assertThat(entity.getProcessDefinitionKey()).isNull(); // not available in process subscriptions
    assertThat(entity.getBpmnProcessId()).isEqualTo("testProcess");
    assertThat(entity.getVersion()).isNull(); // would need process cache
    assertThat(entity.getVersionTag()).isNull(); // would need process cache
    assertThat(entity.getVariables()).isEqualTo("{\"var1\":\"value1\",\"var2\":42}");
    assertThat(entity.getTenantId()).isEqualTo("testTenant");
    assertThat(entity.getDateTime()).isNotNull();
  }

  @Test
  void shouldUpdateEntityWithDefaultTenant() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            builder -> builder.withIntent(ProcessMessageSubscriptionIntent.CORRELATED),
            builder ->
                builder
                    .withMessageKey(67890L)
                    .withMessageName("testMessage")
                    .withElementId("testElement")
                    .withBpmnProcessId("testProcess")
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
    assertThat(valueType).isEqualTo(ValueType.PROCESS_MESSAGE_SUBSCRIPTION);
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

    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            builder -> builder.withIntent(ProcessMessageSubscriptionIntent.CORRELATED),
            builder ->
                builder
                    .withMessageKey(67890L)
                    .withMessageName("testMessage")
                    .withElementId("testElement")
                    .withBpmnProcessId("testProcess")
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
}