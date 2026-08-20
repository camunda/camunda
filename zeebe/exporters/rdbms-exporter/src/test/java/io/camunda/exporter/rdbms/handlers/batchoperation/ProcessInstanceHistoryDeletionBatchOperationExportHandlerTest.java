/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.batchoperation;

import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.batchOperationReferenceNullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.db.rdbms.write.domain.BatchOperationItemDbModel;
import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.protocol.record.value.ImmutableHistoryDeletionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ProcessInstanceHistoryDeletionBatchOperationExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final long batchOperationKey = 42L;

  private final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache =
      mock(ExporterEntityCache.class);
  private final BatchOperationWriter batchOperationWriter = mock(BatchOperationWriter.class);

  private ProcessInstanceHistoryDeletionBatchOperationExportHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new ProcessInstanceHistoryDeletionBatchOperationExportHandler(
            batchOperationWriter, batchOperationCache, BatchOperationType.DELETE_PROCESS_INSTANCE);
    Mockito.reset(batchOperationCache);
    when(batchOperationCache.get(Mockito.anyString()))
        .thenReturn(
            Optional.of(
                new CachedBatchOperationEntity(
                    String.valueOf(batchOperationKey), handler.relevantOperationType)));
  }

  private Record<HistoryDeletionRecordValue> createSuccessRecord() {
    return factory.generateRecord(
        ValueType.HISTORY_DELETION,
        b ->
            b.withIntent(HistoryDeletionIntent.DELETED)
                .withBatchOperationReference(batchOperationKey)
                .withValue(
                    ImmutableHistoryDeletionRecordValue.builder()
                        .from(factory.generateObject(HistoryDeletionRecordValue.class))
                        .withResourceType(HistoryDeletionType.PROCESS_INSTANCE)
                        .build()));
  }

  private Record<HistoryDeletionRecordValue> createFailureRecord() {
    return factory.generateRecord(
        ValueType.HISTORY_DELETION,
        b ->
            b.withRejectionType(RejectionType.PROCESSING_ERROR)
                .withIntent(HistoryDeletionIntent.DELETE)
                .withBatchOperationReference(batchOperationKey)
                .withValue(
                    ImmutableHistoryDeletionRecordValue.builder()
                        .from(factory.generateObject(HistoryDeletionRecordValue.class))
                        .withResourceType(HistoryDeletionType.PROCESS_INSTANCE)
                        .build()));
  }

  @Test
  void shouldNotHandleSuccessRecord() {
    // This handler is not supposed to handle the success record (DELETED intent is not handled)
    assertThat(handler.canExport(createSuccessRecord())).isFalse();
  }

  @Test
  void shouldNotHandleSuccessRecordWithoutBatchOperationKeyInMetadata() {
    final var record =
        ImmutableRecord.<HistoryDeletionRecordValue>builder()
            .from(createSuccessRecord())
            .withBatchOperationReference(batchOperationReferenceNullValue())
            .build();
    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldNotHandleSuccessRecordWhenNotRelevantType() {
    Mockito.reset(batchOperationCache);
    final var otherOperationType =
        Arrays.stream(BatchOperationType.values())
            .filter(t -> !t.equals(handler.relevantOperationType))
            .findAny()
            .get();
    when(batchOperationCache.get(Mockito.anyString()))
        .thenReturn(
            Optional.of(
                new CachedBatchOperationEntity(
                    String.valueOf(batchOperationKey), otherOperationType)));
    final var record =
        ImmutableRecord.<HistoryDeletionRecordValue>builder()
            .from(createSuccessRecord())
            .withBatchOperationReference(batchOperationKey)
            .build();
    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldHandleFailureRecord() {
    assertThat(handler.canExport(createFailureRecord())).isTrue();
  }

  @Test
  void shouldNotHandleFailureRecordWithoutBatchOperationKeyInMetadata() {
    final var record =
        ImmutableRecord.<HistoryDeletionRecordValue>builder()
            .from(createFailureRecord())
            .withBatchOperationReference(batchOperationReferenceNullValue())
            .build();
    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldNotHandleFailureRecordWhenNotRelevantType() {
    Mockito.reset(batchOperationCache);
    final var otherOperationType =
        Arrays.stream(BatchOperationType.values())
            .filter(t -> !t.equals(handler.relevantOperationType))
            .findAny()
            .get();
    when(batchOperationCache.get(Mockito.anyString()))
        .thenReturn(
            Optional.of(
                new CachedBatchOperationEntity(
                    String.valueOf(batchOperationKey), otherOperationType)));
    final var record =
        ImmutableRecord.<HistoryDeletionRecordValue>builder()
            .from(createFailureRecord())
            .withBatchOperationReference(batchOperationKey)
            .build();
    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldUpdateEntityOnFailure() {
    handler.export(createFailureRecord());

    final var itemCaptor = ArgumentCaptor.forClass(BatchOperationItemDbModel.class);
    verify(batchOperationWriter).updateItem(itemCaptor.capture());

    assertThat(itemCaptor.getValue().state()).isEqualTo(BatchOperationItemState.FAILED);
    assertThat(itemCaptor.getValue().processedDate()).isNotNull();
    assertThat(itemCaptor.getValue().errorMessage()).isNotNull();
  }

  @Test
  void shouldUpdateEntityAsSkippedOnNotFound() {
    final var record =
        ImmutableRecord.<HistoryDeletionRecordValue>builder()
            .from(createFailureRecord())
            .withRejectionType(RejectionType.NOT_FOUND)
            .build();

    handler.export(record);

    final var itemCaptor = ArgumentCaptor.forClass(BatchOperationItemDbModel.class);
    verify(batchOperationWriter).updateItem(itemCaptor.capture());

    assertThat(itemCaptor.getValue().state()).isEqualTo(BatchOperationItemState.SKIPPED);
    assertThat(itemCaptor.getValue().processedDate()).isNotNull();
    assertThat(itemCaptor.getValue().errorMessage()).isNull();
  }

  @Test
  void shouldExtractCorrectItemKey() {
    final var record = createSuccessRecord();
    assertThat(handler.getItemKey(record)).isEqualTo(record.getValue().getResourceKey());
  }

  @Test
  void shouldExtractCorrectProcessInstanceKey() {
    final var record = createSuccessRecord();
    assertThat(handler.getProcessInstanceKey(record))
        .isPresent()
        .get()
        .isEqualTo(record.getValue().getResourceKey());
  }
}
