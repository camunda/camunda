/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity.BatchOperationState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BatchOperationCompletedHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-index";
  private final BatchOperationCompletedHandler handler =
      new BatchOperationCompletedHandler(indexName);

  @Test
  void shouldHandleCompletedIntent() {
    // given
    final Record<BatchOperationExecutionRecordValue> record =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_EXECUTION, BatchOperationExecutionIntent.COMPLETED);

    // when
    final boolean result = handler.handlesRecord(record);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldNotHandleOtherIntent() {
    // given
    final Record<BatchOperationExecutionRecordValue> record =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_EXECUTION, BatchOperationExecutionIntent.EXECUTE);

    // when
    final boolean result = handler.handlesRecord(record);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<BatchOperationExecutionRecordValue> record =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_EXECUTION, BatchOperationExecutionIntent.COMPLETED);

    // when
    final List<String> idList = handler.generateIds(record);

    // then
    assertThat(idList).containsExactly(String.valueOf(record.getValue().getBatchOperationKey()));
  }

  @Test
  void shouldCreateNewEntity() {
    // given
    final String id = "12345";

    // when
    final BatchOperationEntity entity = handler.createNewEntity(id);

    // then
    assertThat(entity.getId()).isEqualTo(id);
  }

  @Test
  void shouldUpdateEntity() {
    // given
    final Record<BatchOperationExecutionRecordValue> record =
        factory.generateRecord(
            ValueType.BATCH_OPERATION_EXECUTION,
            r -> r.withIntent(BatchOperationExecutionIntent.COMPLETED));

    final var entity = new BatchOperationEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getState()).isEqualTo(BatchOperationState.COMPLETED);
  }

  @Test
  void shouldFlushEntity() throws Exception {
    // given
    final BatchRequest batchRequest = mock(BatchRequest.class);
    final BatchOperationEntity entity =
        new BatchOperationEntity().setId("12345").setState(BatchOperationState.COMPLETED);

    // when
    handler.flush(entity, batchRequest);

    // then
    final var argumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(batchRequest).update(eq(indexName), eq("12345"), argumentCaptor.capture());
    final Map<String, Object> updateFields = argumentCaptor.getValue();
    assertThat(updateFields).containsEntry("state", BatchOperationState.COMPLETED);
  }
}
