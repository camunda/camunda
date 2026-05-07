/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.handlers.MessageSubscriptionFromProcessMessageSubscriptionHandler.ID_PATTERN;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.CORRELATION_KEY;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.MESSAGE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate;
import io.camunda.webapps.schema.entities.messagesubscription.EventSourceType;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionMetadataEntity;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionState;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.Mockito;

final class MessageSubscriptionFromProcessMessageSubscriptionHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = MessageSubscriptionTemplate.INDEX_NAME;
  private final ExporterMetadata exporterMetadata =
      new ExporterMetadata(TestObjectMapper.objectMapper());

  @SuppressWarnings("unchecked")
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache =
      Mockito.mock(ExporterEntityCache.class);

  private final MessageSubscriptionFromProcessMessageSubscriptionHandler underTest =
      new MessageSubscriptionFromProcessMessageSubscriptionHandler(
          indexName, exporterMetadata, processCache);

  @BeforeEach
  void resetMetadata() {
    exporterMetadata.setFirstProcessMessageSubscriptionKey(-1);
    Mockito.when(processCache.get(Mockito.anyLong())).thenReturn(Optional.empty());
  }

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_MESSAGE_SUBSCRIPTION);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(MessageSubscriptionEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessMessageSubscriptionIntent.class,
      names = {"CREATED", "MIGRATED", "CORRELATED", "DELETED"},
      mode = Mode.INCLUDE)
  void shouldHandleRecord(final Intent intent) {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record = generateRecord(intent);

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessMessageSubscriptionIntent.class,
      names = {"CREATED", "MIGRATED", "CORRELATED", "DELETED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecord(final Intent intent) {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record = generateRecord(intent);

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void testGenerateIds() {
    // given
    final long processInstanceKey = 123L;
    final long elementInstanceKey = 456L;
    final var recordValue =
        ImmutableProcessMessageSubscriptionRecordValue.builder()
            .withProcessInstanceKey(processInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .build();
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION, r -> r.withValue(recordValue));

    // when
    final var ids = underTest.generateIds(record);

    // then
    assertThat(ids)
        .containsExactly(String.format(ID_PATTERN, processInstanceKey, elementInstanceKey));
  }

  @Test
  void shouldGenerateIdForNewVersionRecord() {
    // given
    final long recordKey = 110L;
    exporterMetadata.setFirstProcessMessageSubscriptionKey(recordKey - 1);
    final var recordValue =
        ImmutableProcessMessageSubscriptionRecordValue.builder()
            .withProcessInstanceKey(123L)
            .withElementInstanceKey(456L)
            .build();
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r ->
                r.withIntent(ProcessMessageSubscriptionIntent.CREATED)
                    .withKey(recordKey)
                    .withValue(recordValue));

    // when
    final var ids = underTest.generateIds(record);

    // then
    assertThat(ids).containsExactly(String.valueOf(recordKey));
    assertThat(exporterMetadata.getFirstProcessMessageSubscriptionKey()).isEqualTo(recordKey - 1);
  }

  @Test
  void shouldGenerateIdForOldVersionRecord() {
    // given
    final long recordKey = 90L;
    final int processInstanceKey = 123;
    final int elementInstanceKey = 456;
    exporterMetadata.setFirstProcessMessageSubscriptionKey(recordKey + 1);
    final var recordValue =
        ImmutableProcessMessageSubscriptionRecordValue.builder()
            .withProcessInstanceKey(processInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .build();
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r ->
                r.withIntent(ProcessMessageSubscriptionIntent.CREATED)
                    .withKey(recordKey)
                    .withValue(recordValue));

    // when
    final var ids = underTest.generateIds(record);

    // then
    assertThat(ids)
        .containsExactly(String.format(ID_PATTERN, processInstanceKey, elementInstanceKey));
    assertThat(exporterMetadata.getFirstProcessMessageSubscriptionKey()).isEqualTo(recordKey + 1);
  }

  @Test
  void shouldSetFirstProcessMessageSubscriptionKeyOnCreatedIntent() {
    // given
    final long recordKey = 100L;
    final var recordValue =
        ImmutableProcessMessageSubscriptionRecordValue.builder()
            .withProcessInstanceKey(123L)
            .withElementInstanceKey(456L)
            .build();
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r ->
                r.withIntent(ProcessMessageSubscriptionIntent.CREATED)
                    .withKey(recordKey)
                    .withValue(recordValue));

    // when
    final var ids = underTest.generateIds(record);

    // then
    assertThat(ids).containsExactly(String.valueOf(recordKey));
    assertThat(exporterMetadata.getFirstProcessMessageSubscriptionKey()).isEqualTo(recordKey);
  }

  @Test
  void testCreateNewEntity() {
    // given
    final String id = "id";

    // when
    final var entity = underTest.createNewEntity(id);

    // then
    assertThat(entity.getId()).isEqualTo(id);
  }

  @Test
  public void testUpdateEntity() {
    // given
    final long recordKey = 789;
    final int partitionId = 10;
    final int position = 9999;
    final int processInstanceKey = 123;
    final int elementInstanceKey = 456;
    final int processDefinitionKey = 555;
    final long rootProcessInstanceKey = 321;
    final String elementId = "elementId";
    final String bpmnProcessId = "bpmnProcessId";
    final String tenantId = "tenantId";
    final String messageName = "messageName";
    final String correlationKey = "correlationKey";
    final Intent intent = ProcessMessageSubscriptionIntent.CREATED;
    final var recordValue =
        ImmutableProcessMessageSubscriptionRecordValue.builder()
            .withProcessInstanceKey(processInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withProcessDefinitionKey(processDefinitionKey)
            .withRootProcessInstanceKey(rootProcessInstanceKey)
            .withElementId(elementId)
            .withBpmnProcessId(bpmnProcessId)
            .withTenantId(tenantId)
            .withMessageName(messageName)
            .withCorrelationKey(correlationKey)
            .build();
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r ->
                r.withIntent(intent)
                    .withKey(recordKey)
                    .withPartitionId(partitionId)
                    .withPosition(position)
                    .withValue(recordValue));

    // when
    final var ids = underTest.generateIds(record);
    final MessageSubscriptionEntity entity = underTest.createNewEntity(ids.getFirst());
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo(String.valueOf(recordKey));
    assertThat(entity.getKey()).isEqualTo(recordKey);
    assertThat(entity.getPartitionId()).isEqualTo(partitionId);
    assertThat(entity.getEventSourceType()).isEqualTo(EventSourceType.PROCESS_MESSAGE_SUBSCRIPTION);
    assertThat(entity.getEventType())
        .isEqualTo(MessageSubscriptionState.fromZeebeIntent(intent.name()));
    assertThat(entity.getMessageSubscriptionType()).isEqualTo("PROCESS_EVENT");
    assertThat(entity.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(entity.getFlowNodeInstanceKey()).isEqualTo(elementInstanceKey);
    assertThat(entity.getFlowNodeId()).isEqualTo(elementId);
    assertThat(entity.getBpmnProcessId()).isEqualTo(bpmnProcessId);
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(entity.getTenantId()).isEqualTo(tenantId);
    assertThat(entity.getPositionProcessMessageSubscription()).isEqualTo(position);
    assertThat(entity.getMetadata().getMessageName()).isEqualTo(messageName);
    assertThat(entity.getMetadata().getCorrelationKey()).isEqualTo(correlationKey);
    assertThat(entity.getRootProcessInstanceKey()).isEqualTo(rootProcessInstanceKey);
  }

  @Test
  void shouldNotSetRootProcessInstanceKeyWhenDefault() {
    // given
    final var recordValue =
        ImmutableProcessMessageSubscriptionRecordValue.builder()
            .withRootProcessInstanceKey(-1L)
            .build();
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CREATED).withValue(recordValue));

    final var entity = new MessageSubscriptionEntity();

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldSetProcessDefinitionNameFromCache() {
    // given
    final long processDefinitionKey = 100L;
    final String processName = "My Process";
    Mockito.when(processCache.get(processDefinitionKey))
        .thenReturn(
            Optional.of(
                new CachedProcessEntity(
                    processName, 3, "v3", List.of(), Map.of(), false, Map.of())));

    final ImmutableProcessMessageSubscriptionRecordValue value =
        ImmutableProcessMessageSubscriptionRecordValue.builder()
            .withProcessDefinitionKey(processDefinitionKey)
            .withBpmnProcessId("proc")
            .withElementId("Event_1")
            .withMessageName("msg")
            .withTenantId("<default>")
            .withProcessInstanceKey(15L)
            .withCorrelationKey("")
            .withMessageKey(-1L)
            .build();

    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CREATED).withValue(value));

    final MessageSubscriptionEntity entity = new MessageSubscriptionEntity();

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getProcessDefinitionName()).isEqualTo(processName);
    assertThat(entity.getProcessDefinitionVersion()).isEqualTo(3);
  }

  @Test
  void shouldExtractToolNameAndInboundConnectorTypeFromCache() {
    // given
    final long processDefinitionKey = 100L;
    final String elementId = "Event_1";
    final Map<String, String> extProps =
        Map.of("io.camunda.tool:name", "myTool", "inbound.type", "io.camunda:http-webhook:1");
    Mockito.when(processCache.get(processDefinitionKey))
        .thenReturn(
            Optional.of(
                new CachedProcessEntity(
                    "Process", 1, null, List.of(), Map.of(), false, Map.of(elementId, extProps))));

    final ImmutableProcessMessageSubscriptionRecordValue value =
        ImmutableProcessMessageSubscriptionRecordValue.builder()
            .withProcessDefinitionKey(processDefinitionKey)
            .withBpmnProcessId("proc")
            .withElementId(elementId)
            .withMessageName("msg")
            .withTenantId("<default>")
            .withProcessInstanceKey(15L)
            .withCorrelationKey("")
            .withMessageKey(-1L)
            .build();

    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CREATED).withValue(value));

    final MessageSubscriptionEntity entity = new MessageSubscriptionEntity();

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getToolName()).isEqualTo("myTool");
    assertThat(entity.getInboundConnectorType()).isEqualTo("io.camunda:http-webhook:1");
    // TODO: Add an assertion for the extension properties once they are included in the entity
  }

  @Test
  void shouldHandleMissingProcessCacheGracefully() {
    // given
    Mockito.when(processCache.get(Mockito.anyLong())).thenReturn(Optional.empty());

    final ImmutableProcessMessageSubscriptionRecordValue value =
        ImmutableProcessMessageSubscriptionRecordValue.builder()
            .withProcessDefinitionKey(999L)
            .withBpmnProcessId("proc")
            .withElementId("Event_1")
            .withMessageName("msg")
            .withTenantId("<default>")
            .withProcessInstanceKey(15L)
            .withCorrelationKey("")
            .withMessageKey(-1L)
            .build();

    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CREATED).withValue(value));

    final MessageSubscriptionEntity entity = new MessageSubscriptionEntity();

    // when - then (no exception)
    underTest.updateEntity(record, entity);
    assertThat(entity.getProcessDefinitionName()).isNull();
    assertThat(entity.getProcessDefinitionVersion()).isNull();
    assertThat(entity.getToolName()).isNull();
    assertThat(entity.getInboundConnectorType()).isNull();
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final String expectedIndexName = MessageSubscriptionTemplate.INDEX_NAME;
    final String id = "555";
    final long position = 456L;
    final long key = 333L;

    final MessageSubscriptionMetadataEntity metadata = new MessageSubscriptionMetadataEntity();
    metadata.setMessageName("messageName");
    metadata.setCorrelationKey("correlationKey");
    final MessageSubscriptionEntity inputEntity =
        new MessageSubscriptionEntity()
            .setId(id)
            .setKey(key)
            .setPositionProcessMessageSubscription(position)
            .setMetadata(metadata);

    final Map<String, Object> metadataMap = new LinkedHashMap<>();
    metadataMap.put(MESSAGE_NAME, metadata.getMessageName());
    metadataMap.put(CORRELATION_KEY, metadata.getCorrelationKey());

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put("key", key);
    expectedUpdateFields.put("positionProcessMessageSubscription", position);
    expectedUpdateFields.put("dateTime", null);
    expectedUpdateFields.put("flowNodeId", null);
    expectedUpdateFields.put("eventSourceType", null);
    expectedUpdateFields.put("eventType", null);
    expectedUpdateFields.put("messageSubscriptionType", null);
    expectedUpdateFields.put("bpmnProcessId", null);
    expectedUpdateFields.put("processDefinitionKey", null);
    expectedUpdateFields.put("processDefinitionName", null);
    expectedUpdateFields.put("processDefinitionVersion", null);
    expectedUpdateFields.put("toolName", null);
    expectedUpdateFields.put("inboundConnectorType", null);
    expectedUpdateFields.put("metadata", metadataMap);

    final BatchRequest mockRequest = Mockito.mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).upsert(expectedIndexName, id, inputEntity, expectedUpdateFields);
  }

  private Record<ProcessMessageSubscriptionRecordValue> generateRecord(final Intent intent) {
    return factory.generateRecord(
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION, r -> r.withIntent(intent));
  }
}
