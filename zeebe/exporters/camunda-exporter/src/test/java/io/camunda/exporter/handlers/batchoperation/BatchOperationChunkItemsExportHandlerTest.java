/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.exporter.handlers.ExportHandler.IdAndIndex;
import io.camunda.exporter.handlers.batchoperation.listview.ListViewFromChunkItemHandler;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.index.TargetIndexLocator;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue.BatchOperationItemValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Batch operation chunk items are exported by two handlers: one creates the operation item
 * documents, the other marks the process instances in the list view. Each item must land in the
 * same storage ordinal in both indexes, otherwise an item's operation document and its list-view
 * marker would end up in different ordinal indexes (and be archived/deleted on different
 * schedules).
 */
class BatchOperationChunkItemsExportHandlerTest {

  private static final String OPERATION_INDEX = "test-" + OperationTemplate.INDEX_NAME;
  private static final String LIST_VIEW_INDEX = "test-" + ListViewTemplate.INDEX_NAME;

  private final ProtocolFactory factory = new ProtocolFactory();
  private final TargetIndexLocator indexLocator = mock(TargetIndexLocator.class);

  private final BatchOperationChunkCreatedItemHandler operationItemHandler =
      new BatchOperationChunkCreatedItemHandler(OPERATION_INDEX, mock(ExporterEntityCache.class));
  private final ListViewFromChunkItemHandler listViewHandler =
      new ListViewFromChunkItemHandler(LIST_VIEW_INDEX);

  @Test
  void shouldTargetSameOrdinalForEachItemInOperationAndListViewIndexes() {
    // given - a chunk whose items belong to different ordinals, including the main index
    final long batchOperationKey = 42L;
    final var record = aChunkRecordWithItems(batchOperationKey, 3);
    final var items = record.getValue().getItems();
    final List<Integer> ordinals = List.of(0, 1001, 1002);
    for (int i = 0; i < items.size(); i++) {
      givenItemLocatedAtOrdinal(items.get(i), ordinals.get(i));
    }

    // when
    final var operationResult = operationItemHandler.extractIdAndIndexes(indexLocator, record);
    final var listViewResult = listViewHandler.extractIdAndIndexes(indexLocator, record);

    // then - both handlers resolved the index from the same items, in the same order
    final var operationLookups = ArgumentCaptor.forClass(StorageOrdinalRelated.class);
    final var listViewLookups = ArgumentCaptor.forClass(StorageOrdinalRelated.class);
    verify(indexLocator, times(items.size()))
        .locateOrdinalIndex(eq(OPERATION_INDEX), operationLookups.capture());
    verify(indexLocator, times(items.size()))
        .locateOrdinalIndex(eq(LIST_VIEW_INDEX), listViewLookups.capture());
    assertThat(operationLookups.getAllValues()).containsExactlyElementsOf(items);
    assertThat(listViewLookups.getAllValues()).containsExactlyElementsOf(items);

    // and each item's documents land in the same ordinal in both indexes
    final List<IdAndIndex> expectedOperation = new ArrayList<>();
    final List<IdAndIndex> expectedListView = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      final var item = items.get(i);
      expectedOperation.add(
          new IdAndIndex(
              batchOperationKey + "_" + item.getItemKey(),
              indexFor(OPERATION_INDEX, ordinals.get(i))));
      expectedListView.add(
          new IdAndIndex(
              item.getProcessInstanceKey() + ":" + batchOperationKey,
              indexFor(LIST_VIEW_INDEX, ordinals.get(i))));
    }
    assertThat(operationResult).containsExactlyElementsOf(expectedOperation);
    assertThat(listViewResult).containsExactlyElementsOf(expectedListView);
  }

  private void givenItemLocatedAtOrdinal(final BatchOperationItemValue item, final int ordinal) {
    when(indexLocator.locateOrdinalIndex(eq(OPERATION_INDEX), eq(item)))
        .thenReturn(indexFor(OPERATION_INDEX, ordinal));
    when(indexLocator.locateOrdinalIndex(eq(LIST_VIEW_INDEX), eq(item)))
        .thenReturn(indexFor(LIST_VIEW_INDEX, ordinal));
  }

  private static TargetIndex indexFor(final String indexName, final int ordinal) {
    return ordinal == 0
        ? TargetIndex.mainIndex(indexName)
        : TargetIndex.ordinalIndex(indexName, ordinal);
  }

  private Record<BatchOperationChunkRecordValue> aChunkRecordWithItems(
      final long batchOperationKey, final int numItems) {
    final List<BatchOperationItemValue> items = new ArrayList<>();
    for (int i = 0; i < numItems; i++) {
      items.add(factory.generateObject(BatchOperationItemValue.class));
    }
    final var value =
        ImmutableBatchOperationChunkRecordValue.builder()
            .from(factory.generateObject(BatchOperationChunkRecordValue.class))
            .withBatchOperationKey(batchOperationKey)
            .withItems(items)
            .build();
    return factory.generateRecord(
        ValueType.BATCH_OPERATION_CHUNK,
        r -> r.withValue(value),
        BatchOperationChunkIntent.CREATED);
  }
}
