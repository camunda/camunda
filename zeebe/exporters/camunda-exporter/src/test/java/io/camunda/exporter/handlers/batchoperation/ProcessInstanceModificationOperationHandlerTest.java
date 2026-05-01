/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import static io.camunda.exporter.utils.ExporterUtil.map;
import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.batchOperationReferenceNullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProcessInstanceModificationOperationHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-index";
  private final long batchOperationKey = 42L;

  private final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache =
      mock(ExporterEntityCache.class);

  private ProcessInstanceModificationOperationHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ProcessInstanceModificationOperationHandler(indexName, batchOperationCache);
    Mockito.reset(batchOperationCache);
    when(batchOperationCache.get(Mockito.anyString()))
        .thenReturn(
            Optional.of(
                new CachedBatchOperationEntity(
                    String.valueOf(batchOperationKey), map(handler.getRelevantOperationType()))));
  }

  private Record<ProcessInstanceModificationRecordValue> createSuccessRecord() {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE_MODIFICATION,
        b ->
            b.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                .withBatchOperationReference(batchOperationKey));
  }

  private Record<ProcessInstanceModificationRecordValue> createFailureRecord() {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE_MODIFICATION,
        b ->
            b.withRejectionType(RejectionType.PROCESSING_ERROR)
                .withIntent(ProcessInstanceModificationIntent.MODIFY)
                .withBatchOperationReference(batchOperationKey));
  }

  @Test
  void shouldHandleSuccessRecord() {
    assertThat(handler.handlesRecord(createSuccessRecord())).isTrue();
  }

  @Test
  void shouldNotHandleSuccessRecordWithoutBatchOperationKeyInMetadata() {
    final var record =
        ImmutableRecord.<ProcessInstanceModificationRecordValue>builder()
            .from(createSuccessRecord())
            .withBatchOperationReference(batchOperationReferenceNullValue())
            .build();
    assertThat(handler.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleSuccessRecordWhenNotRelevantType() {
    Mockito.reset(batchOperationCache);
    final var otherOperationType =
        Arrays.stream(OperationType.values())
            .filter(t -> !t.equals(handler.getRelevantOperationType()))
            .findAny()
            .get();
    when(batchOperationCache.get(Mockito.anyString()))
        .thenReturn(
            Optional.of(
                new CachedBatchOperationEntity(
                    String.valueOf(batchOperationKey), map(otherOperationType))));
    final var record =
        ImmutableRecord.<ProcessInstanceModificationRecordValue>builder()
            .from(createSuccessRecord())
            .withBatchOperationReference(batchOperationKey)
            .build();
    assertThat(handler.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldHandleFailureRecord() {
    assertThat(handler.handlesRecord(createFailureRecord())).isTrue();
  }

  @Test
  void shouldNotHandleFailureRecordWithoutBatchOperationKeyInMetadata() {
    final var record =
        ImmutableRecord.<ProcessInstanceModificationRecordValue>builder()
            .from(createFailureRecord())
            .withBatchOperationReference(batchOperationReferenceNullValue())
            .build();
    assertThat(handler.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleFailureRecordWhenNotRelevantType() {
    Mockito.reset(batchOperationCache);
    final var otherOperationType =
        Arrays.stream(OperationType.values())
            .filter(t -> !t.equals(handler.getRelevantOperationType()))
            .findAny()
            .get();
    when(batchOperationCache.get(Mockito.anyString()))
        .thenReturn(
            Optional.of(
                new CachedBatchOperationEntity(
                    String.valueOf(batchOperationKey), map(otherOperationType))));
    final var record =
        ImmutableRecord.<ProcessInstanceModificationRecordValue>builder()
            .from(createFailureRecord())
            .withBatchOperationReference(batchOperationKey)
            .build();
    assertThat(handler.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleOtherRecord() {
    assertThat(handler.handlesRecord(factory.generateRecord(ValueType.VARIABLE))).isFalse();
  }

  @Test
  void shouldUpdateEntityOnSuccess() {
    final var record = createSuccessRecord();
    final var entity = new OperationEntity();
    handler.updateEntity(record, entity);
    assertThat(entity.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(entity.getCompletedDate()).isNotNull();
    assertThat(entity.getErrorMessage()).isNull();
  }

  @Test
  void shouldUpdateEntityOnFailure() {
    final var record = createFailureRecord();
    final var entity = new OperationEntity();
    handler.updateEntity(record, entity);
    assertThat(entity.getState()).isEqualTo(OperationState.FAILED);
    assertThat(entity.getErrorMessage())
        .isEqualTo(record.getRejectionType() + ": " + record.getRejectionReason());
    assertThat(entity.getCompletedDate()).isNull();
  }

  @Test
  void shouldUpdateEntityOnNotFound() {
    final var record =
        ImmutableRecord.<ProcessInstanceModificationRecordValue>builder()
            .from(createFailureRecord())
            .withRejectionType(RejectionType.NOT_FOUND)
            .build();
    final var entity = new OperationEntity();
    handler.updateEntity(record, entity);
    assertThat(entity.getState()).isEqualTo(OperationState.SKIPPED);
    assertThat(entity.getErrorMessage()).isNull();
    assertThat(entity.getCompletedDate()).isNull();
  }

  @Test
  void shouldFlushEntityFields() {
    final var entity = new OperationEntity();
    entity.setState(OperationState.COMPLETED);
    entity.setType(handler.getRelevantOperationType());
    entity.setBatchOperationId(String.valueOf(batchOperationKey));
    entity.setItemKey(123L);
    entity.setProcessInstanceKey(456L);
    entity.setCompletedDate(OffsetDateTime.now());
    entity.setErrorMessage("error message");
    final var mockRequest = mock(BatchRequest.class);
    handler.flush(null, entity, mockRequest);
    verify(mockRequest, times(1))
        .upsert(
            indexName,
            entity.getId(),
            entity,
            Map.of(
                OperationTemplate.STATE, entity.getState(),
                OperationTemplate.TYPE, entity.getType(),
                OperationTemplate.BATCH_OPERATION_ID, entity.getBatchOperationId(),
                OperationTemplate.ITEM_KEY, entity.getItemKey(),
                OperationTemplate.PROCESS_INSTANCE_KEY, entity.getProcessInstanceKey(),
                OperationTemplate.COMPLETED_DATE, entity.getCompletedDate(),
                OperationTemplate.ERROR_MSG, entity.getErrorMessage()));
  }

  @Test
  void shouldGenerateCorrectId() {
    final var record = createSuccessRecord();
    assertThat(handler.generateIds(record))
        .containsExactly(batchOperationKey + "_" + handler.getItemKey(record));
  }

  @Test
  void shouldUpdateEntitySetRootProcessInstanceKeyOnSuccess() {
    final var record = createSuccessRecord();
    final var entity = new OperationEntity();
    handler.updateEntity(record, entity);
    assertThat(entity.getRootProcessInstanceKey())
        .isPositive()
        .isEqualTo(handler.getRootProcessInstanceKey(record));
  }

  @Test
  void shouldUpdateEntitySetRootProcessInstanceKeyOnFailure() {
    final var record = createFailureRecord();
    final var entity = new OperationEntity();
    handler.updateEntity(record, entity);
    assertThat(entity.getRootProcessInstanceKey())
        .isPositive()
        .isEqualTo(handler.getRootProcessInstanceKey(record));
  }

  @Test
  void shouldExtractCorrectItemKey() {
    final var record = createSuccessRecord();
    assertThat(handler.getItemKey(record)).isEqualTo(record.getValue().getProcessInstanceKey());
  }

  @Test
  void shouldExtractCorrectProcessInstanceKey() {
    final var record = createSuccessRecord();
    assertThat(handler.getProcessInstanceKey(record))
        .isEqualTo(record.getValue().getProcessInstanceKey());
  }

  @Test
  void shouldNotSetRootProcessInstanceKeyWhenDefault() {
    final Record<ProcessInstanceModificationRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            b ->
                b.withRejectionType(RejectionType.PROCESSING_ERROR)
                    .withIntent(ProcessInstanceModificationIntent.MODIFY)
                    .withValue(
                        ImmutableProcessInstanceModificationRecordValue.builder()
                            .from(
                                factory.generateObject(
                                    ProcessInstanceModificationRecordValue.class))
                            .withRootProcessInstanceKey(-1L)
                            .build())
                    .withBatchOperationReference(batchOperationKey));
    final var entity = new OperationEntity();
    handler.updateEntity(record, entity);
    assertThat(entity.getRootProcessInstanceKey()).isNull();
  }
}
