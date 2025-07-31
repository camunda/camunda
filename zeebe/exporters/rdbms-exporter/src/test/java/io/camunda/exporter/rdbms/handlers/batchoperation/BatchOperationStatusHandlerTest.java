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
import static org.mockito.Mockito.mock;

import io.camunda.db.rdbms.write.domain.BatchOperationItemDbModel;
import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class BatchOperationStatusHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final long batchOperationKey = 42L;

  private final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache =
      mock(ExporterEntityCache.class);
  private final BatchOperationWriter batchOperationWriter = mock(BatchOperationWriter.class);

  abstract class AbstractOperationStatusHandlerTest<T extends RecordValue> {

    final RdbmsBatchOperationStatusExportHandler<T> handler;

    AbstractOperationStatusHandlerTest(final RdbmsBatchOperationStatusExportHandler<T> handler) {
      this.handler = handler;

      Mockito.reset(batchOperationCache);
      when(batchOperationCache.get(Mockito.anyString()))
          .thenReturn(
              Optional.of(
                  new CachedBatchOperationEntity(
                      String.valueOf(batchOperationKey), handler.relevantOperationType)));
    }

    @Test
    void shouldHandleSuccessRecord() {
      final var record = createSuccessRecord();

      assertThat(handler.canExport(record)).isTrue();
    }

    @Test
    void shouldNotHandleSuccessRecordWithoutBatchOperationKeyInMetadata() {
      final var record =
          ImmutableRecord.<T>builder()
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
          ImmutableRecord.<T>builder()
              .from(createSuccessRecord())
              .withBatchOperationReference(batchOperationKey)
              .build();

      assertThat(handler.canExport(record)).isFalse();
    }

    @Test
    void shouldHandleFailureRecord() {
      final var record = createFailureRecord();

      assertThat(handler.canExport(record)).isTrue();
    }

    @Test
    void shouldNotHandleFailureRecordWithoutBatchOperationKeyInMetadata() {
      final var record =
          ImmutableRecord.<T>builder()
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
          ImmutableRecord.<T>builder()
              .from(createFailureRecord())
              .withBatchOperationReference(batchOperationKey)
              .build();

      assertThat(handler.canExport(record)).isFalse();
    }

    @Test
    void shouldUpdateEntityOnSuccess() {
      final var record = createSuccessRecord();

      handler.export(record);

      final var itemCaptor = ArgumentCaptor.forClass(BatchOperationItemDbModel.class);
      verify(batchOperationWriter).updateItem(itemCaptor.capture());

      assertThat(itemCaptor.getValue().state()).isEqualTo(BatchOperationItemState.COMPLETED);
      assertThat(itemCaptor.getValue().processedDate()).isNotNull();
      assertThat(itemCaptor.getValue().errorMessage()).isNull();
    }

    @Test
    void shouldUpdateEntityOnFailure() {
      final var record = createFailureRecord();

      handler.export(record);

      final var itemCaptor = ArgumentCaptor.forClass(BatchOperationItemDbModel.class);
      verify(batchOperationWriter).updateItem(itemCaptor.capture());

      assertThat(itemCaptor.getValue().state()).isEqualTo(BatchOperationItemState.FAILED);
      assertThat(itemCaptor.getValue().processedDate()).isNotNull();
      assertThat(itemCaptor.getValue().errorMessage()).isNotNull();
    }

    @Test
    void shouldUpdateEntityAsSkippedOnNotFound() {
      final var record =
          ImmutableRecord.<T>builder()
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
    abstract void shouldExtractCorrectItemKey();

    @Test
    abstract void shouldExtractCorrectProcessInstanceKey();

    abstract Record<T> createSuccessRecord();

    abstract Record<T> createFailureRecord();
  }

  @Nested
  class ProcessInstanceModificationOperationHandlerTest
      extends AbstractOperationStatusHandlerTest<ProcessInstanceModificationRecordValue> {

    ProcessInstanceModificationOperationHandlerTest() {
      super(
          new ProcessInstanceModificationBatchOperationExportHandler(
              batchOperationWriter, batchOperationCache));
    }

    @Override
    void shouldExtractCorrectItemKey() {
      final var record = createSuccessRecord();
      final var itemKey = handler.getItemKey(record);

      assertThat(itemKey).isEqualTo(record.getValue().getProcessInstanceKey());
    }

    @Override
    void shouldExtractCorrectProcessInstanceKey() {
      final var record = createSuccessRecord();
      final var processInstanceKey = handler.getProcessInstanceKey(record);

      assertThat(processInstanceKey).isEqualTo(record.getValue().getProcessInstanceKey());
    }

    @Override
    Record<ProcessInstanceModificationRecordValue> createSuccessRecord() {
      return factory.generateRecord(
          ValueType.PROCESS_INSTANCE_MODIFICATION,
          b ->
              b.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                  .withBatchOperationReference(batchOperationKey));
    }

    @Override
    Record<ProcessInstanceModificationRecordValue> createFailureRecord() {
      return factory.generateRecord(
          ValueType.PROCESS_INSTANCE_MODIFICATION,
          b ->
              b.withRejectionType(RejectionType.PROCESSING_ERROR)
                  .withIntent(ProcessInstanceModificationIntent.MODIFY)
                  .withBatchOperationReference(batchOperationKey));
    }
  }

  @Nested
  class ProcessInstanceMigrationOperationHandlerTest
      extends AbstractOperationStatusHandlerTest<ProcessInstanceMigrationRecordValue> {

    ProcessInstanceMigrationOperationHandlerTest() {
      super(
          new ProcessInstanceMigrationBatchOperationExportHandler(
              batchOperationWriter, batchOperationCache));
    }

    @Override
    void shouldExtractCorrectItemKey() {
      final var record = createSuccessRecord();
      final var itemKey = handler.getItemKey(record);

      assertThat(itemKey).isEqualTo(record.getValue().getProcessInstanceKey());
    }

    @Override
    void shouldExtractCorrectProcessInstanceKey() {
      final var record = createSuccessRecord();
      final var processInstanceKey = handler.getProcessInstanceKey(record);

      assertThat(processInstanceKey).isEqualTo(record.getValue().getProcessInstanceKey());
    }

    @Override
    Record<ProcessInstanceMigrationRecordValue> createSuccessRecord() {
      return factory.generateRecord(
          ValueType.PROCESS_INSTANCE_MIGRATION,
          b ->
              b.withIntent(ProcessInstanceMigrationIntent.MIGRATED)
                  .withBatchOperationReference(batchOperationKey));
    }

    @Override
    Record<ProcessInstanceMigrationRecordValue> createFailureRecord() {
      return factory.generateRecord(
          ValueType.PROCESS_INSTANCE_MIGRATION,
          b ->
              b.withRejectionType(RejectionType.PROCESSING_ERROR)
                  .withIntent(ProcessInstanceMigrationIntent.MIGRATE)
                  .withBatchOperationReference(batchOperationKey));
    }
  }

  @Nested
  class ProcessInstanceCancellationOperationHandlerTest
      extends AbstractOperationStatusHandlerTest<ProcessInstanceRecordValue> {

    ProcessInstanceCancellationOperationHandlerTest() {
      super(
          new ProcessInstanceCancellationBatchOperationExportHandler(
              batchOperationWriter, batchOperationCache));
    }

    @Test
    void shouldNotHandleRecordOfSubprocess() {
      final Record<ProcessInstanceRecordValue> record =
          factory.generateRecord(
              ValueType.PROCESS_INSTANCE,
              b ->
                  b.withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                      .withValue(
                          ImmutableProcessInstanceRecordValue.builder()
                              .from(factory.generateObject(ProcessInstanceRecordValue.class))
                              .withBpmnElementType(BpmnElementType.PROCESS)
                              .build())
                      .withBatchOperationReference(batchOperationKey));

      assertThat(handler.canExport(record)).isFalse();
    }

    @Override
    void shouldExtractCorrectItemKey() {
      final var record = createSuccessRecord();
      final var itemKey = handler.getItemKey(record);

      assertThat(itemKey).isEqualTo(record.getValue().getProcessInstanceKey());
    }

    @Override
    void shouldExtractCorrectProcessInstanceKey() {
      final var record = createSuccessRecord();
      final var processInstanceKey = handler.getProcessInstanceKey(record);

      assertThat(processInstanceKey).isEqualTo(record.getValue().getProcessInstanceKey());
    }

    @Override
    Record<ProcessInstanceRecordValue> createSuccessRecord() {
      return factory.generateRecord(
          ValueType.PROCESS_INSTANCE,
          b ->
              b.withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                  .withValue(
                      ImmutableProcessInstanceRecordValue.builder()
                          .from(factory.generateObject(ProcessInstanceRecordValue.class))
                          .withBpmnElementType(BpmnElementType.PROCESS)
                          .withParentProcessInstanceKey(-1)
                          .build())
                  .withBatchOperationReference(batchOperationKey));
    }

    @Override
    Record<ProcessInstanceRecordValue> createFailureRecord() {
      return factory.generateRecord(
          ValueType.PROCESS_INSTANCE_MIGRATION,
          b ->
              b.withRejectionType(RejectionType.PROCESSING_ERROR)
                  .withValue(
                      ImmutableProcessInstanceRecordValue.builder()
                          .from(factory.generateObject(ProcessInstanceRecordValue.class))
                          .withBpmnElementType(BpmnElementType.PROCESS)
                          .withParentProcessInstanceKey(-1)
                          .build())
                  .withIntent(ProcessInstanceIntent.CANCEL)
                  .withBatchOperationReference(batchOperationKey));
    }
  }

  @Nested
  class ResolveIncidentOperationHandlerTest
      extends AbstractOperationStatusHandlerTest<IncidentRecordValue> {

    ResolveIncidentOperationHandlerTest() {
      super(new IncidentBatchOperationExportHandler(batchOperationWriter, batchOperationCache));
    }

    @Override
    void shouldExtractCorrectItemKey() {
      final var record = createSuccessRecord();
      final var itemKey = handler.getItemKey(record);

      assertThat(itemKey).isEqualTo(record.getValue().getProcessInstanceKey());
    }

    @Override
    void shouldExtractCorrectProcessInstanceKey() {
      final var record = createSuccessRecord();
      final var processInstanceKey = handler.getProcessInstanceKey(record);

      assertThat(processInstanceKey).isEqualTo(record.getValue().getProcessInstanceKey());
    }

    @Override
    Record<IncidentRecordValue> createSuccessRecord() {
      return factory.generateRecord(
          ValueType.INCIDENT,
          b ->
              b.withIntent(IncidentIntent.RESOLVED).withBatchOperationReference(batchOperationKey));
    }

    @Override
    Record<IncidentRecordValue> createFailureRecord() {
      return factory.generateRecord(
          ValueType.INCIDENT,
          b ->
              b.withRejectionType(RejectionType.PROCESSING_ERROR)
                  .withIntent(IncidentIntent.RESOLVE)
                  .withBatchOperationReference(batchOperationKey));
    }
  }
}
