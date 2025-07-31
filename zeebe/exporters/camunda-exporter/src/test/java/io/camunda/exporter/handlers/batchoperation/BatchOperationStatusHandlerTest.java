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
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BatchOperationStatusHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-index";
  private final long batchOperationKey = 42L;

  private final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache =
      mock(ExporterEntityCache.class);

  abstract class AbstractOperationStatusHandlerTest<T extends RecordValue> {

    final AbstractOperationStatusHandler<T> handler;

    AbstractOperationStatusHandlerTest(final AbstractOperationStatusHandler<T> handler) {
      this.handler = handler;

      Mockito.reset(batchOperationCache);
      when(batchOperationCache.get(Mockito.anyString()))
          .thenReturn(
              Optional.of(
                  new CachedBatchOperationEntity(
                      String.valueOf(batchOperationKey), map(handler.getRelevantOperationType()))));
    }

    @Test
    void shouldHandleSuccessRecord() {
      final var record = createSuccessRecord();

      assertThat(handler.handlesRecord(record)).isTrue();
    }

    @Test
    void shouldNotHandleSuccessRecordWithoutBatchOperationKeyInMetadata() {
      final var record =
          ImmutableRecord.<T>builder()
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
          ImmutableRecord.<T>builder()
              .from(createSuccessRecord())
              .withBatchOperationReference(batchOperationKey)
              .build();

      assertThat(handler.handlesRecord(record)).isFalse();
    }

    @Test
    void shouldHandleFailureRecord() {
      final var record = createFailureRecord();

      assertThat(handler.handlesRecord(record)).isTrue();
    }

    @Test
    void shouldNotHandleFailureRecordWithoutBatchOperationKeyInMetadata() {
      final var record =
          ImmutableRecord.<T>builder()
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
          ImmutableRecord.<T>builder()
              .from(createFailureRecord())
              .withBatchOperationReference(batchOperationKey)
              .build();

      assertThat(handler.handlesRecord(record)).isFalse();
    }

    @Test
    void shouldNotHandleOtherRecord() {
      final var record = factory.generateRecord(ValueType.VARIABLE);
      assertThat(handler.handlesRecord((Record<T>) record)).isFalse();
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
          ImmutableRecord.<T>builder()
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
      entity.setBatchOperationId(String.valueOf(batchOperationKey));
      entity.setItemKey(123L);
      entity.setProcessInstanceKey(456L);
      entity.setCompletedDate(OffsetDateTime.now());
      entity.setErrorMessage("error message");

      final var mockRequest = mock(BatchRequest.class);

      // when
      handler.flush(entity, mockRequest);

      // then
      verify(mockRequest, times(1))
          .upsert(
              indexName,
              entity.getId(),
              entity,
              Map.of(
                  OperationTemplate.STATE, entity.getState(),
                  OperationTemplate.BATCH_OPERATION_ID, entity.getBatchOperationId(),
                  OperationTemplate.ITEM_KEY, entity.getItemKey(),
                  OperationTemplate.PROCESS_INSTANCE_KEY, entity.getProcessInstanceKey(),
                  OperationTemplate.COMPLETED_DATE, entity.getCompletedDate(),
                  OperationTemplate.ERROR_MSG, entity.getErrorMessage()));
    }

    @Test
    void shouldGenerateCorrectId() {
      final var record = createSuccessRecord();
      final var generatedIds = handler.generateIds(record);

      assertThat(generatedIds)
          .containsExactly(batchOperationKey + "_" + handler.getItemKey(record));
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
      super(new ProcessInstanceModificationOperationHandler(indexName, batchOperationCache));
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
      super(new ProcessInstanceMigrationOperationHandler(indexName, batchOperationCache));
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
      super(new ProcessInstanceCancellationOperationHandler(indexName, batchOperationCache));
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

      assertThat(handler.handlesRecord(record)).isFalse();
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
                          .withParentProcessInstanceKey(-1L)
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
                          .withParentProcessInstanceKey(-1L)
                          .build())
                  .withIntent(ProcessInstanceIntent.CANCEL)
                  .withBatchOperationReference(batchOperationKey));
    }
  }

  @Nested
  class ResolveIncidentOperationHandlerTest
      extends AbstractOperationStatusHandlerTest<IncidentRecordValue> {

    ResolveIncidentOperationHandlerTest() {
      super(new ResolveIncidentOperationHandler(indexName, batchOperationCache));
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
