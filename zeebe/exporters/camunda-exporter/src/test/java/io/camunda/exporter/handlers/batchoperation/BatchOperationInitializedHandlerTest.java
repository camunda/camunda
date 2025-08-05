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
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity.BatchOperationState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationInitializationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BatchOperationInitializedHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-" + OperationTemplate.INDEX_NAME;
  private final BatchOperationInitializedHandler underTest =
      new BatchOperationInitializedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.BATCH_OPERATION_CREATION);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(BatchOperationEntity.class);
  }

  @Test
  void shouldHandleInitializedRecord() {
    // given
    final Record<BatchOperationInitializationRecordValue> record =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_INITIALIZATION, BatchOperationIntent.INITIALIZED);

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<BatchOperationInitializationRecordValue> record =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_INITIALIZATION, BatchOperationIntent.INITIALIZED);

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
    final var recordValue = factory.generateObject(BatchOperationInitializationRecordValue.class);
    final Record<BatchOperationInitializationRecordValue> record =
        factory.generateRecord(
            ValueType.BATCH_OPERATION_INITIALIZATION,
            r -> r.withIntent(BatchOperationIntent.INITIALIZED).withValue(recordValue));

    final var entity = new BatchOperationEntity();

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getStartDate()).isNotNull();
    assertThat(entity.getState()).isEqualTo(BatchOperationState.ACTIVE);
  }

  @Test
  void shouldUpdateEntityOnFlush() throws PersistenceException {
    // given
    final var entity =
        new BatchOperationEntity()
            .setId("123")
            .setState(BatchOperationState.ACTIVE)
            .setStartDate(OffsetDateTime.now());
    final var mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(entity, mockRequest);

    // then
    verify(mockRequest, times(1))
        .update(
            indexName,
            entity.getId(),
            Map.of(
                "state", entity.getState(),
                "startDate", entity.getStartDate()));
  }
}
