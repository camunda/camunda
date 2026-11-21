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

import io.camunda.exporter.handlers.ExportHandler.IdAndIndex;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.index.TargetIndexLocator;
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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

class UpdateJobOperationHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-index";
  private final long batchOperationKey = 42L;

  private final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache =
      mock(ExporterEntityCache.class);

  private UpdateJobOperationHandler handler;

  @BeforeEach
  void setUp() {
    handler = new UpdateJobOperationHandler(indexName, batchOperationCache);
    Mockito.reset(batchOperationCache);
    when(batchOperationCache.get(Mockito.anyString()))
        .thenReturn(
            Optional.of(
                new CachedBatchOperationEntity(
                    String.valueOf(batchOperationKey), map(handler.getRelevantOperationType()))));
  }

  private Record<JobRecordValue> createRecord(
      final JobIntent intent, final RejectionType rejectionType) {
    return factory.generateRecord(
        ValueType.JOB,
        b ->
            b.withIntent(intent)
                .withRejectionType(rejectionType)
                .withBatchOperationReference(batchOperationKey));
  }

  private Record<JobRecordValue> createSuccessRecord() {
    return createRecord(JobIntent.UPDATED, RejectionType.NULL_VAL);
  }

  private Record<JobRecordValue> createFailureRecord() {
    return createRecord(JobIntent.UPDATE, RejectionType.PROCESSING_ERROR);
  }

  private void givenCachedBatchOperationOfOtherType() {
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
  }

  @Test
  void shouldHandleJobValueType() {
    assertThat(handler.getHandledValueType()).isEqualTo(ValueType.JOB);
    assertThat(handler.getRelevantOperationType()).isEqualTo(OperationType.UPDATE_JOB);
  }

  @ParameterizedTest
  @EnumSource(
      value = JobIntent.class,
      names = {"UPDATED", "PRIORITY_UPDATED"})
  void shouldHandleSuccessRecord(final JobIntent intent) {
    // given
    final var record = createRecord(intent, RejectionType.NULL_VAL);

    // when - then
    assertThat(handler.handlesRecord(record)).isTrue();
  }

  @Test
  void shouldNotHandleSuccessRecordWithoutBatchOperationKeyInMetadata() {
    // given
    final var record =
        ImmutableRecord.<JobRecordValue>builder()
            .from(createSuccessRecord())
            .withBatchOperationReference(batchOperationReferenceNullValue())
            .build();

    // when - then
    assertThat(handler.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleSuccessRecordWhenNotRelevantType() {
    // given
    givenCachedBatchOperationOfOtherType();

    // when - then
    assertThat(handler.handlesRecord(createSuccessRecord())).isFalse();
  }

  @Test
  void shouldHandleFailureRecord() {
    assertThat(handler.handlesRecord(createFailureRecord())).isTrue();
  }

  @Test
  void shouldNotHandleFailureRecordWithoutBatchOperationKeyInMetadata() {
    // given
    final var record =
        ImmutableRecord.<JobRecordValue>builder()
            .from(createFailureRecord())
            .withBatchOperationReference(batchOperationReferenceNullValue())
            .build();

    // when - then
    assertThat(handler.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleFailureRecordWhenNotRelevantType() {
    // given
    givenCachedBatchOperationOfOtherType();

    // when - then
    assertThat(handler.handlesRecord(createFailureRecord())).isFalse();
  }

  @Test
  void shouldNotHandleUpdateCommandThatWasNotRejected() {
    // given
    final var record = createRecord(JobIntent.UPDATE, RejectionType.NULL_VAL);

    // when - then
    assertThat(handler.handlesRecord(record)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = JobIntent.class,
      names = {"UPDATED", "PRIORITY_UPDATED", "UPDATE"},
      mode = EnumSource.Mode.EXCLUDE)
  void shouldNotHandleOtherJobIntents(final JobIntent intent) {
    // given
    final var record = createRecord(intent, RejectionType.NULL_VAL);

    // when - then
    assertThat(handler.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleOtherRecord() {
    assertThat(handler.handlesRecord(factory.generateRecord(ValueType.VARIABLE))).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = JobIntent.class,
      names = {"UPDATED", "PRIORITY_UPDATED"})
  void shouldUpdateEntityOnSuccess(final JobIntent intent) {
    // given
    final var record = createRecord(intent, RejectionType.NULL_VAL);
    final var entity = new OperationEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(entity.getType()).isEqualTo(OperationType.UPDATE_JOB);
    assertThat(entity.getBatchOperationId()).isEqualTo(String.valueOf(batchOperationKey));
    assertThat(entity.getItemKey()).isEqualTo(record.getKey());
    assertThat(entity.getProcessInstanceKey()).isEqualTo(record.getValue().getProcessInstanceKey());
    assertThat(entity.getCompletedDate()).isNotNull();
    assertThat(entity.getErrorMessage()).isNull();
  }

  @Test
  void shouldUpdateEntityOnFailure() {
    // given
    final var record = createFailureRecord();
    final var entity = new OperationEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getState()).isEqualTo(OperationState.FAILED);
    assertThat(entity.getErrorMessage())
        .isEqualTo(record.getRejectionType() + ": " + record.getRejectionReason());
    assertThat(entity.getCompletedDate()).isNull();
  }

  @Test
  void shouldUpdateEntityOnNotFound() {
    // given
    final var record = createRecord(JobIntent.UPDATE, RejectionType.NOT_FOUND);
    final var entity = new OperationEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getState()).isEqualTo(OperationState.SKIPPED);
    assertThat(entity.getErrorMessage()).isNull();
    assertThat(entity.getCompletedDate()).isNull();
  }

  @Test
  void shouldUpdateEntitySetRootProcessInstanceKey() {
    // given
    final var record = createSuccessRecord();
    final var entity = new OperationEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getRootProcessInstanceKey())
        .isPositive()
        .isEqualTo(record.getValue().getRootProcessInstanceKey());
  }

  @Test
  void shouldNotSetRootProcessInstanceKeyWhenDefault() {
    // given
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            b ->
                b.withIntent(JobIntent.UPDATED)
                    .withValue(
                        ImmutableJobRecordValue.builder()
                            .from(factory.generateObject(JobRecordValue.class))
                            .withRootProcessInstanceKey(-1L)
                            .build())
                    .withBatchOperationReference(batchOperationKey));
    final var entity = new OperationEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldFlushEntityFields() {
    // given
    final var entity = new OperationEntity();
    entity.setState(OperationState.COMPLETED);
    entity.setType(handler.getRelevantOperationType());
    entity.setBatchOperationId(String.valueOf(batchOperationKey));
    entity.setItemKey(123L);
    entity.setProcessInstanceKey(456L);
    entity.setCompletedDate(OffsetDateTime.now());
    entity.setErrorMessage("error message");
    final TargetIndex index = TargetIndex.mainIndex(indexName);
    final var mockRequest = mock(BatchRequest.class);

    // when
    handler.flush(index, entity, mockRequest);

    // then
    verify(mockRequest, times(1))
        .upsert(
            index,
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
  void shouldExtractIdAndOrdinalIndexForSuccessRecord() {
    // given
    final var record = createSuccessRecord();
    final var indexLocator = mock(TargetIndexLocator.class);
    final var index = TargetIndex.ordinalIndex(indexName, 1005);
    when(indexLocator.locateOrdinalIndex(eq(indexName), eq(record.getValue()))).thenReturn(index);

    // when
    final var idAndIndexes = handler.extractIdAndIndexes(indexLocator, record);

    // then
    assertThat(idAndIndexes)
        .containsExactly(new IdAndIndex(batchOperationKey + "_" + record.getKey(), index));
  }

  @Test
  void shouldExtractIdAndOrdinalIndexForFailureRecord() {
    // given
    final var record = createFailureRecord();
    final var indexLocator = mock(TargetIndexLocator.class);
    final var index = TargetIndex.ordinalIndex(indexName, 1005);
    when(indexLocator.locateOrdinalIndex(eq(indexName), eq(record.getValue()))).thenReturn(index);

    // when
    final var idAndIndexes = handler.extractIdAndIndexes(indexLocator, record);

    // then
    assertThat(idAndIndexes)
        .containsExactly(new IdAndIndex(batchOperationKey + "_" + record.getKey(), index));
  }
}
