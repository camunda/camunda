/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.zeebe.protocol.record.value.HistoryDeletionType.PROCESS_INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.HistoryDeletionEntity;
import io.camunda.zeebe.protocol.record.ImmutableRecord.Builder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableHistoryDeletionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class HistoryDeletionDeletedHandlerTest {

  private static final Long BATCH_OPERATION_KEY = 1L;
  private static final Long RESOURCE_KEY = 123L;
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-history-deletion";
  private final HistoryDeletionDeletedHandler underTest =
      new HistoryDeletionDeletedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.HISTORY_DELETION);
  }

  private Record<HistoryDeletionRecordValue> generateRecord(
      final UnaryOperator<Builder<HistoryDeletionRecordValue>> fnBuild) {
    return factory.generateRecord(
        ValueType.HISTORY_DELETION, fnBuild, HistoryDeletionIntent.DELETED);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final var recordValue =
        ImmutableHistoryDeletionRecordValue.builder()
            .withResourceKey(RESOURCE_KEY)
            .withResourceType(PROCESS_INSTANCE)
            .build();
    final var record = generateRecord(b -> b.withValue(recordValue));

    // when - then
    assertThat(underTest.handlesRecord(record)).isEqualTo(true);
  }

  @Test
  void shouldNotHandleWrongIntent() {
    // when - then
    assertThat(
            underTest.handlesRecord(
                factory.generateRecord(
                    ValueType.HISTORY_DELETION, b -> b, HistoryDeletionIntent.DELETE)))
        .isFalse();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final var recordValue =
        ImmutableHistoryDeletionRecordValue.builder()
            .withResourceKey(RESOURCE_KEY)
            .withResourceType(PROCESS_INSTANCE)
            .build();
    final var record =
        generateRecord(
            b ->
                b.withValue(recordValue)
                    .withKey(RESOURCE_KEY)
                    .withBatchOperationReference(BATCH_OPERATION_KEY));

    // when
    final var idList = underTest.generateIds(record);

    // then
    assertThat(idList).containsOnly(BATCH_OPERATION_KEY + "_" + RESOURCE_KEY);
  }

  @Test
  void shouldUpdateEntity() throws PersistenceException {
    // given
    final var recordValue =
        ImmutableHistoryDeletionRecordValue.builder()
            .withResourceKey(RESOURCE_KEY)
            .withResourceType(PROCESS_INSTANCE)
            .build();
    final var record =
        generateRecord(
            b ->
                b.withValue(recordValue)
                    .withKey(RESOURCE_KEY)
                    .withBatchOperationReference(BATCH_OPERATION_KEY));
    final var historyDeletionEntity =
        new HistoryDeletionEntity().setId(BATCH_OPERATION_KEY + "_" + RESOURCE_KEY);

    // when
    underTest.updateEntity(record, historyDeletionEntity);

    // then
    assertThat(historyDeletionEntity.getResourceKey()).isEqualTo(RESOURCE_KEY);
    assertThat(historyDeletionEntity.getResourceType()).isEqualTo(PROCESS_INSTANCE);
    assertThat(historyDeletionEntity.getBatchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity(String.valueOf(RESOURCE_KEY));

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(String.valueOf(RESOURCE_KEY));
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final var historyDeletionEntity = new HistoryDeletionEntity();
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(historyDeletionEntity, mockRequest);

    // then
    verify(mockRequest).add(indexName, historyDeletionEntity);
    verifyNoMoreInteractions(mockRequest);
  }
}
