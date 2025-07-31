/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation.listview;

import static io.camunda.exporter.utils.ExporterUtil.map;
import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.batchOperationReferenceNullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public abstract class AbstractProcessInstanceFromOperationItemHandlerTest<
    R extends RecordValue & ProcessInstanceRelated> {
  protected static final ExporterEntityCache<String, CachedBatchOperationEntity> CACHE =
      mock(ExporterEntityCache.class);
  protected static final String INDEX_NAME = "test-" + ListViewTemplate.INDEX_NAME;
  protected final ProtocolFactory factory = new ProtocolFactory();
  final AbstractProcessInstanceFromOperationItemHandler<R> underTest;

  AbstractProcessInstanceFromOperationItemHandlerTest(
      final AbstractProcessInstanceFromOperationItemHandler<R> underTest) {
    this.underTest = underTest;
  }

  @Test
  void shouldHandleCompletedRecord() {
    // Given
    final var record = createCompletedRecord();
    when(CACHE.get(Mockito.anyString()))
        .thenReturn(
            Optional.of(
                new CachedBatchOperationEntity(
                    String.valueOf(record.getBatchOperationReference()),
                    map(underTest.getRelevantOperationType()))));

    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @Test
  void shouldHandleRejectedRecord() {
    // Given
    final var record = createRejectedRecord();
    when(CACHE.get(Mockito.anyString()))
        .thenReturn(
            Optional.of(
                new CachedBatchOperationEntity(
                    String.valueOf(record.getBatchOperationReference()),
                    map(underTest.getRelevantOperationType()))));

    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @Test
  void shouldNotHandleRecordsWithNoOperationReference() {
    // Given
    final var record =
        ImmutableRecord.<R>builder()
            .from(createCompletedRecord())
            .withBatchOperationReference(batchOperationReferenceNullValue())
            .build();
    when(CACHE.get(Mockito.anyString()))
        .thenReturn(
            Optional.of(
                new CachedBatchOperationEntity(
                    String.valueOf(record.getBatchOperationReference()),
                    map(underTest.getRelevantOperationType()))));
    // When
    final boolean result = underTest.handlesRecord(record);

    // Then
    assertThat(result).isFalse();
  }

  @Test
  void shouldNotHandleIrrelevantRecord() {
    // Given
    final var record = createCompletedRecord();
    final var irrelevantOperationType =
        Arrays.stream(OperationType.values())
            .filter(t -> t != underTest.getRelevantOperationType())
            .findFirst()
            .get();

    when(CACHE.get(Mockito.anyString()))
        .thenReturn(
            Optional.of(
                new CachedBatchOperationEntity(
                    String.valueOf(record.getBatchOperationReference()),
                    map(irrelevantOperationType))));

    // When
    final boolean result = underTest.handlesRecord(record);

    // Then
    assertThat(result).isFalse();
  }

  @Test
  void shouldGenerateId() {
    // Given
    final var record = createCompletedRecord();

    // When
    final var idList = underTest.generateIds(record);

    // Then
    assertThat(idList).containsExactly(String.valueOf(record.getValue().getProcessInstanceKey()));
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // Given
    final var record = createCompletedRecord();
    final var entity = underTest.createNewEntity("test-id");

    // When
    underTest.updateEntity(record, entity);

    // Then
    assertThat(entity.getBatchOperationIds())
        .containsExactly(String.valueOf(record.getBatchOperationReference()));
  }

  @Test
  void shouldFlushEntity() {
    // Given
    final var entity = underTest.createNewEntity("test-id");
    entity.setBatchOperationIds(List.of("batch-op-1"));
    final var batchRequest = mock(BatchRequest.class);

    // When
    underTest.flush(entity, batchRequest);

    // Then
    Mockito.verify(batchRequest)
        .updateWithScript(
            eq(underTest.getIndexName()),
            eq(entity.getId()),
            anyString(),
            eq(Map.of("batchOperationId", "batch-op-1")));
  }

  protected abstract Record<R> createCompletedRecord();

  protected abstract Record<R> createRejectedRecord();
}
