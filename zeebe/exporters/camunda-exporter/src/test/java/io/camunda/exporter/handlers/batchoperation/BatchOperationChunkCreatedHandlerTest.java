/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationItem;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BatchOperationChunkCreatedHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-" + OperationTemplate.INDEX_NAME;
  private final BatchOperationChunkCreatedHandler underTest =
      new BatchOperationChunkCreatedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.BATCH_OPERATION_CHUNK);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(BatchOperationEntity.class);
  }

  @Test
  void shouldHandleChunkCreatedRecord() {
    // given
    final Record<BatchOperationChunkRecordValue> record =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_CHUNK, BatchOperationChunkIntent.CREATED);

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<BatchOperationChunkRecordValue> record =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_CHUNK, BatchOperationChunkIntent.CREATED);

    // when
    final var idList = underTest.generateIds(record);

    // then - static composite ID (batchKey:chunk) to collapse all chunks into one cached entity
    assertThat(idList).containsExactly(record.getValue().getBatchOperationKey() + ":chunk");
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final Record<BatchOperationChunkRecordValue> record = createRecord(1L, 11L);

    final var entity = new BatchOperationEntity();

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getOperationsTotalCount()).isEqualTo(1);
    assertThat(entity.getEndDate()).isNull();
    assertThat(entity.getPendingChunkRecordItemCounts()).containsEntry(record.getKey(), 1);
  }

  @Test
  void shouldUpdateEntityFromMultipleRecords() {
    // given
    final Record<BatchOperationChunkRecordValue> record1 = createRecord(1L, 11L);
    final Record<BatchOperationChunkRecordValue> record2 = createRecord(2L, 12L);

    final var entity = new BatchOperationEntity();

    // when
    underTest.updateEntity(record1, entity);
    underTest.updateEntity(record2, entity);

    // then
    assertThat(entity.getOperationsTotalCount()).isEqualTo(2);
    assertThat(entity.getEndDate()).isNull();
    assertThat(entity.getPendingChunkRecordItemCounts())
        .containsEntry(record1.getKey(), 1)
        .containsEntry(record2.getKey(), 1)
        .hasSize(2);
  }

  @Test
  void shouldNotReapplySameRecordKeyIfUpdateEntityCalledTwiceWithSameRecord() {
    // given
    final Record<BatchOperationChunkRecordValue> record = createRecord(1L, 11L);
    final var entity = new BatchOperationEntity();

    // when - simulates the same record being folded into the in-memory entity twice
    underTest.updateEntity(record, entity);
    underTest.updateEntity(record, entity);

    // then - the per-record map still has exactly one entry for that key, and the total isn't
    // doubled either (it is derived from the map, not incremented on each call)
    assertThat(entity.getPendingChunkRecordItemCounts()).containsEntry(record.getKey(), 1);
    assertThat(entity.getPendingChunkRecordItemCounts()).hasSize(1);
    assertThat(entity.getOperationsTotalCount()).isEqualTo(1);
  }

  @Test
  void shouldUpdateWithScriptOnFlush() throws PersistenceException {
    // given
    final var entity = new BatchOperationEntity().setId("123:chunk").setEndDate(null);
    final Record<BatchOperationChunkRecordValue> record = createRecord(1L, 11L);
    underTest.updateEntity(record, entity);
    final var index = TargetIndex.mainIndex("test-index");
    final var mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, entity, mockRequest);

    final Map<String, Object> expectedParams = new HashMap<>();
    expectedParams.put(
        BatchOperationTemplate.PROCESSED_CHUNK_RECORD_KEYS, List.of(record.getKey()));
    expectedParams.put("chunkRecordItemCounts", List.of(1));

    // then - the ES document ID is just the batchKey extracted from the composite ID
    final var scriptCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockRequest, times(1))
        .updateWithScript(eq(index), eq("123"), scriptCaptor.capture(), eq(expectedParams));

    final var script = scriptCaptor.getValue();

    assertThat(script).contains("ctx._source.processedChunkRecordKeys");
    assertThat(script).contains("((Number) processedKey).longValue() == recordKey");
    assertThat(script)
        .contains("ctx._source.operationsTotalCount = ctx._source.operationsTotalCount + delta");
    assertThat(script).contains("ctx._source.endDate = null");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldTrackDistinctRecordKeysAcrossMultipleUpdatesBeforeFlush() throws PersistenceException {
    // given - two distinct records with different item counts folded into one flush cycle
    final Record<BatchOperationChunkRecordValue> record1 = createRecord(1L, 11L);
    final Record<BatchOperationChunkRecordValue> record2 =
        createRecordWithItems(
            List.<BatchOperationChunkRecordValue.BatchOperationItemValue>of(
                new BatchOperationItem(2L, 12L, 12L),
                new BatchOperationItem(3L, 13L, 13L),
                new BatchOperationItem(4L, 14L, 14L)));

    final var entity = new BatchOperationEntity().setId("123:chunk").setEndDate(null);
    underTest.updateEntity(record1, entity);
    underTest.updateEntity(record2, entity);
    final var index = TargetIndex.mainIndex("test-index");
    final var mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, entity, mockRequest);

    // then - the two parallel param lists stay index-aligned per record
    final ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(mockRequest, times(1))
        .updateWithScript(eq(index), eq("123"), any(), paramsCaptor.capture());

    final Map<String, Object> params = paramsCaptor.getValue();
    final var recordKeys =
        (List<Long>) params.get(BatchOperationTemplate.PROCESSED_CHUNK_RECORD_KEYS);
    final var itemCounts = (List<Integer>) params.get("chunkRecordItemCounts");

    assertThat(recordKeys).containsExactly(record1.getKey(), record2.getKey());
    assertThat(itemCounts).containsExactly(1, 3);
  }

  private Record<BatchOperationChunkRecordValue> createRecord(
      final long itemKey, final long processInstanceKey) {
    return createRecordWithItems(
        List.<BatchOperationChunkRecordValue.BatchOperationItemValue>of(
            new BatchOperationItem(itemKey, processInstanceKey, processInstanceKey)));
  }

  private Record<BatchOperationChunkRecordValue> createRecordWithItems(
      final List<BatchOperationChunkRecordValue.BatchOperationItemValue> items) {
    return factory.generateRecord(
        ValueType.BATCH_OPERATION_CHUNK,
        r ->
            r.withIntent(BatchOperationChunkIntent.CREATED)
                .withValue(
                    new BatchOperationChunkRecord().setBatchOperationKey(123L).setItems(items)));
  }
}
