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
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class BatchOperationCreatedHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-" + BatchOperationTemplate.INDEX_NAME;

  private final ExporterEntityCache batchOperationCache = mock(ExporterEntityCache.class);
  private final BatchOperationCreatedHandler underTest =
      new BatchOperationCreatedHandler(indexName, batchOperationCache);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.BATCH_OPERATION_CREATION);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(BatchOperationEntity.class);
  }

  @Test
  void shouldHandleCreatedRecord() {
    // given
    final Record<BatchOperationCreationRecordValue> record =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_CREATION, BatchOperationIntent.CREATED);

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<BatchOperationCreationRecordValue> record =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_CREATION, BatchOperationIntent.CREATED);

    // when
    final var idList = underTest.generateIds(record);

    // then
    assertThat(idList).containsExactly(String.valueOf(record.getValue().getBatchOperationKey()));
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
    final var recordValue = factory.generateObject(BatchOperationCreationRecordValue.class);
    final Record<BatchOperationCreationRecordValue> record =
        factory.generateRecord(
            ValueType.BATCH_OPERATION_CREATION,
            r -> r.withIntent(BatchOperationIntent.CREATED).withValue(recordValue));

    final var entity = new BatchOperationEntity();

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo(String.valueOf(recordValue.getBatchOperationKey()));
    assertThat(entity.getType())
        .isEqualTo(OperationType.valueOf(recordValue.getBatchOperationType().name()));
    assertThat(entity.getState()).isEqualTo(BatchOperationEntity.BatchOperationState.CREATED);

    verify(batchOperationCache).put(eq(entity.getId()), any());
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final var entity = new BatchOperationEntity().setId("123");
    final var mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(entity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, entity);
  }
}
