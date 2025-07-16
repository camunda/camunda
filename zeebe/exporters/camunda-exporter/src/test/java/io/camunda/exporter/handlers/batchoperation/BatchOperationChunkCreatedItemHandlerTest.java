/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import static io.camunda.exporter.handlers.batchoperation.BatchOperationChunkCreatedItemHandler.ID_PATTERN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue.BatchOperationItemValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationChunkRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BatchOperationChunkCreatedItemHandlerTest {
  private final String indexName = "test-" + OperationTemplate.INDEX_NAME;

  private final ProtocolFactory factory = new ProtocolFactory();
  private final BatchOperationChunkCreatedItemHandler underTest =
      new BatchOperationChunkCreatedItemHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.BATCH_OPERATION_CHUNK);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(OperationEntity.class);
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
    final int numItems = 3;
    final Record<BatchOperationChunkRecordValue> record = aChunkRecordWithMultipleItems(numItems);

    // when
    final var idList = underTest.generateIds(record);

    // then
    assertThat(idList).hasSize(numItems);
    final long batchOperationKey = record.getValue().getBatchOperationKey();
    record
        .getValue()
        .getItems()
        .forEach(
            item -> {
              final String expectedId =
                  String.format(ID_PATTERN, batchOperationKey, item.getItemKey());
              assertThat(idList).contains(expectedId);
            });
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
    final int numItems = 3;
    final Record<BatchOperationChunkRecordValue> record = aChunkRecordWithMultipleItems(numItems);
    final var item = record.getValue().getItems().getFirst();
    final String expectedId =
        String.format(ID_PATTERN, record.getValue().getBatchOperationKey(), item.getItemKey());

    // when
    final var entity = underTest.createNewEntity(expectedId);
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo(expectedId);
    assertThat(entity.getBatchOperationId())
        .isEqualTo(String.valueOf(record.getValue().getBatchOperationKey()));
    assertThat(entity.getState()).isEqualTo(OperationState.SCHEDULED);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(item.getProcessInstanceKey());
  }

  @Test
  void shouldFlushEntity() {
    // given
    final var mockRequest = mock(BatchRequest.class);
    final Record<BatchOperationChunkRecordValue> record =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_CHUNK, BatchOperationChunkIntent.CREATED);
    final var item = record.getValue().getItems().getFirst();
    final String expectedId =
        String.format(ID_PATTERN, record.getValue().getBatchOperationKey(), item.getItemKey());
    final var entity = underTest.createNewEntity(expectedId);
    underTest.updateEntity(record, entity);

    // when
    underTest.flush(entity, mockRequest); // Assuming null is acceptable for this test

    // then
    final var entityCaptor = ArgumentCaptor.forClass(OperationEntity.class);
    verify(mockRequest).add(eq(indexName), entityCaptor.capture());
    final var capturedEntity = entityCaptor.getValue();
    assertThat(capturedEntity).isEqualTo(entity);
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
