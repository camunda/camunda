/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation.listview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.exporter.handlers.ExportHandler.IdAndIndex;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.index.TargetIndexLocator;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue.BatchOperationItemValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationChunkRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ListViewFromChunkItemHandlerTest {
  private final String indexName = "test-" + ListViewTemplate.INDEX_NAME;
  private final ProtocolFactory factory = new ProtocolFactory();

  private final ListViewFromChunkItemHandler underTest =
      new ListViewFromChunkItemHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.BATCH_OPERATION_CHUNK);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(ProcessInstanceForListViewEntity.class);
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
  void shouldExtractIdAndIndexes() {
    // given
    final int numItems = 3;
    final Record<BatchOperationChunkRecordValue> record = aChunkRecordWithMultipleItems(numItems);
    factory.generateRecordWithIntent(
        ValueType.BATCH_OPERATION_CHUNK, BatchOperationChunkIntent.CREATED);
    final var batchOpKey = record.getValue().getBatchOperationKey();

    final TargetIndexLocator indexLocator = mock(TargetIndexLocator.class);
    final TargetIndex index1 = TargetIndex.mainIndex(indexName);
    final TargetIndex index2 = TargetIndex.ordinalIndex(indexName, 1);
    final TargetIndex index3 = TargetIndex.ordinalIndex(indexName, 2);

    final var items = record.getValue().getItems();
    when(indexLocator.locateOrdinalIndex(eq(indexName), eq(items.get(0)))).thenReturn(index1);
    when(indexLocator.locateOrdinalIndex(eq(indexName), eq(items.get(1)))).thenReturn(index2);
    when(indexLocator.locateOrdinalIndex(eq(indexName), eq(items.get(2)))).thenReturn(index3);

    // when
    final var idsAndIndexes = underTest.extractIdAndIndexes(indexLocator, record);

    // then
    assertThat(idsAndIndexes)
        .isEqualTo(
            List.of(
                new IdAndIndex(items.get(0).getProcessInstanceKey() + ":" + batchOpKey, index1),
                new IdAndIndex(items.get(1).getProcessInstanceKey() + ":" + batchOpKey, index2),
                new IdAndIndex(items.get(2).getProcessInstanceKey() + ":" + batchOpKey, index3)));
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
    final Record<BatchOperationChunkRecordValue> record =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_CHUNK, BatchOperationChunkIntent.CREATED);
    final var entity = underTest.createNewEntity("id");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getBatchOperationIds())
        .containsExactly(String.valueOf(record.getValue().getBatchOperationKey()));
  }

  @Test
  void shouldFlushEntity() {
    // given
    final String batchOperationKey = "batch-op-1";
    final String processInstanceKey = "42";
    // entity uses composite cache ID (processInstanceKey:batchOperationKey)
    final var entity = underTest.createNewEntity(processInstanceKey + ":" + batchOperationKey);
    entity.setBatchOperationIds(List.of(batchOperationKey));

    // when
    final var index = TargetIndex.mainIndex("test-index");
    final var batchRequest = mock(BatchRequest.class);
    underTest.flush(index, entity, batchRequest);

    // then - the ES document ID is just the processInstanceKey, extracted from the composite ID
    Mockito.verify(batchRequest)
        .updateWithScript(
            eq(index),
            eq(processInstanceKey),
            anyString(),
            eq(Map.of("batchOperationId", batchOperationKey)));
  }

  private Record<BatchOperationChunkRecordValue> aChunkRecordWithMultipleItems(final int numItems) {
    final List<BatchOperationItemValue> items = new ArrayList<>();
    for (int i = 0; i < numItems; i++) {
      final BatchOperationItemValue item = factory.generateObject(BatchOperationItemValue.class);
      items.add(item);
    }
    final var value =
        ImmutableBatchOperationChunkRecordValue.builder()
            .from(factory.generateObject(BatchOperationChunkRecordValue.class))
            .withItems(items)
            .build();
    return factory.generateRecord(
        ValueType.BATCH_OPERATION_CHUNK,
        r -> r.withValue(value),
        BatchOperationChunkIntent.CREATED);
  }
}
