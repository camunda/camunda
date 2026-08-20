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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ProcessInstanceMigrationBatchOperationExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final long batchOperationKey = 42L;

  private final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache =
      mock(ExporterEntityCache.class);
  private final BatchOperationWriter batchOperationWriter = mock(BatchOperationWriter.class);

  private ProcessInstanceMigrationBatchOperationExportHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new ProcessInstanceMigrationBatchOperationExportHandler(
            batchOperationWriter, batchOperationCache);
    Mockito.reset(batchOperationCache);
    when(batchOperationCache.get(Mockito.anyString()))
        .thenReturn(
            Optional.of(
                new CachedBatchOperationEntity(
                    String.valueOf(batchOperationKey), handler.relevantOperationType)));
  }

  private Record<ProcessInstanceMigrationRecordValue> createSuccessRecord() {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE_MIGRATION,
        b ->
            b.withIntent(ProcessInstanceMigrationIntent.MIGRATED)
                .withBatchOperationReference(batchOperationKey));
  }

  private Record<ProcessInstanceMigrationRecordValue> createFailureRecord() {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE_MIGRATION,
        b ->
            b.withRejectionType(RejectionType.PROCESSING_ERROR)
                .withIntent(ProcessInstanceMigrationIntent.MIGRATE)
                .withBatchOperationReference(batchOperationKey));
  }

  @Test
  void shouldHandleSuccessRecord() {
    assertThat(handler.canExport(createSuccessRecord())).isTrue();
  }

  @Test
  void shouldNotHandleSuccessRecordWithoutBatchOperationKeyInMetadata() {
    final var record =
        ImmutableRecord.<ProcessInstanceMigrationRecordValue>builder()
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
        ImmutableRecord.<ProcessInstanceMigrationRecordValue>builder()
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
        ImmutableRecord.<ProcessInstanceMigrationRecordValue>builder()
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
        ImmutableRecord.<ProcessInstanceMigrationRecordValue>builder()
            .from(createFailureRecord())
            .withBatchOperationReference(batchOperationKey)
            .build();
    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldUpdateEntityOnSuccess() {
    handler.export(createSuccessRecord());

    final var itemCaptor = ArgumentCaptor.forClass(BatchOperationItemDbModel.class);
    verify(batchOperationWriter).updateItem(itemCaptor.capture());

    assertThat(itemCaptor.getValue().state()).isEqualTo(BatchOperationItemState.COMPLETED);
    assertThat(itemCaptor.getValue().processedDate()).isNotNull();
    assertThat(itemCaptor.getValue().errorMessage()).isNull();
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
        ImmutableRecord.<ProcessInstanceMigrationRecordValue>builder()
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
    assertThat(handler.getItemKey(record)).isEqualTo(record.getValue().getProcessInstanceKey());
  }

  @Test
  void shouldExtractCorrectProcessInstanceKey() {
    final var record = createSuccessRecord();
    assertThat(handler.getProcessInstanceKey(record))
        .isPresent()
        .get()
        .isEqualTo(record.getValue().getProcessInstanceKey());
  }
}
