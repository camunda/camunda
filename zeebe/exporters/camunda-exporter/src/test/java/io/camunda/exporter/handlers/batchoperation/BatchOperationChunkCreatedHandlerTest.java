/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
    final var recordValue = factory.generateObject(BatchOperationChunkRecordValue.class);
    final Record<BatchOperationChunkRecordValue> record =
        factory.generateRecord(
            ValueType.BATCH_OPERATION_CHUNK,
            r -> r.withIntent(BatchOperationChunkIntent.CREATED).withValue(recordValue));

    final var entity = new BatchOperationEntity();

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getEndDate()).isNull();
  }

  @Test
  void shouldUpdateEntityOnFlush() throws PersistenceException {
    // given
    final var entity = new BatchOperationEntity().setId("123").setEndDate(null);
    final var mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(entity, mockRequest);

    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put("endDate", null);

    // then
    verify(mockRequest, times(1)).update(indexName, entity.getId(), updateFields);
  }
}
